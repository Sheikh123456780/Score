#include <jni.h>
#include <sys/system_properties.h>
#include <cstring>
#include <cstdlib>
#include <dlfcn.h>
#include <unistd.h>
#include "hidden_api.h"
#include "Log.h"

// ============================================================
// LOG MACROS
// ============================================================
#ifndef LOGI
#define LOGI(...) ALOGD(__VA_ARGS__)
#endif

#ifndef LOGE
#define LOGE(...) ALOGE(__VA_ARGS__)
#endif

int get_android_api_level() {
    char sdk_ver_str[PROP_VALUE_MAX] = {0};
    if (__system_property_get("ro.build.version.sdk", sdk_ver_str)) {
        return std::strtol(sdk_ver_str, nullptr, 10);
    }
    return 0;
}

// ============================================================
// ANDROID 9-13 (API 28-33): Native Symbol Hooking
// ============================================================
bool disable_hidden_api_native(JNIEnv *env) {
    int api_level = get_android_api_level();
    
    if (api_level < 28 || api_level > 33) {
        return false;
    }
    
    // Try multiple symbol names for different ART versions
    const char* symbol_names[] = {
        "_ZN3artL32VMRuntime_setHiddenApiExemptionsEP7_JNIEnvP7_jclassP13_jobjectArray",
        "_ZN3art12VMRuntime22setHiddenApiExemptionsEP7_JNIEnvP13_jobjectArray",
        "_ZN3artL17SetHiddenApiStateEP7_JNIEnvP13_jobjectArray",
        "_ZN3art12VMRuntime27setHiddenApiExemptionsNativeEP7_JNIEnvP7_jclassP13_jobjectArray",
        nullptr
    };

    void* libart = dlopen("libart.so", RTLD_NOW);
    if (libart == nullptr) {
        LOGE("Failed to open libart.so");
        return false;
    }
    
    for (int i = 0; symbol_names[i] != nullptr; i++) {
        void *addr = dlsym(libart, symbol_names[i]);
        if (addr == nullptr) {
            continue;
        }
        
        jclass stringClass = env->FindClass("java/lang/String");
        if (stringClass == nullptr) {
            env->ExceptionClear();
            continue;
        }
        
        jstring str = env->NewStringUTF("L");
        if (str == nullptr) {
            env->ExceptionClear();
            continue;
        }
        
        jobjectArray args = env->NewObjectArray(1, stringClass, str);
        if (args == nullptr) {
            env->ExceptionClear();
            env->DeleteLocalRef(str);
            continue;
        }
        
        auto func = reinterpret_cast<void (*)(JNIEnv *, jclass, jobjectArray)>(addr);
        func(env, stringClass, args);
        
        env->DeleteLocalRef(str);
        env->DeleteLocalRef(args);
        
        if (!env->ExceptionCheck()) {
            LOGI("Hidden API disabled via native hook: %s", symbol_names[i]);
            dlclose(libart);
            return true;
        }
        env->ExceptionClear();
    }
    dlclose(libart);
    return false;
}

// ============================================================
// ANDROID 14+ (API 34+): JNI Reflection Bypass
// ============================================================
bool disable_hidden_api_reflection(JNIEnv *env) {
    int api_level = get_android_api_level();
    
    if (api_level < 34) return false;
    
    jclass vm_runtime_cls = env->FindClass("dalvik/system/VMRuntime");
    if (vm_runtime_cls == nullptr) {
        env->ExceptionClear();
        LOGI("VMRuntime class not found");
        return false;
    }

    jmethodID get_runtime = env->GetStaticMethodID(vm_runtime_cls, "getRuntime", "()Ldalvik/system/VMRuntime;");
    if (get_runtime == nullptr) {
        env->ExceptionClear();
        LOGI("getRuntime method not found");
        return false;
    }

    jobject runtime_inst = env->CallStaticObjectMethod(vm_runtime_cls, get_runtime);
    if (runtime_inst == nullptr || env->ExceptionCheck()) {
        env->ExceptionClear();
        LOGI("Failed to get VMRuntime instance");
        return false;
    }

    // Try multiple method signatures for setHiddenApiExemptions
    const char* method_names[] = {
        "setHiddenApiExemptions",
        "setHiddenApiExemptionsNative",
        nullptr
    };

    for (int i = 0; method_names[i] != nullptr; i++) {
        jmethodID set_exemptions = env->GetMethodID(vm_runtime_cls, method_names[i], "([Ljava/lang/String;)V");
        if (set_exemptions == nullptr) {
            env->ExceptionClear();
            continue;
        }
        
        jclass string_cls = env->FindClass("java/lang/String");
        if (string_cls == nullptr) {
            env->ExceptionClear();
            continue;
        }
        
        jstring str = env->NewStringUTF("L");
        if (str == nullptr) {
            env->ExceptionClear();
            continue;
        }
        
        jobjectArray exemptions = env->NewObjectArray(1, string_cls, str);
        if (exemptions == nullptr) {
            env->ExceptionClear();
            env->DeleteLocalRef(str);
            continue;
        }

        env->CallVoidMethod(runtime_inst, set_exemptions, exemptions);
        
        env->DeleteLocalRef(str);
        env->DeleteLocalRef(exemptions);
        
        if (!env->ExceptionCheck()) {
            LOGI("Hidden API disabled via reflection using %s on API %d", method_names[i], api_level);
            env->DeleteLocalRef(runtime_inst);
            return true;
        }
        env->ExceptionClear();
    }

    // Android 16+ (API 36+): Try setHiddenApiPolicy
    if (api_level >= 36) {
        jmethodID set_policy = env->GetMethodID(vm_runtime_cls, "setHiddenApiPolicy", "(I)V");
        if (set_policy != nullptr) {
            env->CallVoidMethod(runtime_inst, set_policy, 0);
            if (!env->ExceptionCheck()) {
                LOGI("Hidden API disabled via setHiddenApiPolicy on API %d", api_level);
                env->DeleteLocalRef(runtime_inst);
                return true;
            }
            env->ExceptionClear();
        }
    }
    
    env->DeleteLocalRef(runtime_inst);
    return false;
}

// ============================================================
// ANDROID 14+ ALTERNATIVE: Use JniHook approach
// ============================================================
bool disable_hidden_api_jnihook(JNIEnv *env) {
    int api_level = get_android_api_level();
    
    if (api_level < 34) return false;
    
    // Try to make setHiddenApiExemptions accessible via JniHook
    jclass jniHookClass = env->FindClass("com/jnihook/jni/JniHook");
    if (jniHookClass == nullptr) {
        env->ExceptionClear();
        return false;
    }
    
    jclass vmRuntimeClass = env->FindClass("dalvik/system/VMRuntime");
    if (vmRuntimeClass == nullptr) {
        env->ExceptionClear();
        return false;
    }
    
    jmethodID getRuntime = env->GetStaticMethodID(vmRuntimeClass, "getRuntime", "()Ldalvik/system/VMRuntime;");
    if (getRuntime == nullptr) {
        env->ExceptionClear();
        return false;
    }
    
    jobject runtimeInstance = env->CallStaticObjectMethod(vmRuntimeClass, getRuntime);
    if (runtimeInstance == nullptr || env->ExceptionCheck()) {
        env->ExceptionClear();
        return false;
    }
    
    // Try to find and make setHiddenApiExemptions accessible
    jmethodID setExemptions = env->GetMethodID(vmRuntimeClass, "setHiddenApiExemptions", "([Ljava/lang/String;)V");
    if (setExemptions == nullptr) {
        env->ExceptionClear();
        env->DeleteLocalRef(runtimeInstance);
        return false;
    }
    
    // Use JniHook to make the method accessible
    jmethodID setAccessible = env->GetStaticMethodID(jniHookClass, "setAccessible", 
                                                      "(Ljava/lang/Class;Ljava/lang/reflect/Method;)V");
    if (setAccessible != nullptr) {
        jobject methodObj = env->ToReflectedMethod(vmRuntimeClass, setExemptions, false);
        if (methodObj != nullptr) {
            env->CallStaticVoidMethod(jniHookClass, setAccessible, vmRuntimeClass, methodObj);
            env->DeleteLocalRef(methodObj);
        }
    }
    
    // Now try to call it
    jclass stringClass = env->FindClass("java/lang/String");
    if (stringClass != nullptr) {
        jstring str = env->NewStringUTF("L");
        if (str != nullptr) {
            jobjectArray exemptions = env->NewObjectArray(1, stringClass, str);
            if (exemptions != nullptr) {
                env->CallVoidMethod(runtimeInstance, setExemptions, exemptions);
                
                env->DeleteLocalRef(str);
                env->DeleteLocalRef(exemptions);
                
                if (!env->ExceptionCheck()) {
                    LOGI("Hidden API disabled via JniHook on API %d", api_level);
                    env->DeleteLocalRef(runtimeInstance);
                    return true;
                }
                env->ExceptionClear();
            }
            env->DeleteLocalRef(str);
        }
    }
    
    env->DeleteLocalRef(runtimeInstance);
    return false;
}

// ============================================================
// ANDROID 9-17: Java-level fallback
// ============================================================
bool disable_hidden_api_java_fallback(JNIEnv *env) {
    // Try to call VMRuntime.setHiddenApiExemptions via JNI
    jclass vm_runtime_cls = env->FindClass("dalvik/system/VMRuntime");
    if (vm_runtime_cls == nullptr) {
        env->ExceptionClear();
        return false;
    }

    jmethodID get_runtime = env->GetStaticMethodID(vm_runtime_cls, "getRuntime", "()Ldalvik/system/VMRuntime;");
    if (get_runtime == nullptr) {
        env->ExceptionClear();
        return false;
    }

    jobject runtime_inst = env->CallStaticObjectMethod(vm_runtime_cls, get_runtime);
    if (runtime_inst == nullptr || env->ExceptionCheck()) {
        env->ExceptionClear();
        return false;
    }

    // Try setting via field
    jfieldID exemptionsField = env->GetFieldID(vm_runtime_cls, "hiddenApiExemptions", "[Ljava/lang/String;");
    if (exemptionsField != nullptr) {
        jclass string_cls = env->FindClass("java/lang/String");
        if (string_cls != nullptr) {
            jstring str = env->NewStringUTF("L");
            if (str != nullptr) {
                jobjectArray exemptions = env->NewObjectArray(1, string_cls, str);
                if (exemptions != nullptr) {
                    env->SetObjectField(runtime_inst, exemptionsField, exemptions);
                    
                    env->DeleteLocalRef(str);
                    env->DeleteLocalRef(exemptions);
                    
                    if (!env->ExceptionCheck()) {
                        LOGI("Hidden API disabled via field access on API %d", get_android_api_level());
                        env->DeleteLocalRef(runtime_inst);
                        return true;
                    }
                    env->ExceptionClear();
                }
                env->DeleteLocalRef(str);
            }
        }
    }

    env->DeleteLocalRef(runtime_inst);
    return false;
}

// ============================================================
// MAIN FUNCTION - Android 9 to 17 Support
// ============================================================
bool disable_hidden_api(JNIEnv *env) {
    if (env == nullptr) return false;

    int api_level = get_android_api_level();
    LOGI("Attempting to disable hidden API on API level: %d", api_level);

    // Try all methods in order
    
    // Method 1: Native hooking (API 28-33)
    if (api_level >= 28 && api_level <= 33) {
        LOGI("Trying native hook method...");
        if (disable_hidden_api_native(env)) return true;
    }

    // Method 2: JniHook approach (API 34+)
    if (api_level >= 34) {
        LOGI("Trying JniHook method...");
        if (disable_hidden_api_jnihook(env)) return true;
    }

    // Method 3: Reflection (API 34+)
    if (api_level >= 34) {
        LOGI("Trying reflection method...");
        if (disable_hidden_api_reflection(env)) return true;
    }

    // Method 4: Java fallback (All APIs)
    LOGI("Trying Java fallback method...");
    if (disable_hidden_api_java_fallback(env)) return true;

    // Method 5: Try to disable using System.setProperty (Last resort)
    jclass systemClass = env->FindClass("java/lang/System");
    if (systemClass != nullptr) {
        jmethodID setProperty = env->GetStaticMethodID(systemClass, "setProperty", 
                                                       "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
        if (setProperty != nullptr) {
            jstring key = env->NewStringUTF("persist.android.hiddenapi.enforce");
            jstring value = env->NewStringUTF("false");
            
            jstring result = (jstring)env->CallStaticObjectMethod(systemClass, setProperty, key, value);
            
            env->DeleteLocalRef(key);
            env->DeleteLocalRef(value);
            
            if (result != nullptr) {
                env->DeleteLocalRef(result);
            }
            
            if (!env->ExceptionCheck()) {
                LOGI("Hidden API disabled via System.setProperty");
                return true;
            }
            env->ExceptionClear();
        }
    }

    LOGE("Failed to disable hidden API on API %d", api_level);
    return false;
}
