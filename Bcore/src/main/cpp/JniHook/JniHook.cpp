//
// Created by Milk on 3/8/21.
// Updated for Android 16 (SDK 36) compatibility.
//

#include <jni.h>
#include "JniHook.h"
#include "Log.h"
#include "ArtMethod.h"

static struct {
    int api_level;
    unsigned int art_field_size;
    int art_field_flags_offset;

    unsigned int art_method_size;
    int art_method_flags_offset;   // provided at runtime by BoxCore.cpp
    int art_method_native_offset;

    int class_flags_offset;

    jclass method_utils_class;
    jmethodID get_method_desc_id;
    jmethodID get_method_declaring_class_id;
    jmethodID get_method_name_id;

} HookEnv;

// Set to true only when a valid ART access_flags_ offset is known.
// Guards every access to art_method + flags_offset so we never
// SIGSEGV inside ART on an unsupported layout.
static bool g_art_offset_valid = false;

static const char *GetMethodDesc(JNIEnv *env, jobject javaMethod) {
    auto desc = reinterpret_cast<jstring>(env->CallStaticObjectMethod(HookEnv.method_utils_class,
                                                                      HookEnv.get_method_desc_id,
                                                                      javaMethod));
    return env->GetStringUTFChars(desc, JNI_FALSE);
}

static const char *GetMethodDeclaringClass(JNIEnv *env, jobject javaMethod) {
    auto desc = reinterpret_cast<jstring>(env->CallStaticObjectMethod(HookEnv.method_utils_class,
                                                                      HookEnv.get_method_declaring_class_id,
                                                                      javaMethod));
    return env->GetStringUTFChars(desc, JNI_FALSE);
}

static const char *GetMethodName(JNIEnv *env, jobject javaMethod) {
    auto desc = reinterpret_cast<jstring>(env->CallStaticObjectMethod(HookEnv.method_utils_class,
                                                                      HookEnv.get_method_name_id,
                                                                      javaMethod));
    return env->GetStringUTFChars(desc, JNI_FALSE);
}

inline static uint32_t GetAccessFlags(const char *art_method) {
    return *reinterpret_cast<const uint32_t *>(art_method + HookEnv.art_method_flags_offset);
}

inline static bool SetAccessFlags(char *art_method, uint32_t flags) {
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
    // FastNative
    return HookEnv.api_level < __ANDROID_API_P__ && ClearAccessFlag(art_method, kAccFastNative);
}

static void *GetArtMethod(JNIEnv *env, jclass clazz, jmethodID methodId) {
    if (HookEnv.api_level >= __ANDROID_API_Q__) {
        jclass executable = env->FindClass("java/lang/reflect/Executable");
        jfieldID artId = env->GetFieldID(executable, "artMethod", "J");
        jobject method = env->ToReflectedMethod(clazz, methodId, true);
        return reinterpret_cast<void *>(env->GetLongField(method, artId));
    } else {
        return methodId;
    }
}

static void *GetFieldMethod(JNIEnv *env, jobject field) {
    if (HookEnv.api_level >= __ANDROID_API_Q__) {
        jclass fieldClass = env->FindClass("java/lang/reflect/Field");
        jmethodID getArtField = env->GetMethodID(fieldClass, "getArtField", "()J");
        return reinterpret_cast<void *>(env->CallLongMethod(field, getArtField));
    } else {
        return env->FromReflectedField(field);
    }
}

bool CheckFlags(void *artMethod) {
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
    const char *class_name = GetMethodDeclaringClass(env, java_method);
    const char *method_name = GetMethodName(env, java_method);
    const char *sign = GetMethodDesc(env, java_method);
    HookJniFun(env, class_name, method_name, sign, new_fun, orig_fun, is_static);
}

void
JniHook::HookJniFun(JNIEnv *env, const char *class_name, const char *method_name, const char *sign,
                    void *new_fun, void **orig_fun, bool is_static) {
    // Refuse to hook if the ART offset probe failed on this device.
    if (!g_art_offset_valid || HookEnv.art_method_flags_offset == 0) {
        ALOGE("HookJniFun skipped: invalid ART offset (class=%s, method=%s)",
              class_name, method_name);
        return;
    }
    if (HookEnv.art_method_native_offset == 0) {
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
        return;
    }
    *orig_fun = reinterpret_cast<void *>(artMethod[HookEnv.art_method_native_offset]);
    if (env->RegisterNatives(clazz, gMethods, 1) < 0) {
        ALOGE("jni hook error. class：%s, method：%s", class_name, method_name);
        return;
    }
    // FastNative
    if (HookEnv.api_level == __ANDROID_API_O__ || HookEnv.api_level == __ANDROID_API_O_MR1__) {
        AddAccessFlag((char *) artMethod, kAccFastNative);
    }
    ALOGD("register class：%s, method：%s success!", class_name, method_name);
}

__attribute__((section (".mytext")))  JNICALL void native_offset
        (JNIEnv *env, jclass obj) {
}

__attribute__((section (".mytext")))  JNICALL void native_offset2
        (JNIEnv *env, jclass obj) {
}

__attribute__((section (".mytext")))  JNICALL void set_method_accessible
        (JNIEnv *env, jclass obj, jclass clazz, jobject method) {
    if (!g_art_offset_valid || HookEnv.art_method_flags_offset == 0) {
        return;
    }
    jmethodID methodId = env->FromReflectedMethod(method);
    char *art_method = static_cast<char *>(GetArtMethod(env, clazz, methodId));
    AddAccessFlag(art_method, kAccPublic);
    if (HookEnv.api_level >= __ANDROID_API_Q__) {
        AddAccessFlag(art_method, kAccPublicApi);
    }
}

__attribute__((section (".mytext")))  JNICALL void set_field_accessible
        (JNIEnv *env, jclass obj, jclass clazz, jobject field) {
    if (HookEnv.art_field_flags_offset == 0) {
        return;
    }
    char *artField = static_cast<char *>(GetFieldMethod(env, field));
    AddAccessFlag(artField, kAccPublic);
    if (HookEnv.api_level >= __ANDROID_API_Q__) {
        AddAccessFlag(artField, kAccPublicApi);
    }
    ClearAccessFlag(artField, kAccFinal);
}

void registerNative(JNIEnv *env) {
    jclass clazz = env->FindClass("com/jnihook/jni/JniHook");
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
// Legacy 2-arg version. Kept so existing callers still compile.
// Skips ART hooks because the flags offset is not known here.
// ============================================================
void JniHook::InitJniHook(JNIEnv *env, int api_level) {
    JniHook::InitJniHook(env, api_level, 0);
}

// ============================================================
// New 3-arg version. Receives the runtime-discovered access_flags_
// offset from BoxCore.cpp. If offset == 0 we skip ART hooks rather
// than crash inside ART on Android 16.
// ============================================================
void JniHook::InitJniHook(JNIEnv *env, int api_level, uint32_t art_method_flags_offset) {
    registerNative(env);
    HookEnv.api_level = api_level;

    // -------- ART access_flags_ offset (given to us) --------
    if (art_method_flags_offset == 0) {
        ALOGE("InitJniHook: no valid ART access_flags_ offset supplied; "
              "ART hooks will NOT be installed.");
        g_art_offset_valid = false;
        HookEnv.art_method_flags_offset = 0;
    } else {
        HookEnv.art_method_flags_offset = static_cast<int>(art_method_flags_offset);
        g_art_offset_valid = true;
        ALOGD("InitJniHook: using ART access_flags_ offset 0x%x (api=%d)",
              HookEnv.art_method_flags_offset, api_level);
    }

    jclass clazz = env->FindClass("com/jnihook/jni/JniHook");
    if (clazz == nullptr) {
        ALOGE("InitJniHook: failed to find com/jnihook/jni/JniHook");
        return;
    }

    jmethodID nativeOffsetId = env->GetStaticMethodID(clazz, "nativeOffset", "()V");
    jmethodID nativeOffset2Id = env->GetStaticMethodID(clazz, "nativeOffset2", "()V");
    if (nativeOffsetId == nullptr || nativeOffset2Id == nullptr) {
        ALOGE("InitJniHook: failed to resolve native offset methods");
        return;
    }

    jfieldID nativeOffsetFieldId = env->GetStaticFieldID(clazz, "NATIVE_OFFSET", "I");
    jfieldID nativeOffsetField2Id = env->GetStaticFieldID(clazz, "NATIVE_OFFSET_2", "I");
    if (nativeOffsetFieldId == nullptr || nativeOffsetField2Id == nullptr) {
        ALOGE("InitJniHook: failed to resolve NATIVE_OFFSET fields");
        return;
    }

    void *nativeOffsetField = GetFieldMethod(env, env->ToReflectedField(clazz, nativeOffsetFieldId,
                                                                        true));
    void *nativeOffsetField2 = GetFieldMethod(env, env->ToReflectedField(clazz, nativeOffsetField2Id,
                                                                         true));
    HookEnv.art_field_size = (size_t) nativeOffsetField2 - (size_t) nativeOffsetField;

    void *nativeOffset = GetArtMethod(env, clazz, nativeOffsetId);
    void *nativeOffset2 = GetArtMethod(env, clazz, nativeOffset2Id);
    HookEnv.art_method_size = (size_t) nativeOffset2 - (size_t) nativeOffset;

    // -------- Discover art_method_native_offset --------
    int i = 0;
    auto artMethod = reinterpret_cast<uintptr_t *>(nativeOffset);
    for (i = 0; i < HookEnv.art_method_size; ++i) {
        if (reinterpret_cast<void *>(artMethod[i]) == native_offset) {
            HookEnv.art_method_native_offset = i;
            break;
        }
    }
    if (i == HookEnv.art_method_size) {
        ALOGE("init jni hook error. art_method_native_offset not found!");
        g_art_offset_valid = false;
        return;
    }

    // -------- Discover art_field_flags_offset --------
    uint32_t fieldFlags = 0x0;
    fieldFlags |= kAccPublic;
    fieldFlags |= kAccStatic;
    fieldFlags |= kAccFinal;
    if (api_level >= __ANDROID_API_Q__) {
        fieldFlags |= kAccPublicApi;
    }

    char *fieldStart = reinterpret_cast<char *>(nativeOffsetField);
    for (i = 1; i < HookEnv.art_field_size; ++i) {
        auto value = *(int32_t *) (fieldStart + i * sizeof(int32_t));
        if (value == fieldFlags) {
            HookEnv.art_field_flags_offset = i * sizeof(int32_t);
            break;
        }
    }
    if (i == HookEnv.art_field_size) {
        ALOGE("init jni hook error. art_field_flags_offset not found!");
        HookEnv.art_field_flags_offset = 0;
    } else {
        ALOGD("art_field_flags_offset = 0x%x", HookEnv.art_field_flags_offset);
    }

    // -------- MethodUtils --------
    HookEnv.method_utils_class = env->FindClass("com/jnihook/MethodUtils");
    if (HookEnv.method_utils_class == nullptr) {
        ALOGE("InitJniHook: failed to find com/jnihook/MethodUtils");
        g_art_offset_valid = false;
        return;
    }
    HookEnv.get_method_desc_id = env->GetStaticMethodID(HookEnv.method_utils_class, "getDesc",
                                                        "(Ljava/lang/reflect/Method;)Ljava/lang/String;");
    HookEnv.get_method_declaring_class_id = env->GetStaticMethodID(HookEnv.method_utils_class,
                                                                   "getDeclaringClass",
                                                                   "(Ljava/lang/reflect/Method;)Ljava/lang/String;");
    HookEnv.get_method_name_id = env->GetStaticMethodID(HookEnv.method_utils_class, "getMethodName",
                                                        "(Ljava/lang/reflect/Method;)Ljava/lang/String;");
    if (HookEnv.get_method_desc_id == nullptr ||
        HookEnv.get_method_declaring_class_id == nullptr ||
        HookEnv.get_method_name_id == nullptr) {
        ALOGE("InitJniHook: failed to resolve MethodUtils methods");
        g_art_offset_valid = false;
        return;
    }

    ALOGD("JniHook init complete (api=%d, method_flags=0x%x, native_offset=%d, field_flags=0x%x)",
          api_level,
          HookEnv.art_method_flags_offset,
          HookEnv.art_method_native_offset,
          HookEnv.art_field_flags_offset);
}
