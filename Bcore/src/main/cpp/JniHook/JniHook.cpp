//
// Created by Milk on 3/8/21.
// Modified for Android 9-17 Support
//

#include <jni.h>
#include "JniHook.h"
#include "Log.h"
#include "ArtMethod.h"
#include <dlfcn.h>
#include <unistd.h>

static struct {
    int api_level;
    unsigned int art_field_size;
    int art_field_flags_offset;

    unsigned int art_method_size;
    int art_method_flags_offset;
    int art_method_native_offset;

    int class_flags_offset;

    jclass method_utils_class;
    jmethodID get_method_desc_id;
    jmethodID get_method_declaring_class_id;
    jmethodID get_method_name_id;
    
    bool is_initialized;
} HookEnv;

static const char *GetMethodDesc(JNIEnv *env, jobject javaMethod) {
    if (HookEnv.method_utils_class == nullptr || HookEnv.get_method_desc_id == nullptr) {
        return "";
    }
    auto desc = reinterpret_cast<jstring>(env->CallStaticObjectMethod(HookEnv.method_utils_class,
                                                                      HookEnv.get_method_desc_id,
                                                                      javaMethod));
    if (desc == nullptr) return "";
    return env->GetStringUTFChars(desc, JNI_FALSE);
}

static const char *GetMethodDeclaringClass(JNIEnv *env, jobject javaMethod) {
    if (HookEnv.method_utils_class == nullptr || HookEnv.get_method_declaring_class_id == nullptr) {
        return "";
    }
    auto desc = reinterpret_cast<jstring>(env->CallStaticObjectMethod(HookEnv.method_utils_class,
                                                                      HookEnv.get_method_declaring_class_id,
                                                                      javaMethod));
    if (desc == nullptr) return "";
    return env->GetStringUTFChars(desc, JNI_FALSE);
}

static const char *GetMethodName(JNIEnv *env, jobject javaMethod) {
    if (HookEnv.method_utils_class == nullptr || HookEnv.get_method_name_id == nullptr) {
        return "";
    }
    auto desc = reinterpret_cast<jstring>(env->CallStaticObjectMethod(HookEnv.method_utils_class,
                                                                      HookEnv.get_method_name_id,
                                                                      javaMethod));
    if (desc == nullptr) return "";
    return env->GetStringUTFChars(desc, JNI_FALSE);
}

inline static uint32_t GetAccessFlags(const char *art_method) {
    if (HookEnv.art_method_flags_offset < 0) return 0;
    return *reinterpret_cast<const uint32_t *>(art_method + HookEnv.art_method_flags_offset);
}

inline static bool SetAccessFlags(char *art_method, uint32_t flags) {
    if (HookEnv.art_method_flags_offset < 0) return false;
    *reinterpret_cast<uint32_t *>(art_method + HookEnv.art_method_flags_offset) = flags;
    return true;
}

inline static bool AddAccessFlag(char *art_method, uint32_t flag) {
    uint32_t old_flag = GetAccessFlags(art_method);
    uint32_t new_flag = old_flag | flag;
    return new_flag != old_flag && SetAccessFlags(art_method, new_flag);
}

inline static bool ClearAccessFlag(char *art_method, uint32_t flag) {
    uint32_t old_flag = GetAccessFlags(art_method);
    uint32_t new_flag = old_flag & ~flag;
    return new_flag != old_flag && SetAccessFlags(art_method, new_flag);
}

inline static bool HasAccessFlag(char *art_method, uint32_t flag) {
    uint32_t flags = GetAccessFlags(art_method);
    ALOGD("AccessFlag:flags = 0x%x,flag = 0x%x", flags, flag);
    return (flags & flag) == flag;
}

inline static bool ClearFastNativeFlag(char *art_method) {
    return HookEnv.api_level < __ANDROID_API_P__ && ClearAccessFlag(art_method, kAccFastNative);
}

static void *GetArtMethod(JNIEnv *env, jclass clazz, jmethodID methodId) {
    if (HookEnv.api_level >= __ANDROID_API_Q__) {
        jclass executable = env->FindClass("java/lang/reflect/Executable");
        if (executable == nullptr) {
            env->ExceptionClear();
            return methodId;
        }
        jfieldID artId = env->GetFieldID(executable, "artMethod", "J");
        if (artId == nullptr) {
            env->ExceptionClear();
            return methodId;
        }
        jobject method = env->ToReflectedMethod(clazz, methodId, true);
        if (method == nullptr) {
            env->ExceptionClear();
            return methodId;
        }
        return reinterpret_cast<void *>(env->GetLongField(method, artId));
    } else {
        return methodId;
    }
}

static void *GetFieldMethod(JNIEnv *env, jobject field) {
    if (HookEnv.api_level >= __ANDROID_API_Q__) {
        jclass fieldClass = env->FindClass("java/lang/reflect/Field");
        if (fieldClass == nullptr) {
            env->ExceptionClear();
            return env->FromReflectedField(field);
        }
        jmethodID getArtField = env->GetMethodID(fieldClass, "getArtField", "()J");
        if (getArtField == nullptr) {
            env->ExceptionClear();
            return env->FromReflectedField(field);
        }
        return reinterpret_cast<void *>(env->CallLongMethod(field, getArtField));
    } else {
        return env->FromReflectedField(field);
    }
}

bool CheckFlags(void *artMethod) {
    if (artMethod == nullptr) return false;
    char *method = static_cast<char *>(artMethod);
    if (!HasAccessFlag(method, kAccNative)) {
        ALOGE("not native method");
        return false;
    }
    ClearFastNativeFlag(method);
    return true;
}

void JniHook::HookJniFun(JNIEnv *env, jobject java_method, void *new_fun,
                         void **orig_fun, bool is_static) {
    if (!HookEnv.is_initialized) {
        ALOGE("JniHook not initialized!");
        return;
    }
    const char *class_name = GetMethodDeclaringClass(env, java_method);
    const char *method_name = GetMethodName(env, java_method);
    const char *sign = GetMethodDesc(env, java_method);
    HookJniFun(env, class_name, method_name, sign, new_fun, orig_fun, is_static);
}

void JniHook::HookJniFun(JNIEnv *env, const char *class_name, const char *method_name, const char *sign,
                    void *new_fun, void **orig_fun, bool is_static) {
    if (!HookEnv.is_initialized) {
        ALOGE("JniHook not initialized!");
        return;
    }
    if (HookEnv.art_method_native_offset < 0) {
        ALOGD("Native offset not available, using RegisterNatives directly");
        // Fallback: use RegisterNatives
        jclass clazz = env->FindClass(class_name);
        if (!clazz) {
            ALOGD("findClass fail: %s %s", class_name, method_name);
            env->ExceptionClear();
            return;
        }
        JNINativeMethod gMethods[] = {
            {method_name, sign, (void *) new_fun},
        };
        if (env->RegisterNatives(clazz, gMethods, 1) < 0) {
            ALOGE("jni hook error. class：%s, method：%s", class_name, method_name);
        } else {
            ALOGD("register class：%s, method：%s success!", class_name, method_name);
        }
        return;
    }
    
    jclass clazz = env->FindClass(class_name);
    if (!clazz) {
        ALOGD("findClass fail: %s %s", class_name, method_name);
        env->ExceptionClear();
        return;
    }
    jmethodID method = nullptr;
    if (is_static) {
        method = env->GetStaticMethodID(clazz, method_name, sign);
    } else {
        method = env->GetMethodID(clazz, method_name, sign);
    }
    if (!method) {
        env->ExceptionClear();
        ALOGD("get method id fail: %s %s", class_name, method_name);
        return;
    }
    JNINativeMethod gMethods[] = {
        {method_name, sign, (void *) new_fun},
    };

    auto artMethod = reinterpret_cast<uintptr_t *>(GetArtMethod(env, clazz, method));
    if (!CheckFlags(artMethod)) {
        ALOGE("check flags error. class：%s, method：%s", class_name, method_name);
        // Try RegisterNatives anyway
        if (env->RegisterNatives(clazz, gMethods, 1) < 0) {
            ALOGE("jni hook error. class：%s, method：%s", class_name, method_name);
            return;
        }
        ALOGD("register class：%s, method：%s success!", class_name, method_name);
        return;
    }
    *orig_fun = reinterpret_cast<void *>(artMethod[HookEnv.art_method_native_offset]);
    if (env->RegisterNatives(clazz, gMethods, 1) < 0) {
        ALOGE("jni hook error. class：%s, method：%s", class_name, method_name);
        return;
    }
    if (HookEnv.api_level == __ANDROID_API_O__ || HookEnv.api_level == __ANDROID_API_O_MR1__) {
        AddAccessFlag((char *) artMethod, kAccFastNative);
    }
    ALOGD("register class：%s, method：%s success!", class_name, method_name);
}

__attribute__((section (".mytext"))) JNICALL void native_offset
    (JNIEnv *env, jclass obj) {
}

__attribute__((section (".mytext"))) JNICALL void native_offset2
    (JNIEnv *env, jclass obj) {
}

__attribute__((section (".mytext"))) JNICALL void set_method_accessible
    (JNIEnv *env, jclass obj, jclass clazz, jobject method) {
    if (!HookEnv.is_initialized) return;
    jmethodID methodId = env->FromReflectedMethod(method);
    char *art_method = static_cast<char *>(GetArtMethod(env, clazz, methodId));
    if (art_method != nullptr) {
        AddAccessFlag(art_method, kAccPublic);
        if (HookEnv.api_level >= __ANDROID_API_Q__) {
            AddAccessFlag(art_method, kAccPublicApi);
        }
    }
}

__attribute__((section (".mytext"))) JNICALL void set_field_accessible
    (JNIEnv *env, jclass obj, jclass clazz, jobject field) {
    if (!HookEnv.is_initialized) return;
    char *artField = static_cast<char *>(GetFieldMethod(env, field));
    if (artField != nullptr) {
        AddAccessFlag(artField, kAccPublic);
        if (HookEnv.api_level >= __ANDROID_API_Q__) {
            AddAccessFlag(artField, kAccPublicApi);
        }
        ClearAccessFlag(artField, kAccFinal);
    }
}

void registerNative(JNIEnv *env) {
    jclass clazz = env->FindClass("com/jnihook/jni/JniHook");
    if (clazz == nullptr) {
        ALOGE("Failed to find JniHook class");
        return;
    }
    JNINativeMethod gMethods[] = {
        {"nativeOffset",  "()V",                                            (void *) native_offset},
        {"nativeOffset2", "()V",                                            (void *) native_offset2},
        {"setAccessible", "(Ljava/lang/Class;Ljava/lang/reflect/Method;)V", (void *) set_method_accessible},
        {"setAccessible", "(Ljava/lang/Class;Ljava/lang/reflect/Field;)V",  (void *) set_field_accessible},
    };
    if (env->RegisterNatives(clazz, gMethods, sizeof(gMethods) / sizeof(gMethods[0])) < 0) {
        ALOGE("jni register error.");
    }
}

// ============================================================
// ANDROID 14+ COMPATIBILITY - Get ArtMethod using alternative methods
// ============================================================
static void* GetArtMethodAlternative(JNIEnv* env, jclass clazz, jmethodID methodId) {
    // For Android 14+, try using the hidden API
    jclass executableClass = env->FindClass("java/lang/reflect/Executable");
    if (executableClass == nullptr) {
        env->ExceptionClear();
        return nullptr;
    }
    
    jfieldID artMethodField = env->GetFieldID(executableClass, "artMethod", "J");
    if (artMethodField == nullptr) {
        env->ExceptionClear();
        // Try alternative field name
        artMethodField = env->GetFieldID(executableClass, "artMethod", "I");
        if (artMethodField == nullptr) {
            env->ExceptionClear();
            return nullptr;
        }
    }
    
    jobject methodObj = env->ToReflectedMethod(clazz, methodId, true);
    if (methodObj == nullptr) {
        env->ExceptionClear();
        return nullptr;
    }
    
    jlong artMethodPtr = env->GetLongField(methodObj, artMethodField);
    if (artMethodPtr == 0) {
        // Try int field
        jint artMethodInt = env->GetIntField(methodObj, artMethodField);
        return reinterpret_cast<void*>(artMethodInt);
    }
    return reinterpret_cast<void*>(artMethodPtr);
}

void JniHook::InitJniHook(JNIEnv *env, int api_level) {
    if (HookEnv.is_initialized) return;
    
    // Zero out the struct
    memset(&HookEnv, 0, sizeof(HookEnv));
    HookEnv.art_method_flags_offset = -1;
    HookEnv.art_method_native_offset = -1;
    HookEnv.art_field_flags_offset = -1;
    
    registerNative(env);
    HookEnv.api_level = api_level;

    jclass clazz = env->FindClass("com/jnihook/jni/JniHook");
    if (clazz == nullptr) {
        ALOGE("Failed to find JniHook class");
        HookEnv.is_initialized = true;
        return;
    }
    
    jmethodID nativeOffsetId = env->GetStaticMethodID(clazz, "nativeOffset", "()V");
    jmethodID nativeOffset2Id = env->GetStaticMethodID(clazz, "nativeOffset2", "()V");

    jfieldID nativeOffsetFieldId = env->GetStaticFieldID(clazz, "NATIVE_OFFSET", "I");
    jfieldID nativeOffsetField2Id = env->GetStaticFieldID(clazz, "NATIVE_OFFSET_2", "I");
    
    if (nativeOffsetId == nullptr || nativeOffset2Id == nullptr || 
        nativeOffsetFieldId == nullptr || nativeOffsetField2Id == nullptr) {
        ALOGE("Failed to find native methods/fields");
        HookEnv.is_initialized = true;
        return;
    }

    void *nativeOffsetField = GetFieldMethod(env, env->ToReflectedField(clazz, nativeOffsetFieldId, true));
    void *nativeOffsetField2 = GetFieldMethod(env, env->ToReflectedField(clazz, nativeOffsetField2Id, true));
    
    if (nativeOffsetField != nullptr && nativeOffsetField2 != nullptr) {
        HookEnv.art_field_size = (size_t) nativeOffsetField2 - (size_t) nativeOffsetField;
    }

    void *nativeOffset = GetArtMethod(env, clazz, nativeOffsetId);
    void *nativeOffset2 = GetArtMethod(env, clazz, nativeOffset2Id);
    
    if (nativeOffset == nullptr || nativeOffset2 == nullptr) {
        // Try alternative method
        nativeOffset = GetArtMethodAlternative(env, clazz, nativeOffsetId);
        nativeOffset2 = GetArtMethodAlternative(env, clazz, nativeOffset2Id);
    }
    
    if (nativeOffset != nullptr && nativeOffset2 != nullptr) {
        HookEnv.art_method_size = (size_t) nativeOffset2 - (size_t) nativeOffset;
    } else {
        ALOGE("Failed to get ArtMethod sizes");
        // Set default sizes
        HookEnv.art_method_size = 64; // Default size for newer Android
        HookEnv.art_field_size = 32;  // Default size for newer Android
    }

    // ========== FIND NATIVE OFFSET ==========
    HookEnv.art_method_native_offset = -1;
    
    if (nativeOffset != nullptr) {
        auto artMethod = reinterpret_cast<uintptr_t *>(nativeOffset);
        int methodSize = HookEnv.art_method_size / sizeof(uintptr_t);
        
        for (int i = 0; i < methodSize; ++i) {
            if (reinterpret_cast<void *>(artMethod[i]) == native_offset) {
                HookEnv.art_method_native_offset = i;
                ALOGD("Found native offset at: %d", i);
                break;
            }
        }
    }

    // If not found, try searching the entire method structure
    if (HookEnv.art_method_native_offset == -1 && nativeOffset != nullptr) {
        auto artMethod = reinterpret_cast<uintptr_t *>(nativeOffset);
        int methodSize = std::min(64, (int)(HookEnv.art_method_size / sizeof(uintptr_t)));
        
        for (int i = 0; i < methodSize; ++i) {
            void* ptr = reinterpret_cast<void *>(artMethod[i]);
            // Check if this points to code section
            if (ptr != nullptr && ptr > (void*)0x1000 && ptr < (void*)0x7fffffffffffULL) {
                HookEnv.art_method_native_offset = i;
                ALOGD("Found potential native offset at: %d", i);
                break;
            }
        }
    }

    if (HookEnv.art_method_native_offset == -1) {
        ALOGE("art_method_native_offset not found, using RegisterNatives fallback");
    }

    // ========== FIND FLAGS OFFSET ==========
    HookEnv.art_method_flags_offset = -1;
    
    if (nativeOffset != nullptr) {
        uint32_t flags = kAccPublic | kAccStatic | kAccNative | kAccFinal;
        if (api_level >= __ANDROID_API_Q__) {
            flags |= kAccPublicApi;
        }
        if (api_level >= __ANDROID_API_S__) {
            flags |= kAccNterpInvokeFastPathFlag;
        }

        char *start = reinterpret_cast<char *>(nativeOffset);
        int methodSize = HookEnv.art_method_size / sizeof(uint32_t);
        
        // Try common offsets first
        int common_offsets[] = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        for (int offset : common_offsets) {
            if (offset * 4 < HookEnv.art_method_size) {
                uint32_t value = *(uint32_t *)(start + offset * 4);
                if ((value & (kAccPublic | kAccStatic | kAccNative)) == (kAccPublic | kAccStatic | kAccNative)) {
                    HookEnv.art_method_flags_offset = offset * 4;
                    ALOGD("Found flags offset at: %d", HookEnv.art_method_flags_offset);
                    break;
                }
            }
        }
        
        // If not found, search all
        if (HookEnv.art_method_flags_offset == -1) {
            for (int i = 1; i < methodSize; ++i) {
                uint32_t value = *(uint32_t *)(start + i * sizeof(uint32_t));
                if ((value & (kAccPublic | kAccStatic | kAccNative)) == (kAccPublic | kAccStatic | kAccNative)) {
                    HookEnv.art_method_flags_offset = i * sizeof(uint32_t);
                    ALOGD("Found flags offset via search at: %d", HookEnv.art_method_flags_offset);
                    break;
                }
            }
        }
    }

    if (HookEnv.art_method_flags_offset == -1) {
        // Try to find using known offset patterns
        if (api_level >= 36) { // Android 16+
            HookEnv.art_method_flags_offset = 4;
        } else if (api_level >= 34) { // Android 14+
            HookEnv.art_method_flags_offset = 4;
        } else if (api_level >= 33) { // Android 13
            HookEnv.art_method_flags_offset = 4;
        } else {
            HookEnv.art_method_flags_offset = 4;
        }
        ALOGD("Using default flags offset: %d", HookEnv.art_method_flags_offset);
    }

    // ========== FIND FIELD FLAGS OFFSET ==========
    HookEnv.art_field_flags_offset = -1;
    
    if (nativeOffsetField != nullptr) {
        uint32_t flags = kAccPublic | kAccStatic | kAccFinal;
        if (api_level >= __ANDROID_API_Q__) {
            flags |= kAccPublicApi;
        }
        
        char *fieldStart = reinterpret_cast<char *>(nativeOffsetField);
        int fieldSize = HookEnv.art_field_size / sizeof(int32_t);
        
        for (int i = 1; i < fieldSize; ++i) {
            uint32_t value = *(uint32_t *)(fieldStart + i * sizeof(int32_t));
            if (value == flags) {
                HookEnv.art_field_flags_offset = i * sizeof(int32_t);
                ALOGD("Found field flags offset at: %d", HookEnv.art_field_flags_offset);
                break;
            }
        }
    }

    if (HookEnv.art_field_flags_offset == -1) {
        // Use default offset
        HookEnv.art_field_flags_offset = 4;
        ALOGD("Using default field flags offset: %d", HookEnv.art_field_flags_offset);
    }

    // ========== METHOD UTILS ==========
    HookEnv.method_utils_class = env->FindClass("com/jnihook/MethodUtils");
    if (HookEnv.method_utils_class != nullptr) {
        HookEnv.get_method_desc_id = env->GetStaticMethodID(HookEnv.method_utils_class, "getDesc",
                                                            "(Ljava/lang/reflect/Method;)Ljava/lang/String;");
        HookEnv.get_method_declaring_class_id = env->GetStaticMethodID(HookEnv.method_utils_class,
                                                                       "getDeclaringClass",
                                                                       "(Ljava/lang/reflect/Method;)Ljava/lang/String;");
        HookEnv.get_method_name_id = env->GetStaticMethodID(HookEnv.method_utils_class, "getMethodName",
                                                            "(Ljava/lang/reflect/Method;)Ljava/lang/String;");
    } else {
        ALOGE("Failed to find MethodUtils class");
    }
    
    HookEnv.is_initialized = true;
    ALOGD("JniHook initialized for API level: %d", api_level);
}

// ============================================================
// isInitialized implementation
// ============================================================
bool JniHook::isInitialized() {
    return HookEnv.is_initialized;
}
