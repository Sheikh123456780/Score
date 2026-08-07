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
#include "Utils/PointerCheck.h" // Used to safely validate pointers in sig_callback
#include "hidden_api.h"

#include <cstring>
#include <unistd.h>
#include <csignal>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <linux/seccomp.h>
#include <linux/filter.h>

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
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
    bool initialized;
} VMEnv;

JNIEnv *getEnv() {
    JNIEnv *env = nullptr;
    if (VMEnv.vm != nullptr) {
        VMEnv.vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    }
    return env;
}

JNIEnv *ensureEnvCreated() {
    JNIEnv *env = getEnv();
    if (env == nullptr && VMEnv.vm != nullptr) {
        VMEnv.vm->AttachCurrentThread(&env, nullptr);
    }
    return env;
}

int BoxCore::getCallingUid(JNIEnv *env, int orig) {
    env = ensureEnvCreated();
    if (VMEnv.getCallingUidId == nullptr || env == nullptr) {
        return orig;
    }
    return env->CallStaticIntMethod(VMEnv.NativeCoreClass, VMEnv.getCallingUidId, orig);
}

jstring BoxCore::redirectPathString(JNIEnv *env, jstring path) {
    env = ensureEnvCreated();
    if (VMEnv.redirectPathString == nullptr || env == nullptr) {
        return path;
    }
    return (jstring) env->CallStaticObjectMethod(VMEnv.NativeCoreClass, VMEnv.redirectPathString, path);
}

jobject BoxCore::redirectPathFile(JNIEnv *env, jobject path) {
    env = ensureEnvCreated();
    if (VMEnv.redirectPathFile == nullptr || env == nullptr) {
        return path;
    }
    return env->CallStaticObjectMethod(VMEnv.NativeCoreClass, VMEnv.redirectPathFile, path);
}

jlongArray BoxCore::loadEmptyDex(JNIEnv *env) {
    env = ensureEnvCreated();
    if (VMEnv.loadEmptyDex == nullptr || env == nullptr) {
        return nullptr;
    }
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
    RuntimeHook::init(env);
}

void hideXposed(JNIEnv *env, jclass clazz) {
    ALOGD("set hideXposed");
    VMClassLoaderHook::hideXposed();
}

void init(JNIEnv *env, jobject clazz, jint api_level) {
    if (VMEnv.initialized) {
        ALOGD("NativeCore already initialized");
        return;
    }
    
    ALOGD("NativeCore init on API level: %d", api_level);
    VMEnv.api_level = api_level;
    
    // Safely initialize JniHook once
    try {
        JniHook::InitJniHook(env, api_level);
    } catch (...) {
        LOGE("Failed to initialize JniHook");
    }

    jclass localClass = env->FindClass(VMCORE_CLASS);
    if (localClass == nullptr) {
        env->ExceptionClear();
        LOGE("Failed to find NativeCore class");
        return;
    }
    VMEnv.NativeCoreClass = (jclass) env->NewGlobalRef(localClass);
    
    VMEnv.getCallingUidId = env->GetStaticMethodID(VMEnv.NativeCoreClass, "getCallingUid", "(I)I");
    VMEnv.redirectPathString = env->GetStaticMethodID(VMEnv.NativeCoreClass, "redirectPath","(Ljava/lang/String;)Ljava/lang/String;");
    VMEnv.redirectPathFile = env->GetStaticMethodID(VMEnv.NativeCoreClass, "redirectPath","(Ljava/io/File;)Ljava/io/File;");
    
    VMEnv.initialized = true;
}

// Fixed JNI String Memory Leak
void addIORule(JNIEnv *env, jclass clazz, jstring target_path, jstring relocate_path) {
    ALOGD("set addIORule");
    if (target_path == nullptr || relocate_path == nullptr) return;

    const char *c_target = env->GetStringUTFChars(target_path, nullptr);
    const char *c_relocate = env->GetStringUTFChars(relocate_path, nullptr);

    if (c_target && c_relocate) {
        IO::addRule(c_target, c_relocate);
    }

    if (c_target) env->ReleaseStringUTFChars(target_path, c_target);
    if (c_relocate) env->ReleaseStringUTFChars(relocate_path, c_relocate);
}

void enableIO(JNIEnv *env, jclass clazz) {
    ALOGD("set enableIO");
    IO::init(env);
    nativeHook(env);
}

bool disableHiddenApi(JNIEnv *env, jclass clazz) {
    ALOGD("set disableHiddenApi");
    bool result = disable_hidden_api(env);
    if (!result) {
        ALOGD("set disableHiddenApi Fail!!!");
    } else {
        ALOGD("set disableHiddenApi Success!!!");
    }
    return result;
}

// ========== VALIDATION FUNCTIONS ==========
static bool g_validationCalled = false;
static bool g_validationPassed = false;
static char g_validationError[256] = {0};

static void L1(JNIEnv *env, jclass clazz) {
    g_validationCalled = true;
    LOGI("📞 L1 called");
}

static void L2(JNIEnv *env, jclass clazz, jboolean passed, jstring error) {
    g_validationPassed = passed;
    if (error != nullptr) {
        const char* err = env->GetStringUTFChars(error, nullptr);
        if (err) {
            strncpy(g_validationError, err, sizeof(g_validationError) - 1);
            env->ReleaseStringUTFChars(error, err);
        }
    }
    if (!passed) LOGE("❌ L2 failed: %s", g_validationError);
    else LOGI("✅ L2 passed");
}

static jboolean L3(JNIEnv *env, jclass clazz) {
    if (!g_validationCalled) {
        LOGE("🚨 L1 never called!");
        return JNI_FALSE;
    }
    if (!g_validationPassed) {
        LOGE("🚨 L2 failed: %s", g_validationError);
        return JNI_FALSE;
    }
    LOGI("✅ L3 passed!");
    return JNI_TRUE;
}

// ========== SECCOMP ==========
#define SECMAGIC 0xdeadbeef

#if defined(__aarch64__)
uint64_t OriSyscall(uint64_t num, uint64_t SYSARG_1, uint64_t SYSARG_2, uint64_t SYSARG_3, uint64_t SYSARG_4, uint64_t SYSARG_5, uint64_t SYSARG_6) {
    uint64_t x0;
    __asm__ volatile (
        "mov x8, %1\n\t"
        "mov x0, %2\n\t"
        "mov x1, %3\n\t"
        "mov x2, %4\n\t"
        "mov x3, %5\n\t"
        "mov x4, %6\n\t"
        "mov x5, %7\n\t"
        "svc #0\n\t"
        "mov %0, x0\n\t"
        : "=r"(x0)
        : "r"(num), "r"(SYSARG_1), "r"(SYSARG_2), "r"(SYSARG_3), "r"(SYSARG_4), "r"(SYSARG_5), "r"(SYSARG_6)
        : "x8", "x0", "x1", "x2", "x3", "x4", "x5" // Fixed duplicate "x4"
    );
    return x0;
}
#elif defined(__arm__)
uint32_t OriSyscall(uint32_t num, uint32_t SYSARG_1, uint32_t SYSARG_2, uint32_t SYSARG_3, uint32_t SYSARG_4, uint32_t SYSARG_5, uint32_t SYSARG_6) {
    uint32_t x0;
    __asm__ volatile (
        "mov r7, %1\n\t"
        "mov r0, %2\n\t"
        "mov r1, %3\n\t"
        "mov r2, %4\n\t"
        "mov r3, %5\n\t"
        "mov r4, %6\n\t"
        "mov r5, %7\n\t"
        "svc #0\n\t"
        "mov %0, r0\n\t"
        : "=r"(x0)
        : "r"(num), "r"(SYSARG_1), "r"(SYSARG_2), "r"(SYSARG_3), "r"(SYSARG_4), "r"(SYSARG_5), "r"(SYSARG_6)
        : "r7", "r0", "r1", "r2", "r3", "r4", "r5"
    );
    return x0;
}
#endif

void sig_callback(int signo, siginfo_t *info, void *data) {
    unsigned long syscall_number;
    unsigned long SYSARG_1, SYSARG_2, SYSARG_3, SYSARG_4;

#if defined(__aarch64__)
    ucontext_t *uc = (ucontext_t *) data;
    syscall_number = uc->uc_mcontext.regs[8];
    SYSARG_1 = uc->uc_mcontext.regs[0];
    SYSARG_2 = uc->uc_mcontext.regs[1];
    SYSARG_3 = uc->uc_mcontext.regs[2];
    SYSARG_4 = uc->uc_mcontext.regs[3];
#elif defined(__arm__)
    ucontext_t *uc = (ucontext_t *) data;
    syscall_number = uc->uc_mcontext.arm_r7;
    SYSARG_1 = uc->uc_mcontext.arm_r0;
    SYSARG_2 = uc->uc_mcontext.arm_r1;
    SYSARG_3 = uc->uc_mcontext.arm_r2;
    SYSARG_4 = uc->uc_mcontext.arm_r3;
#endif

    if (syscall_number == __NR_openat) {
        int fd = (int) SYSARG_1;
        const char *pathname = (const char *) SYSARG_2;
        int flags = (int) SYSARG_3;
        int mode = (int) SYSARG_4;

        // Safely check pointer before logging to avoid SIGSEGV inside signal handler
        if (pathname != nullptr && PointerCheck::isValidReadPtr(pathname)) {
            ALOGE("Openat trapped: %s", pathname);
        }

#if defined(__aarch64__)
        uc->uc_mcontext.regs[0] = OriSyscall(__NR_openat, fd, (uint64_t)pathname, flags, mode, SECMAGIC, SECMAGIC);
#elif defined(__arm__)
        uc->uc_mcontext.arm_r0 = OriSyscall(__NR_openat, fd, (uint32_t)pathname, flags, mode, SECMAGIC, SECMAGIC);
#endif
    }
}

void init_seccomp(JNIEnv *env, jclass clazz) {
    struct sock_filter filter[] = {
        BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, nr)),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_openat, 0, 2),
        BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, args[4])),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, SECMAGIC, 0, 1),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_TRAP)
    };

    struct sock_fprog prog;
    prog.filter = filter;
    prog.len = (unsigned short) (sizeof(filter) / sizeof(filter[0]));

    struct sigaction sa;
    sigset_t sigset;
    sigfillset(&sigset);
    sa.sa_sigaction = sig_callback;
    sa.sa_mask = sigset;
    sa.sa_flags = SA_SIGINFO;

    if (sigaction(SIGSYS, &sa, nullptr) == -1) {
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

static JNINativeMethod gMethods[] = {
    {"disableHiddenApi", "()Z", (void *) disableHiddenApi},
    {"init_seccomp",     "()V", (void *) init_seccomp},
    {"hideXposed",       "()V", (void *) hideXposed},
    {"addIORule",        "(Ljava/lang/String;Ljava/lang/String;)V", (void *) addIORule},
    {"enableIO",         "()V", (void *) enableIO},
    {"init",             "(I)V", (void *) init},
};

int registerNativeMethods(JNIEnv *env, const char *className, JNINativeMethod *gMethods, int numMethods) {
    jclass clazz = env->FindClass(className);
    if (clazz == nullptr) {
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, gMethods, numMethods) < 0) {
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

int registerNatives(JNIEnv *env) {
    if (!registerNativeMethods(env, VMCORE_CLASS, gMethods, sizeof(gMethods) / sizeof(gMethods[0]))) {
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

void registerMethod(JNIEnv *jenv) {
    registerNatives(jenv);
}

// ========== JNI_OnLoad - ONLY EXPORTED SYMBOL ==========
JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    VMEnv.vm = vm;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_EVERSION;
    }
    
    registerMethod(env);
    
    // ========== DYNAMIC REGISTRATION (HIDDEN FUNCTION NAMES) ==========
    jclass licenseClass = env->FindClass("com/Score/core/LicenseManager");
    if (licenseClass != nullptr) {
        JNINativeMethod licenseMethods[] = {
            {"A1", "()V", (void *) L1},
            {"A2", "(ZLjava/lang/String;)V", (void *) L2}
        };
        env->RegisterNatives(licenseClass, licenseMethods, 2);
        LOGI("✅ LicenseManager registered (hidden)");
    } else {
        env->ExceptionClear();
        LOGE("❌ Failed to find LicenseManager class!");
    }
    
    jclass bbcClass = env->FindClass("com/Score/ScoreCore");
    if (bbcClass != nullptr) {
        JNINativeMethod bbcMethods[] = {
            {"nativeCheckValidation", "()Z", (void *) L3}
        };
        env->RegisterNatives(bbcClass, bbcMethods, 1);
        LOGI("✅ ScoreCore registered (hidden)");
    } else {
        env->ExceptionClear();
        LOGE("❌ Failed to find ScoreCore class!");
    }
    // =================================================================
    
    return JNI_VERSION_1_6;
}
