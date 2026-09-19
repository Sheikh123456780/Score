//
// JniHook.h
// Updated for Android 16 (SDK 36) compatibility.
//

#ifndef JNIHOOK_H
#define JNIHOOK_H

#include <jni.h>
#include <cstdint>

class JniHook {
public:
    // Legacy signature — kept for compatibility. Skips ART hooks if
    // called directly, because the ART offset is unknown.
    static void InitJniHook(JNIEnv *env, int api_level);

    // New signature — receives the runtime-discovered access_flags_
    // offset from BoxCore.cpp.
    static void InitJniHook(JNIEnv *env, int api_level,
                            uint32_t art_method_flags_offset);

    static void HookJniFun(JNIEnv *env, jobject java_method,
                           void *new_fun, void **orig_fun, bool is_static);

    static void HookJniFun(JNIEnv *env, const char *class_name,
                           const char *method_name, const char *sign,
                           void *new_fun, void **orig_fun, bool is_static);
};

#endif // JNIHOOK_H
