//
// Created by Milk on 4/10/21.
// Updated for Android 16 (SDK 36).
//

#ifndef VIRTUALM_BASEHOOK_H
#define VIRTUALM_BASEHOOK_H

#include <jni.h>
#include <Log.h>

// ============================================================
// Shared HOOK_JNI macro.
//
// Every hook file includes BaseHook.h (directly or via its own
// header), so defining it here makes all of them self-contained.
// Previously this macro lived in a header that is no longer
// transitively included, which caused errors such as
// "unknown type name 'openDexFileNative'".
//
// Expansion:
//   HOOK_JNI(ret, name, args...)
//     -> static ret new_##name(args);
//        static ret (*orig_##name)(args) = nullptr;
//        static ret new_##name(args)
// ============================================================
#ifndef HOOK_JNI
#define HOOK_JNI(ret, name, ...)                       \
    static ret new_##name(__VA_ARGS__);                \
    static ret (*orig_##name)(__VA_ARGS__) = nullptr;  \
    static ret new_##name(__VA_ARGS__)
#endif

class BaseHook {
public:
    static void init(JNIEnv *env);
};

#endif // VIRTUALM_BASEHOOK_H
