#include <jni.h>
#include <sys/system_properties.h>
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

bool disable_hidden_api(JNIEnv *env) {
    int api_level = get_android_api_level();

    // Android 8.1 and below do not restrict hidden APIs
    if (api_level < 28) {
        return true;
    }

    // Android 9 to 13 (API 28 to 33) - Native Symbol Hooking
    if (api_level <= 33) {
        SandHook::ElfImg elf_img("libart.so");
        void *addr = (void*)elf_img.getSymbAddress("_ZN3artL32VMRuntime_setHiddenApiExemptionsEP7_JNIEnvP7_jclassP13_jobjectArray");
        if (addr) {
            jclass stringClass = env->FindClass("java/lang/String");
            jobjectArray args = env->NewObjectArray(1, stringClass, env->NewStringUTF("L"));
            auto func = reinterpret_cast<void (*)(JNIEnv *, jclass, jobjectArray)>(addr);
            func(env, stringClass, args);
            return true;
        }
    }

    // Android 14 to 17 (API 34 to 37+) - Fallback JNI Reflection Bypass
    jclass vm_runtime_cls = env->FindClass("dalvik/system/VMRuntime");
    if (!vm_runtime_cls) return false;

    jmethodID get_runtime = env->GetStaticMethodID(vm_runtime_cls, "getRuntime", "()Ldalvik/system/VMRuntime;");
    if (!get_runtime) return false;

    jobject runtime_inst = env->CallStaticObjectMethod(vm_runtime_cls, get_runtime);
    if (!runtime_inst) return false;

    jmethodID set_exemptions = env->GetMethodID(vm_runtime_cls, "setHiddenApiExemptions", "([Ljava/lang/String;)V");
    if (!set_exemptions) return false;

    jclass string_cls = env->FindClass("java/lang/String");
    jobjectArray exemptions = env->NewObjectArray(1, string_cls, env->NewStringUTF("L"));

    env->CallVoidMethod(runtime_inst, set_exemptions, exemptions);
    
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return false;
    }

    return true;
}
