//
// Created by Milk on 4/9/21.
//

#ifndef VIRTUALM_VMCORE_H
#define VIRTUALM_VMCORE_H

#include <jni.h>
#include <sys/syscall.h>
#include <linux/filter.h>
#include <linux/seccomp.h>
#include <sys/signal.h>
#include <sys/unistd.h>
#include <linux/prctl.h>
#include <sys/prctl.h>

#define VMCORE_CLASS "top/niunaijun/blackbox/core/NativeCore"

class BoxCore {
public:
    static JavaVM *getJavaVM();
    static int getApiLevel();
    static int getCallingUid(JNIEnv *env, int orig);
    static jstring redirectPathString(JNIEnv *env, jstring path);
    static jobject redirectPathFile(JNIEnv *env, jobject path);
    static jlongArray loadEmptyDex(JNIEnv *env);
};

// ============================================================
// JNI Function Prototypes for Android 16
// These must match the Java NativeCore class methods
// ============================================================

#ifdef __cplusplus
extern "C" {
#endif

// Android 16 ServiceConnection methods
JNIEXPORT void JNICALL 
Java_top_niunaijun_blackbox_core_NativeCore_hookServiceConnection(JNIEnv*, jclass);

JNIEXPORT jobject JNICALL 
Java_top_niunaijun_blackbox_core_NativeCore_fixServiceConnectionTransaction(
    JNIEnv*, jclass, jobject, jobjectArray);

JNIEXPORT void JNICALL 
Java_top_niunaijun_blackbox_core_NativeCore_attachServiceSession(
    JNIEnv*, jclass, jobject, jobject);

JNIEXPORT jboolean JNICALL 
Java_top_niunaijun_blackbox_core_NativeCore_convertServiceConnection(
    JNIEnv*, jclass, jobject, jobjectArray, jobjectArray);

// Existing methods
JNIEXPORT void JNICALL 
Java_top_niunaijun_blackbox_core_NativeCore_init(JNIEnv*, jclass, jint);

JNIEXPORT void JNICALL 
Java_top_niunaijun_blackbox_core_NativeCore_enableIO(JNIEnv*, jclass);

JNIEXPORT void JNICALL 
Java_top_niunaijun_blackbox_core_NativeCore_addIORule(JNIEnv*, jclass, jstring, jstring);

JNIEXPORT void JNICALL 
Java_top_niunaijun_blackbox_core_NativeCore_hideXposed(JNIEnv*, jclass);

JNIEXPORT jboolean JNICALL 
Java_top_niunaijun_blackbox_core_NativeCore_disableHiddenApi(JNIEnv*, jclass);

JNIEXPORT void JNICALL 
Java_top_niunaijun_blackbox_core_NativeCore_init_seccomp(JNIEnv*, jclass);

#ifdef __cplusplus
}
#endif

#endif //VIRTUALM_VMCORE_H
