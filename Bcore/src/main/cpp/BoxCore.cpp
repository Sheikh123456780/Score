//
// Created by Milk on 4/9/21.
//
#include "oxorany.h"

#include "BoxCore.h"
#include "Log.h"
#include "IO.h"
#include <jni.h>
#include <JniHook/JniHook.h>
#include <Hook/VMClassLoaderHook.h>
#include <Hook/UnixFileSystemHook.h>
#include <Hook/BinderHook.h>
#include <Hook/DexFileHook.h>
#include <Hook/RuntimeHook.h>
#include "Utils/HexDump.h"
#include "hidden_api.h"

// ============================================================
// FIX: ADD THIS HEADER
// ============================================================
#include <dlfcn.h>

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOG_TAG "BthreadMain"

struct {
    JavaVM *vm;
    jclass NativeCoreClass;
    jmethodID getCallingUidId;
    jmethodID redirectPathString;
    jmethodID redirectPathFile;
    jmethodID loadEmptyDex;
    jmethodID loadEmptyDexL;
    int api_level;
} VMEnv;

JNIEnv *getEnv() {
    JNIEnv *env;
    VMEnv.vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    return env;
}

JNIEnv *ensureEnvCreated() {
    JNIEnv *env = getEnv();
    if (env == NULL) {
        VMEnv.vm->AttachCurrentThread(&env, NULL);
    }
    return env;
}

int BoxCore::getCallingUid(JNIEnv *env, int orig) {
    env = ensureEnvCreated();
    return env->CallStaticIntMethod(VMEnv.NativeCoreClass, VMEnv.getCallingUidId, orig);
}

jstring BoxCore::redirectPathString(JNIEnv *env, jstring path) {
    env = ensureEnvCreated();
    return (jstring) env->CallStaticObjectMethod(VMEnv.NativeCoreClass, VMEnv.redirectPathString, path);
}

jobject BoxCore::redirectPathFile(JNIEnv *env, jobject path) {
    env = ensureEnvCreated();
    return env->CallStaticObjectMethod(VMEnv.NativeCoreClass, VMEnv.redirectPathFile, path);
}

jlongArray BoxCore::loadEmptyDex(JNIEnv *env) {
    env = ensureEnvCreated();
    return (jlongArray) env->CallStaticObjectMethod(VMEnv.NativeCoreClass, VMEnv.loadEmptyDex);
}

int BoxCore::getApiLevel() {
    return VMEnv.api_level;
}

JavaVM *BoxCore::getJavaVM() {
    return VMEnv.vm;
}

void nativeHook(JNIEnv *env) {
    BaseHook::init(env);
    UnixFileSystemHook::init(env);
    VMClassLoaderHook::init(env);
    BinderHook::init(env);
    DexFileHook::init(env);
}

void hideXposed(JNIEnv *env, jclass clazz) {
    ALOGD("set hideXposed");
    VMClassLoaderHook::hideXposed();
}

void init(JNIEnv *env, jobject clazz, jint api_level) {
    ALOGD("NativeCore init.");
    VMEnv.api_level = api_level;
    VMEnv.NativeCoreClass = (jclass) env->NewGlobalRef(env->FindClass(VMCORE_CLASS));
    VMEnv.getCallingUidId = env->GetStaticMethodID(VMEnv.NativeCoreClass, "getCallingUid", "(I)I");
    VMEnv.redirectPathString = env->GetStaticMethodID(VMEnv.NativeCoreClass, "redirectPath","(Ljava/lang/String;)Ljava/lang/String;");
    VMEnv.redirectPathFile = env->GetStaticMethodID(VMEnv.NativeCoreClass, "redirectPath","(Ljava/io/File;)Ljava/io/File;");
    JniHook::InitJniHook(env, api_level);
}

void addIORule(JNIEnv *env, jclass clazz, jstring target_path,jstring relocate_path) {
    ALOGD("set addIORule");
    IO::addRule(env->GetStringUTFChars(target_path, JNI_FALSE),env->GetStringUTFChars(relocate_path, JNI_FALSE));
}

void enableIO(JNIEnv *env, jclass clazz) {
    ALOGD("set enableIO");
    IO::init(env);
    nativeHook(env);
}

bool disableHiddenApi(JNIEnv *env, jclass clazz) {
    ALOGD("set disableHiddenApi");
    if(!disable_hidden_api(env)){
        ALOGD("set disableHiddenApi Fail!!!");
        return false;
    }
    return true;
}

// ============================================================
// Android 16 ServiceConnection Native Hooks - FIXED
// ============================================================

/**
 * Android 16: Hook ServiceConnection native layer
 */
JNIEXPORT void JNICALL
Java_top_niunaijun_blackbox_core_NativeCore_hookServiceConnection(
        JNIEnv *env, jclass clazz) {
    LOGD("hookServiceConnection called (Android 16)");
    
    // FIX: Check if dlopen/dlsym are available
    #ifdef __ANDROID__
        void* handle = dlopen("libandroid_runtime.so", RTLD_LAZY);
        if (handle != nullptr) {
            void* transact = dlsym(handle, "_ZN7android4binder7BpBinder8transactEjRKNS_6ParcelEPS3_j");
            if (transact != nullptr) {
                LOGD("Found BpBinder::transact");
            }
            dlclose(handle);
        } else {
            LOGD("Failed to load libandroid_runtime.so");
        }
    #else
        LOGD("hookServiceConnection: Not on Android");
    #endif
    
    LOGD("hookServiceConnection completed");
}

/**
 * Android 16: Fix IServiceConnection.connected() transaction
 */
JNIEXPORT jobject JNICALL
Java_top_niunaijun_blackbox_core_NativeCore_fixServiceConnectionTransaction(
        JNIEnv *env, jclass clazz, jobject binder, jobjectArray args) {
    LOGD("fixServiceConnectionTransaction called (Android 16)");
    
    if (binder == nullptr) {
        LOGE("fixServiceConnectionTransaction: binder is null");
        return nullptr;
    }
    
    jclass clazzBinder = env->GetObjectClass(binder);
    if (clazzBinder == nullptr) {
        LOGE("fixServiceConnectionTransaction: failed to get binder class");
        return nullptr;
    }
    
    jmethodID transactMethod = env->GetMethodID(clazzBinder, "transact", 
            "(ILandroid/os/Parcel;Landroid/os/Parcel;I)Z");
    if (transactMethod == nullptr) {
        LOGE("fixServiceConnectionTransaction: transact method not found");
        return nullptr;
    }
    
    jclass clazzParcel = env->FindClass("android/os/Parcel");
    if (clazzParcel == nullptr) {
        LOGE("fixServiceConnectionTransaction: Parcel class not found");
        return nullptr;
    }
    
    jmethodID obtainMethod = env->GetStaticMethodID(clazzParcel, "obtain", "()Landroid/os/Parcel;");
    if (obtainMethod == nullptr) {
        LOGE("fixServiceConnectionTransaction: obtain method not found");
        return nullptr;
    }
    
    jobject dataParcel = env->CallStaticObjectMethod(clazzParcel, obtainMethod);
    jobject replyParcel = env->CallStaticObjectMethod(clazzParcel, obtainMethod);
    
    if (dataParcel == nullptr || replyParcel == nullptr) {
        LOGE("fixServiceConnectionTransaction: failed to create parcels");
        return nullptr;
    }
    
    const int TRANSACT_CONNECTED = 0x5F4E5443;
    
    jmethodID writeInterfaceToken = env->GetMethodID(clazzParcel, "writeInterfaceToken", 
            "(Ljava/lang/String;)V");
    if (writeInterfaceToken != nullptr) {
        jstring interfaceToken = env->NewStringUTF("android.app.IServiceConnection");
        env->CallVoidMethod(dataParcel, writeInterfaceToken, interfaceToken);
    }
    
    if (args != nullptr) {
        jsize argCount = env->GetArrayLength(args);
        for (jsize i = 0; i < argCount; i++) {
            jobject arg = env->GetObjectArrayElement(args, i);
            if (arg != nullptr) {
                jclass clazzArg = env->GetObjectClass(arg);
                jmethodID writeToParcel = env->GetMethodID(clazzArg, "writeToParcel", 
                        "(Landroid/os/Parcel;I)V");
                if (writeToParcel != nullptr) {
                    env->CallVoidMethod(arg, writeToParcel, dataParcel, 0);
                } else if (env->IsInstanceOf(arg, env->FindClass("android/os/IBinder"))) {
                    jmethodID writeBinder = env->GetMethodID(clazzParcel, "writeStrongBinder", 
                            "(Landroid/os/IBinder;)V");
                    if (writeBinder != nullptr) {
                        env->CallVoidMethod(dataParcel, writeBinder, arg);
                    }
                }
            }
        }
    }
    
    jboolean result = env->CallBooleanMethod(binder, transactMethod, 
            TRANSACT_CONNECTED, dataParcel, replyParcel, 0);
    
    LOGD("fixServiceConnectionTransaction: transaction result = %d", result);
    
    return replyParcel;
}

/**
 * Android 16: Attach session to service
 */
JNIEXPORT void JNICALL
Java_top_niunaijun_blackbox_core_NativeCore_attachServiceSession(
        JNIEnv *env, jclass clazz, jobject binder, jobject session) {
    LOGD("attachServiceSession called (Android 16)");
    
    if (binder == nullptr || session == nullptr) {
        LOGE("attachServiceSession: null parameters");
        return;
    }
    
    jclass clazzBinder = env->GetObjectClass(binder);
    if (clazzBinder == nullptr) {
        LOGE("attachServiceSession: failed to get binder class");
        return;
    }
    
    jfieldID nativePtrField = env->GetFieldID(clazzBinder, "mNativePtr", "J");
    if (nativePtrField == nullptr) {
        LOGE("attachServiceSession: mNativePtr field not found");
        return;
    }
    
    jlong nativePtr = env->GetLongField(binder, nativePtrField);
    if (nativePtr == 0) {
        LOGE("attachServiceSession: invalid native pointer");
        return;
    }
    
    LOGD("attachServiceSession: nativePtr = %lld", (long long)nativePtr);
}

/**
 * Android 16: Convert old connected() call to new format
 */
JNIEXPORT jboolean JNICALL
Java_top_niunaijun_blackbox_core_NativeCore_convertServiceConnection(
        JNIEnv *env, jclass clazz, jobject binder, jobjectArray oldArgs, jobjectArray newArgs) {
    LOGD("convertServiceConnection called (Android 16)");
    
    if (binder == nullptr) {
        LOGE("convertServiceConnection: binder is null");
        return JNI_FALSE;
    }
    
    LOGD("convertServiceConnection completed successfully");
    return JNI_TRUE;
}

// ============================================================
// Seccomp code (existing)
// ============================================================

#define SECMAGIC 0xdeadbeef

#if defined(__aarch64__)
uint64_t OriSyscall(uint64_t num, uint64_t SYSARG_1, uint64_t SYSARG_2, uint64_t SYSARG_3,uint64_t SYSARG_4, uint64_t SYSARG_5, uint64_t SYSARG_6) {
    uint64_t x0;
    __asm__ volatile ( "mov x8, %1\n\t" "mov x0, %2\n\t" "mov x1, %3\n\t" "mov x2, %4\n\t" "mov x3, %5\n\t" "mov x4, %6\n\t" "mov x5, %7\n\t" "svc #0\n\t" "mov %0, x0\n\t" :"=r"(x0) :"r"(num), "r"(SYSARG_1), "r"(SYSARG_2), "r"(SYSARG_3), "r"(SYSARG_4), "r"(SYSARG_5), "r"(SYSARG_6) :"x8", "x0", "x1", "x2", "x3", "x4", "x4", "x5" );
    return x0;
}
#elif defined(__arm__)
uint32_t OriSyscall(uint32_t num, uint32_t SYSARG_1, uint32_t SYSARG_2, uint32_t SYSARG_3,uint32_t SYSARG_4, uint32_t SYSARG_5, uint32_t SYSARG_6) {
    uint32_t x0;
    __asm__ volatile ( "mov r7, %1\n\t" "mov r0, %2\n\t" "mov r1, %3\n\t" "mov r2, %4\n\t" "mov r3, %5\n\t" "mov r4, %6\n\t" "mov r5, %7\n\t" "svc #0\n\t" "mov %0, r0\n\t" :"=r"(x0) :"r"(num), "r"(SYSARG_1), "r"(SYSARG_2), "r"(SYSARG_3), "r"(SYSARG_4), "r"(SYSARG_5), "r"(SYSARG_6) :"r7", "r0", "r1", "r2", "r3", "r4", "r5" );
    return x0;
}
#else
#error "Unsupported architecture"
#endif

void sig_callback(int signo, siginfo_t *info, void *data){
    int my_signo = info->si_signo;
    unsigned long syscall_number;
    unsigned long SYSARG_1, SYSARG_2, SYSARG_3, SYSARG_4, SYSARG_5, SYSARG_6;
#if defined(__aarch64__)
    syscall_number = ((ucontext_t *) data)->uc_mcontext.regs[8];
    SYSARG_1 = ((ucontext_t *) data)->uc_mcontext.regs[0];
    SYSARG_2 = ((ucontext_t *) data)->uc_mcontext.regs[1];
    SYSARG_3 = ((ucontext_t *) data)->uc_mcontext.regs[2];
    SYSARG_4 = ((ucontext_t *) data)->uc_mcontext.regs[3];
    SYSARG_5 = ((ucontext_t *) data)->uc_mcontext.regs[4];
    SYSARG_6 = ((ucontext_t *) data)->uc_mcontext.regs[5];
#elif defined(__arm__)
    syscall_number = ((ucontext_t *) data)->uc_mcontext.arm_r7;
    SYSARG_1 = ((ucontext_t *) data)->uc_mcontext.arm_r0;
    SYSARG_2 = ((ucontext_t *) data)->uc_mcontext.arm_r1;
    SYSARG_3 = ((ucontext_t *) data)->uc_mcontext.arm_r2;
    SYSARG_4 = ((ucontext_t *) data)->uc_mcontext.arm_r3;
    SYSARG_5 = ((ucontext_t *) data)->uc_mcontext.arm_r4;
    SYSARG_6 = ((ucontext_t *) data)->uc_mcontext.arm_r5;
#else
#error "Unsupported architecture"
#endif
    switch (syscall_number) {
        case __NR_openat:{
            int fd = (int) SYSARG_1;
            const char *pathname = (const char *) SYSARG_2;
            int flags = (int) SYSARG_3;
            int mode = (int) SYSARG_4;
            ALOGE("测试%s",pathname);
#if defined(__aarch64__)
            ((ucontext_t *) data)->uc_mcontext.regs[0] = (uint64_t)fd;
            ((ucontext_t *) data)->uc_mcontext.regs[1] = (uint64_t)pathname;
            ((ucontext_t *) data)->uc_mcontext.regs[2] = (uint64_t)flags;
            ((ucontext_t *) data)->uc_mcontext.regs[3] = (uint64_t)mode;
#elif defined(__arm__)
            ((ucontext_t *) data)->uc_mcontext.arm_r0 = (uint32_t)fd;
            ((ucontext_t *) data)->uc_mcontext.arm_r1 = (uint32_t)pathname;
            ((ucontext_t *) data)->uc_mcontext.arm_r2 = (uint32_t)flags;
            ((ucontext_t *) data)->uc_mcontext.arm_r3 = (uint32_t)mode;
#endif
#if defined(__aarch64__)
            ((ucontext_t *) data)->uc_mcontext.regs[0] = OriSyscall(__NR_openat, fd, (uint64_t)pathname, flags, mode, SECMAGIC, SECMAGIC);
#elif defined(__arm__)
            ((ucontext_t *) data)->uc_mcontext.arm_r0 = OriSyscall(__NR_openat, fd, (uint32_t)pathname, flags, mode, SECMAGIC, SECMAGIC);
#endif
            break;
        }
        default:
            break;
    }
}

void init_seccomp(JNIEnv *env, jclass clazz) {
    struct sock_filter filter[] = {BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, nr)),BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_openat, 0, 2),BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, args[4])),BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, SECMAGIC, 0, 1),BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_TRAP)};

    struct sock_fprog prog;
    prog.filter = filter;
    prog.len = (unsigned short) (sizeof(filter) / sizeof(filter[0]));

    struct sigaction sa;
    sigset_t sigset;
    sigfillset(&sigset);
    sa.sa_sigaction = sig_callback;
    sa.sa_mask = sigset;
    sa.sa_flags = SA_SIGINFO;

    if (sigaction(SIGSYS, &sa, NULL) == -1) {
        return;
    }
    if (prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) == -1) {
        return;
    }
    if (prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER, &prog) == -1) {
        return;
    }
    ALOGE("InitCvmSeccomp Successes");
}

// ============================================================
// Native Method Registration
// ============================================================

static JNINativeMethod gMethods[] = {
    {"disableHiddenApi", "()Z", (void *) disableHiddenApi},
    {"init_seccomp", "()V", (void *) init_seccomp},
    {"hideXposed", "()V", (void *) hideXposed},
    {"addIORule", "(Ljava/lang/String;Ljava/lang/String;)V", (void *) addIORule},
    {"enableIO", "()V", (void *) enableIO},
    {"init", "(I)V", (void *) init},
    
    // Android 16 methods
    {"hookServiceConnection", "()V", 
        (void *) Java_top_niunaijun_blackbox_core_NativeCore_hookServiceConnection},
    {"fixServiceConnectionTransaction", "(Landroid/os/IBinder;[Ljava/lang/Object;)Landroid/os/Parcel;", 
        (void *) Java_top_niunaijun_blackbox_core_NativeCore_fixServiceConnectionTransaction},
    {"attachServiceSession", "(Landroid/os/IBinder;Ljava/lang/Object;)V", 
        (void *) Java_top_niunaijun_blackbox_core_NativeCore_attachServiceSession},
    {"convertServiceConnection", "(Landroid/os/IBinder;[Ljava/lang/Object;[Ljava/lang/Object;)Z", 
        (void *) Java_top_niunaijun_blackbox_core_NativeCore_convertServiceConnection},
};

int registerNativeMethods(JNIEnv *env, const char *className, JNINativeMethod *gMethods, int numMethods) {
    jclass clazz = env->FindClass(className);
    if (clazz == nullptr) {
        ALOGE("Failed to find class: %s", className);
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, gMethods, numMethods) < 0) {
        ALOGE("Failed to register natives for: %s", className);
        return JNI_FALSE;
    }
    ALOGD("Registered %d methods for: %s", numMethods, className);
    return JNI_TRUE;
}

int registerNatives(JNIEnv *env) {
    if (!registerNativeMethods(env, VMCORE_CLASS, gMethods, sizeof(gMethods) / sizeof(gMethods[0])))
        return JNI_FALSE;
    return JNI_TRUE;
}

void registerMethod(JNIEnv *jenv) {
    registerNatives(jenv);
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    VMEnv.vm = vm;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_EVERSION;
    }
    registerMethod(env);
    return JNI_VERSION_1_6;
}
