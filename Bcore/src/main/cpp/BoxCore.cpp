//
// Created by Milk on 4/9/21.
// Updated for Android 16 (SDK 36) compatibility.
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
#include "ArtMethod.h"
#include <cstring>
#include <unistd.h>
#include <sys/mman.h>

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
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

// ========== VALIDATION TRACKER ==========
static bool g_validationCalled = false;
static bool g_validationPassed = false;
static char g_validationError[256] = {0};
// =======================================

// ========== ART METHOD FLAGS OFFSET (discovered at runtime) ==========
static uint32_t g_art_method_flags_offset = 0;
// =====================================================================

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

// =====================================================================
// ART offset discovery (Android 16 compatible)
//
// The `ArtMethod` struct layout changed again in Android 16. The
// `access_flags_` field offset is not stable across ART versions.
// This function probes for it by looking at a known method
// (java.lang.Object.hashCode) whose flags we can predict.
//
// Returns the byte offset, or 0 if the probe failed.
// =====================================================================
namespace art_probe {

    // Known flags for java.lang.Object.hashCode():
    //   public (0x0001) | native (0x0100) = 0x0101
    // Some builds also add final/synthetic, so we accept a small mask.
    static bool looks_like_flags(uint32_t value) {
        // Accept values that contain the PUBLIC bit and NATIVE bit.
        if ((value & kAccPublic) == 0) return false;
        if ((value & kAccNative) == 0) return false;
        // Reject values that have bits set outside the known flag range.
        const uint32_t known_mask =
                kAccPublic | kAccPrivate | kAccProtected | kAccStatic |
                kAccFinal | kAccSynchronized | kAccVolatile | kAccBridge |
                kAccTransient | kAccVarargs | kAccNative | kAccInterface |
                kAccAbstract | kAccStrict | kAccSynthetic | kAccAnnotation |
                kAccEnum | kAccFastNative | kAccCriticalNative |
                kAccNterpInvokeFastPathFlag | kAccPublicApi |
                kAccCorePlatformApi;
        if ((value & ~known_mask) != 0) return false;
        return true;
    }

    uint32_t discover_art_method_flags_offset() {
        JNIEnv *env = ensureEnvCreated();
        if (env == nullptr) {
            LOGE("art_probe: no JNIEnv");
            return 0;
        }

        // Get java.lang.Object.hashCode as a known ArtMethod pointer.
        jclass objectClass = env->FindClass("java/lang/Object");
        if (objectClass == nullptr) {
            LOGE("art_probe: java/lang/Object not found");
            return 0;
        }

        jmethodID hashCodeId = env->GetMethodID(objectClass, "hashCode", "()I");
        if (hashCodeId == nullptr) {
            LOGE("art_probe: hashCode not found");
            env->DeleteLocalRef(objectClass);
            return 0;
        }

        // jmethodID IS an ArtMethod* on Android.
        uintptr_t method_ptr = reinterpret_cast<uintptr_t>(hashCodeId);
        if (method_ptr == 0) {
            LOGE("art_probe: hashCodeId is null");
            env->DeleteLocalRef(objectClass);
            return 0;
        }

        // Scan the first 128 bytes of the ArtMethod for a 32-bit value
        // whose bits look like the access flags of Object.hashCode.
        const uint32_t scan_limit = 128;
        const uint32_t* words = reinterpret_cast<const uint32_t*>(method_ptr);

        for (uint32_t offset = 0; offset < scan_limit; offset += sizeof(uint32_t)) {
            uint32_t candidate = words[offset / sizeof(uint32_t)];
            if (looks_like_flags(candidate)) {
                LOGI("art_probe: candidate flags 0x%x at offset 0x%x",
                     candidate, offset);
                // Additional sanity: require the NATIVE bit set.
                if ((candidate & kAccNative) != 0) {
                    env->DeleteLocalRef(objectClass);
                    return offset;
                }
            }
        }

        LOGW("art_probe: no matching offset found in first %u bytes", scan_limit);
        env->DeleteLocalRef(objectClass);
        return 0;
    }

} // namespace art_probe

// =====================================================================
// Native hook installation
// =====================================================================
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

// =====================================================================
// init() — now probes for the ART offset before installing hooks
// =====================================================================
void init(JNIEnv *env, jobject clazz, jint api_level) {
    ALOGD("NativeCore init. api_level=%d", api_level);

    VMEnv.api_level = api_level;

    VMEnv.NativeCoreClass = (jclass) env->NewGlobalRef(env->FindClass(VMCORE_CLASS));
    if (VMEnv.NativeCoreClass == nullptr) {
        LOGE("❌ NativeCore class not found!");
        return;
    }

    VMEnv.getCallingUidId = env->GetStaticMethodID(
            VMEnv.NativeCoreClass, "getCallingUid", "(I)I");
    VMEnv.redirectPathString = env->GetStaticMethodID(
            VMEnv.NativeCoreClass, "redirectPath", "(Ljava/lang/String;)Ljava/lang/String;");
    VMEnv.redirectPathFile = env->GetStaticMethodID(
            VMEnv.NativeCoreClass, "redirectPath", "(Ljava/io/File;)Ljava/io/File;");

    if (VMEnv.getCallingUidId == nullptr ||
        VMEnv.redirectPathString == nullptr ||
        VMEnv.redirectPathFile == nullptr) {
        LOGE("❌ Failed to resolve NativeCore methods!");
        return;
    }

    // ------------------------------------------------------------------
    // Android 16 fix: dynamically discover the ART access_flags_ offset.
    // ------------------------------------------------------------------
    g_art_method_flags_offset = art_probe::discover_art_method_flags_offset();

    if (g_art_method_flags_offset == 0) {
        // Fallback: this is the common value on Android 14/15/16 AOSP
        // and most Samsung builds. If it is wrong on your firmware the
        // JNI hooks will fail safely below.
        g_art_method_flags_offset = 0x04;
        LOGW("⚠️ ART probe failed, falling back to offset 0x%x",
             g_art_method_flags_offset);
    } else {
        LOGI("✅ ART access_flags_ offset discovered: 0x%x",
             g_art_method_flags_offset);
    }

    // Pass the discovered offset to the JNI hook layer.
    // (You must update JniHook::InitJniHook to accept this parameter —
    //  see the accompanying JniHook.h / JniHook.cpp patch.)
    JniHook::InitJniHook(env, api_level, g_art_method_flags_offset);
}

void addIORule(JNIEnv *env, jclass clazz, jstring target_path, jstring relocate_path) {
    ALOGD("set addIORule");
    if (target_path == nullptr || relocate_path == nullptr) return;
    const char *target = env->GetStringUTFChars(target_path, nullptr);
    const char *relocate = env->GetStringUTFChars(relocate_path, nullptr);
    if (target == nullptr || relocate == nullptr) {
        if (target != nullptr) env->ReleaseStringUTFChars(target_path, target);
        if (relocate != nullptr) env->ReleaseStringUTFChars(relocate_path, relocate);
        return;
    }
    IO::addRule(target, relocate);
    env->ReleaseStringUTFChars(target_path, target);
    env->ReleaseStringUTFChars(relocate_path, relocate);
}

void enableIO(JNIEnv *env, jclass clazz) {
    ALOGD("set enableIO");
    IO::init(env);
    nativeHook(env);
}

bool disableHiddenApi(JNIEnv *env, jclass clazz) {
    ALOGD("set disableHiddenApi");
    if (!disable_hidden_api(env)) {
        ALOGD("set disableHiddenApi Fail!!!");
        return false;
    }
    return true;
}

// ========== HIDDEN STATIC FUNCTIONS (NOT EXPORTED) ==========
static void L1(JNIEnv *env, jclass clazz) {
    g_validationCalled = true;
    LOGI("📞 L1 called");
}

static void L2(JNIEnv *env, jclass clazz, jboolean passed, jstring error) {
    g_validationPassed = passed;
    const char *err = env->GetStringUTFChars(error, nullptr);
    strncpy(g_validationError, err, sizeof(g_validationError) - 1);
    env->ReleaseStringUTFChars(error, err);
    if (!passed) LOGE("❌ L2 failed: %s", g_validationError);
    else LOGI("✅ L2 passed");
}

static jboolean L3(JNIEnv *env, jclass clazz) {
    if (!g_validationCalled) {
        LOGE("🚨 L1 never called! (Java code removed)");
        sleep(2);
        *(volatile int *) 0 = 0;
        return JNI_FALSE;
    }
    if (!g_validationPassed) {
        LOGE("🚨 L2 failed: %s", g_validationError);
        sleep(2);
        *(volatile int *) 0 = 0;
        return JNI_FALSE;
    }
    LOGI("✅ L3 passed!");
    return JNI_TRUE;
}
// ==========================================================

// =====================================================================
// seccomp / syscall interception (unchanged)
// =====================================================================
#define SECMAGIC 0xdeadbeef

#if defined(__aarch64__)
uint64_t OriSyscall(uint64_t num, uint64_t SYSARG_1, uint64_t SYSARG_2,
                    uint64_t SYSARG_3, uint64_t SYSARG_4,
                    uint64_t SYSARG_5, uint64_t SYSARG_6) {
    uint64_t x0;
    __asm__ volatile(
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
            : "r"(num), "r"(SYSARG_1), "r"(SYSARG_2), "r"(SYSARG_3),
              "r"(SYSARG_4), "r"(SYSARG_5), "r"(SYSARG_6)
            : "x8", "x0", "x1", "x2", "x3", "x4", "x5");
    return x0;
}
#elif defined(__arm__)
uint32_t OriSyscall(uint32_t num, uint32_t SYSARG_1, uint32_t SYSARG_2,
                    uint32_t SYSARG_3, uint32_t SYSARG_4,
                    uint32_t SYSARG_5, uint32_t SYSARG_6) {
    uint32_t x0;
    __asm__ volatile(
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
            : "r"(num), "r"(SYSARG_1), "r"(SYSARG_2), "r"(SYSARG_3),
              "r"(SYSARG_4), "r"(SYSARG_5), "r"(SYSARG_6)
            : "r7", "r0", "r1", "r2", "r3", "r4", "r5");
    return x0;
}
#endif

void sig_callback(int signo, siginfo_t *info, void *data) {
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
#endif

    switch (syscall_number) {
        case __NR_openat: {
            int fd = (int) SYSARG_1;
            const char *pathname = (const char *) SYSARG_2;
            int flags = (int) SYSARG_3;
            int mode = (int) SYSARG_4;
            ALOGE("测试%s", pathname);
#if defined(__aarch64__)
            ((ucontext_t *) data)->uc_mcontext.regs[0] = (uint64_t) fd;
            ((ucontext_t *) data)->uc_mcontext.regs[1] = (uint64_t) pathname;
            ((ucontext_t *) data)->uc_mcontext.regs[2] = (uint64_t) flags;
            ((ucontext_t *) data)->uc_mcontext.regs[3] = (uint64_t) mode;
            ((ucontext_t *) data)->uc_mcontext.regs[0] = OriSyscall(
                    __NR_openat, fd, (uint64_t) pathname, flags, mode,
                    SECMAGIC, SECMAGIC);
#elif defined(__arm__)
            ((ucontext_t *) data)->uc_mcontext.arm_r0 = (uint32_t) fd;
            ((ucontext_t *) data)->uc_mcontext.arm_r1 = (uint32_t) pathname;
            ((ucontext_t *) data)->uc_mcontext.arm_r2 = (uint32_t) flags;
            ((ucontext_t *) data)->uc_mcontext.arm_r3 = (uint32_t) mode;
            ((ucontext_t *) data)->uc_mcontext.arm_r0 = OriSyscall(
                    __NR_openat, fd, (uint32_t) pathname, flags, mode,
                    SECMAGIC, SECMAGIC);
#endif
            break;
        }
        default:
            break;
    }
}

void init_seccomp(JNIEnv *env, jclass clazz) {
    struct sock_filter filter[] = {
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS,
                     offsetof(struct seccomp_data, nr)),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_openat, 0, 2),
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS,
                     offsetof(struct seccomp_data, args[4])),
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

// =====================================================================
// JNI native method table
// =====================================================================
static JNINativeMethod gMethods[] = {
        {"disableHiddenApi", "()Z",                                  (void *) disableHiddenApi},
        {"init_seccomp",     "()V",                                  (void *) init_seccomp},
        {"hideXposed",       "()V",                                  (void *) hideXposed},
        {"addIORule",        "(Ljava/lang/String;Ljava/lang/String;)V", (void *) addIORule},
        {"enableIO",         "()V",                                  (void *) enableIO},
        {"init",             "(I)V",                                 (void *) init},
};

int registerNativeMethods(JNIEnv *env, const char *className,
                          JNINativeMethod *methods, int numMethods) {
    jclass clazz;
    clazz = env->FindClass(className);
    if (clazz == nullptr) {
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, methods, numMethods) < 0) {
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

int registerNatives(JNIEnv *env) {
    if (!registerNativeMethods(env, VMCORE_CLASS, gMethods,
                               sizeof(gMethods) / sizeof(gMethods[0])))
        return JNI_FALSE;
    return JNI_TRUE;
}

void registerMethod(JNIEnv *jenv) {
    registerNatives(jenv);
}

// =====================================================================
// JNI_OnLoad — only exported symbol
// =====================================================================
JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    VMEnv.vm = vm;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_EVERSION;
    }
    registerMethod(env);

    // -------- LicenseManager (hidden names) --------
    jclass licenseClass = env->FindClass("com/Score/core/LicenseManager");
    if (licenseClass != nullptr) {
        JNINativeMethod licenseMethods[] = {
                {"A1", "()V",                       (void *) L1},
                {"A2", "(ZLjava/lang/String;)V",    (void *) L2}
        };
        env->RegisterNatives(licenseClass, licenseMethods, 2);
        LOGI("✅ LicenseManager registered (hidden)");
    } else {
        LOGE("❌ Failed to find LicenseManager class!");
    }

    // -------- ScoreCore (nativeCheckValidation) --------
    jclass bbcClass = env->FindClass("com/Score/ScoreCore");
    if (bbcClass != nullptr) {
        JNINativeMethod bbcMethods[] = {
                {"nativeCheckValidation", "()Z", (void *) L3}
        };
        env->RegisterNatives(bbcClass, bbcMethods, 1);
        LOGI("✅ ScoreCore registered (hidden)");
    } else {
        LOGE("❌ Failed to find ScoreCore class!");
    }

    return JNI_VERSION_1_6;
}
