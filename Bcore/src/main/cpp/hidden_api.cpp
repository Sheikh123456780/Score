#include <jni.h>
#include <sys/system_properties.h>
#include <cstring>
#include <cstdlib>
#include "hidden_api.h"
#include "Log.h"
#include "SandHook/ElfImg.h"

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
    
    if (api_level >= 28 && api_level <= 33) {
        // Try multiple symbol names for different ART versions
        const char* symbol_names[] = {
            "_ZN3artL32VMRuntime_setHiddenApiExemptionsEP7_JNIEnvP7_jclassP13_jobjectArray",
            "_ZN3art12VMRuntime22setHiddenApiExemptionsEP7_JNIEnvP13_jobjectArray",
            "_ZN3artL17SetHiddenApiStateEP7_JNIEnvP13_jobjectArray",
            nullptr
        };

        SandHook::ElfImg elf_img("libart.so");
        
        for (int i = 0; symbol_names[i] != nullptr; i++) {
            void *addr = (void*)elf_img.getSymbAddress(symbol_names[i]);
            if (addr != nullptr) {
                jclass stringClass = env->FindClass("java/lang/String");
                if (stringClass == nullptr) {
                    env->ExceptionClear();
                    continue;
                }
                
                jobjectArray args = env->NewObjectArray(1, stringClass, env->NewStringUTF("L"));
                if (args == nullptr) {
                    env->ExceptionClear();
                    continue;
                }
                
                auto func = reinterpret_cast<void (*)(JNIEnv *, jclass, jobjectArray)>(addr);
                func(env, stringClass, args);
                
                if (!env->ExceptionCheck()) {
                    LOGI("Hidden API disabled via native hook: %s", symbol_names[i]);
                    return true;
                }
                env->ExceptionClear();
            }
        }
    }
    return false;
}

// ============================================================
// ANDROID 14+ (API 34-37+): JNI Reflection Bypass
// ============================================================
bool disable_hidden_api_reflection(JNIEnv *env) {
    int api_level = get_android_api_level();
    
    if (api_level < 34) return false;
    
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
    if (runtime_inst == nullptr) {
        env->ExceptionClear();
        return false;
    }

    // Try multiple method signatures
    const char* method_signatures[] = {
        "setHiddenApiExemptions",
        "setHiddenApiExemptions",
        nullptr
    };

    for (int i = 0; method_signatures[i] != nullptr; i++) {
        jmethodID set_exemptions = env->GetMethodID(vm_runtime_cls, method_signatures[i], "([Ljava/lang/String;)V");
        if (set_exemptions != nullptr) {
            jclass string_cls = env->FindClass("java/lang/String");
            if (string_cls == nullptr) {
                env->ExceptionClear();
                continue;
            }
            
            jobjectArray exemptions = env->NewObjectArray(1, string_cls, env->NewStringUTF("L"));
            if (exemptions == nullptr) {
                env->ExceptionClear();
                continue;
            }

            env->CallVoidMethod(runtime_inst, set_exemptions, exemptions);
            
            if (!env->ExceptionCheck()) {
                LOGI("Hidden API disabled via reflection on API %d", api_level);
                return true;
            }
            env->ExceptionClear();
        }
    }

    // Android 16+ (API 36+): Try alternate method
    if (api_level >= 36) {
        jmethodID set_policy = env->GetMethodID(vm_runtime_cls, "setHiddenApiPolicy", "(I)V");
        if (set_policy != nullptr) {
            env->CallVoidMethod(runtime_inst, set_policy, 0); // 0 = no restrictions
            if (!env->ExceptionCheck()) {
                LOGI("Hidden API disabled via setHiddenApiPolicy on API %d", api_level);
                return true;
            }
            env->ExceptionClear();
        }
    }

    return false;
}

// ============================================================
// MAIN FUNCTION - Android 9 to 17 Support
// ============================================================
bool disable_hidden_api(JNIEnv *env) {
    if (env == nullptr) return false;

    int api_level = get_android_api_level();
    LOGI("Attempting to disable hidden API on API level: %d", api_level);

    // API 28-33: Native hooking
    if (api_level >= 28 && api_level <= 33) {
        if (disable_hidden_api_native(env)) {
            return true;
        }
    }

    // API 34+: Reflection
    if (api_level >= 34) {
        if (disable_hidden_api_reflection(env)) {
            return true;
        }
    }

    // API 28+: Try Java-level fallback
    jclass vm_runtime_cls = env->FindClass("dalvik/system/VMRuntime");
    if (vm_runtime_cls != nullptr) {
        jmethodID get_runtime = env->GetStaticMethodID(vm_runtime_cls, "getRuntime", "()Ldalvik/system/VMRuntime;");
        if (get_runtime != nullptr) {
            jobject runtime_inst = env->CallStaticObjectMethod(vm_runtime_cls, get_runtime);
            if (runtime_inst != nullptr) {
                // Try to set hidden API exemptions via field
                jfieldID exemptionsField = env->GetFieldID(vm_runtime_cls, "hiddenApiExemptions", "[Ljava/lang/String;");
                if (exemptionsField != nullptr) {
                    jclass string_cls = env->FindClass("java/lang/String");
                    if (string_cls != nullptr) {
                        jobjectArray exemptions = env->NewObjectArray(1, string_cls, env->NewStringUTF("L"));
                        if (exemptions != nullptr) {
                            env->SetObjectField(runtime_inst, exemptionsField, exemptions);
                            if (!env->ExceptionCheck()) {
                                LOGI("Hidden API disabled via field access on API %d", api_level);
                                return true;
                            }
                            env->ExceptionClear();
                        }
                    }
                }
            }
        }
    }

    LOGE("Failed to disable hidden API on API %d", api_level);
    return false;
}
