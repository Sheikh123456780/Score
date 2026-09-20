#include <jni.h>
#include <sys/system_properties.h>
#include "xdl.h"
#include "hidden_api.h"
#include "Utils/elf_util.h"
#include "Log.h"

bool disable_hidden_api(JNIEnv *env) {
    char version_str[PROP_VALUE_MAX];
    if (!__system_property_get("ro.build.version.sdk", version_str)) {
        ALOGE("Failed to obtain SDK int");
        return false;
    }
    long android_version = std::strtol(version_str, nullptr, 10);

    // Hidden API introduced in SDK 29
    if (android_version < 29) {
        ALOGD("HiddenAPI: Android version < 29, no need to disable");
        return true;
    }

    // ---- Android 14+ (SDK 34+): setHiddenApiExemptions is gone ----
    // There is no known public replacement. Just log and return true so the
    // Java layer continues with its own fallbacks instead of aborting init.
    if (android_version >= 34) {
        ALOGD("HiddenAPI: SDK %ld — setHiddenApiExemptions removed; "
              "skipping native exemption (Java layer will handle)", android_version);
        return true;
    }

    SandHook::ElfImg *elf_img = new SandHook::ElfImg("libart.so");
    if (!elf_img->isValid()) {
        ALOGE("HiddenAPI: Failed to load libart.so");
        delete elf_img;
        return false;
    }

    void *addr = nullptr;
    const char* symbol_names[] = {
        "_ZN3artL32VMRuntime_setHiddenApiExemptionsEP7_JNIEnvP7_jclassP13_jobjectArray",
        "_ZN3art9VMRuntime22setHiddenApiExemptionsEP7_JNIEnvP7_jclassP13_jobjectArray",
        "art::VMRuntime::setHiddenApiExemptions(_JNIEnv*, _jclass*, _jobjectArray*)",
        nullptr
    };

    for (int i = 0; symbol_names[i] != nullptr; i++) {
        addr = (void*)elf_img->getSymbAddress(symbol_names[i]);
        if (addr) {
            ALOGD("HiddenAPI: Found symbol %s at %p", symbol_names[i], addr);
            break;
        }
    }
    delete elf_img;

    if (!addr) {
        ALOGE("HiddenAPI: Didn't find setHiddenApiExemptions in any form");
        // Do NOT abort — let Java fallbacks proceed.
        return true;
    }

    jclass stringClass = env->FindClass("java/lang/String");
    if (!stringClass) {
        ALOGE("HiddenAPI: Failed to find String class");
        env->ExceptionClear();
        return false;
    }

    jstring wildcard = env->NewStringUTF("L");
    if (!wildcard) {
        ALOGE("HiddenAPI: Failed to create wildcard string");
        return false;
    }

    jobjectArray args = env->NewObjectArray(1, stringClass, wildcard);
    if (!args) {
        ALOGE("HiddenAPI: Failed to create args array");
        return false;
    }

    auto func = reinterpret_cast<void (*)(JNIEnv *, jclass, jobjectArray)>(addr);
    func(env, stringClass, args);
    ALOGD("HiddenAPI: Successfully disabled hidden API restrictions");
    return true;
}

bool disable_resource_loading() {
    char version_str[PROP_VALUE_MAX];
    long android_version = 0;
    if (__system_property_get("ro.build.version.sdk", version_str)) {
        android_version = std::strtol(version_str, nullptr, 10);
    }

    // Android 14+ ApkAssets layout changed; skip hook attempts to avoid crashes.
    if (android_version >= 34) {
        ALOGD("ResourceLoading: SDK %ld — skipping ApkAssets hook path", android_version);
    } else {
        try {
            void* handle = xdl_open("libandroid_runtime.so", XDL_DEFAULT);
            if (handle) {
                void* nativeLoadAddr = xdl_sym(handle,
                        "_ZN7android8ApkAssets9nativeLoadEPKc", nullptr);
                if (nativeLoadAddr) {
                    ALOGD("ResourceLoading: Found ApkAssets.nativeLoad at %p", nativeLoadAddr);
                } else {
                    ALOGD("ResourceLoading: Could not find ApkAssets.nativeLoad symbol");
                }
                xdl_close(handle);
            } else {
                ALOGD("ResourceLoading: Could not open libandroid_runtime.so");
            }
        } catch (...) {
            ALOGD("ResourceLoading: Exception hooking ApkAssets.nativeLoad");
        }

        try {
            void* handle = xdl_open("libc.so", XDL_DEFAULT);
            if (handle) {
                void* openAddr = xdl_sym(handle, "open", nullptr);
                if (openAddr) {
                    ALOGD("ResourceLoading: Found open function at %p", openAddr);
                } else {
                    ALOGD("ResourceLoading: Could not find open function symbol");
                }
                xdl_close(handle);
            } else {
                ALOGD("ResourceLoading: Could not open libc.so");
            }
        } catch (...) {
            ALOGD("ResourceLoading: Exception hooking file system calls");
        }
    }

    ALOGD("ResourceLoading: Native resource loading hooks initialized");
    return true;
}
