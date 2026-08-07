#include <jni.h>
#include <sys/system_properties.h>
#include <cstring>
#include <cstdlib>
#include <dlfcn.h>
#include <unistd.h>
#include "hidden_api.h"
#include "Log.h"
#include "Utils/fake_dlfcn.h"

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
// ANDROID 9-17: Native Symbol Hooking via fake_dlfcn
// ============================================================
bool disable_hidden_api_native(JNIEnv *env) {
    int api_level = get_android_api_level();
    if (api_level < 28) return false;
    
    // Symbol names across Android 9 through 17
    const char* symbol_names[] = {
        "_ZN3artL32VMRuntime_setHiddenApiExemptionsEP7_JNIEnvP7_jclassP13_jobjectArray",
        "_ZN3art12VMRuntime22setHiddenApiExemptionsEP7_JNIEnvP13_jobjectArray",
        "_ZN3artL17SetHiddenApiStateEP7_JNIEnvP13_jobjectArray",
        "_ZN3art12VMRuntime27setHiddenApiExemptionsNativeEP7_JNIEnvP7_jclassP13_jobjectArray",
        nullptr
    };

    // Use fake_dlopen to bypass APEX linker namespace isolation on Android 10+
    void* libart = fake_dlopen("libart.so", RTLD_NOW);
    if (libart == nullptr) {
        // Fallback to APEX path directly
        libart = fake_dlopen("/apex/com.android.art/lib64/libart.so", RTLD_NOW);
    }
    
    if (libart == nullptr) {
        LOGE("Failed to fake_dlopen libart.so");
        return false;
    }
    
    bool success = false;
    for (int i = 0; symbol_names[i] != nullptr; i++) {
        void *addr = fake_dlsym(libart, symbol_names[i]);
        if (addr == nullptr) continue;
        
        jclass stringClass = env->FindClass("java/lang/String");
        if (stringClass == nullptr) {
            env->ExceptionClear();
            continue;
        }
        
        jstring str = env->NewStringUTF("L");
        if (str == nullptr) {
            env->ExceptionClear();
            env->DeleteLocalRef(stringClass);
            continue;
        }
        
        jobjectArray args = env->NewObjectArray(1, stringClass, str);
        if (args == nullptr) {
            env->ExceptionClear();
            env->DeleteLocalRef(str);
            env->DeleteLocalRef(stringClass);
            continue;
        }
        
        auto func = reinterpret_cast<void (*)(JNIEnv *, jclass, jobjectArray)>(addr);
        func(env, stringClass, args);
        
        env->DeleteLocalRef(str);
        env->DeleteLocalRef(args);
        env->DeleteLocalRef(stringClass);
        
        if (!env->ExceptionCheck()) {
            LOGI("Hidden API disabled via native symbol: %s", symbol_names[i]);
            success = true;
            break;
        }
        env->ExceptionClear();
    }
    
    fake_dlclose(libart);
    return success;
}

// ============================================================
// ANDROID 14+ JNI Reflection Fallback
// ============================================================
bool disable_hidden_api_reflection(JNIEnv *env) {
    jclass vm_runtime_cls = env->FindClass("dalvik/system/VMRuntime");
    if (vm_runtime_cls == nullptr) {
        env->ExceptionClear();
        return false;
    }

    jmethodID get_runtime = env->GetStaticMethodID(vm_runtime_cls, "getRuntime", "()Ldalvik/system/VMRuntime;");
    if (get_runtime == nullptr) {
        env->ExceptionClear();
        env->DeleteLocalRef(vm_runtime_cls);
        return false;
    }

    jobject runtime_inst = env->CallStaticObjectMethod(vm_runtime_cls, get_runtime);
    if (runtime_inst == nullptr || env->ExceptionCheck()) {
        env->ExceptionClear();
        env->DeleteLocalRef(vm_runtime_cls);
        return false;
    }

    const char* method_names[] = {
        "setHiddenApiExemptions",
        "setHiddenApiExemptionsNative",
        nullptr
    };

    bool success = false;
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
        jobjectArray exemptions = env->NewObjectArray(1, string_cls, str);

        env->CallVoidMethod(runtime_inst, set_exemptions, exemptions);
        
        env->DeleteLocalRef(str);
        env->DeleteLocalRef(exemptions);
        env->DeleteLocalRef(string_cls);
        
        if (!env->ExceptionCheck()) {
            LOGI("Hidden API disabled via reflection using %s", method_names[i]);
            success = true;
            break;
        }
        env->ExceptionClear();
    }

    env->DeleteLocalRef(runtime_inst);
    env->DeleteLocalRef(vm_runtime_cls);
    return success;
}

// ============================================================
// MAIN DISABLER FUNCTION
// ============================================================
bool disable_hidden_api(JNIEnv *env) {
    if (env == nullptr) return false;

    int api_level = get_android_api_level();
    LOGI("Disabling hidden API checks on API level %d", api_level);

    if (disable_hidden_api_native(env)) return true;
    if (disable_hidden_api_reflection(env)) return true;

    LOGE("Failed to disable hidden API on API level %d", api_level);
    return false;
}
