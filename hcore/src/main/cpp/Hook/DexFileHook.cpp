#include "DexFileHook.h"
#include <IO.h>
#include <BoxCore.h>
#include "UnixFileSystemHook.h"
#import "JniHook/JniHook.h"
#include <sys/stat.h>

HOOK_JNI(jobject, openDexFileNative, JNIEnv *env, jobject obj,
         jstring sourceName, jstring outputName, jint flags,
         jobject loader, jobject elements) {
    const char *sourceNameC = env->GetStringUTFChars(sourceName, JNI_FALSE);
    ALOGD("openDexFileNative: %s", sourceNameC);
    if (strstr(sourceNameC, "/blackbox/") != nullptr) {
        DexFileHook::setFileReadonly(sourceNameC);
    }
    jobject orig = orig_openDexFileNative(env, obj, sourceName, outputName,
                                          flags, loader, elements);
    env->ReleaseStringUTFChars(sourceName, sourceNameC);
    return orig;
}

void DexFileHook::init(JNIEnv *env) {
    int api = BoxCore::getApiLevel();

    // Hook on Android 10 (Q) and above. Older versions either don't use
    // this native entry point or have a different signature.
    // We used to gate this to 14+ only, which broke 10–13 (writable dex
    // files -> SecurityException). Hook now covers all supported versions.
    if (api < 29) {
        ALOGD("DexFileHook: SDK %d < 29, skipping openDexFileNative hook", api);
        return;
    }

    const char *clazz = "dalvik/system/DexFile";
    const char *sig =
        "(Ljava/lang/String;Ljava/lang/String;ILjava/lang/ClassLoader;"
        "[Ldalvik/system/DexPathList$Element;)Ljava/lang/Object;";

    JniHook::HookJniFun(env, clazz, "openDexFileNative", sig,
                        (void *) new_openDexFileNative,
                        (void **) (&orig_openDexFileNative),
                        true);

    ALOGD("DexFileHook: openDexFileNative hooked for SDK %d", api);
}

void DexFileHook::setFileReadonly(const char *filePath) {
    struct stat fileStat;

    if (stat(filePath, &fileStat) != 0) {
        ALOGD("DexFileHook::setFileReadonly: %s does not exist", filePath);
        return;
    }

    if (chmod(filePath, S_IRUSR) != 0) {
        ALOGD("DexFileHook::setFileReadonly: failed to chmod %s", filePath);
    } else {
        ALOGD("DexFileHook::setFileReadonly: %s set read-only", filePath);
    }
}
