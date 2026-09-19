//
// Created by Milk on 3/7/21.
// Updated for Android 16 (SDK 36) compatibility.
//
#include <stdio.h>
#include <cstdint>

#ifndef ARTHOOK_ART_METHOD_H
#define ARTHOOK_ART_METHOD_H

#define __ANDROID_API_R__ 30
#define __ANDROID_API_Q__ 29
#define __ANDROID_API_P__ 28
#define __ANDROID_API_S__ 31
#define __ANDROID_API_S2__ 32
#define __ANDROID_API_T__ 33
#define __ANDROID_API_U__ 34
#define __ANDROID_API_V__ 35
#define __ANDROID_API_V2__ 36   // Android 16
#define __ANDROID_API_W__ 37

static constexpr uint32_t kAccPublic =       0x0001;
static constexpr uint32_t kAccPrivate =      0x0002;
static constexpr uint32_t kAccProtected =    0x0004;
static constexpr uint32_t kAccStatic =       0x0008;
static constexpr uint32_t kAccFinal =        0x0010;
static constexpr uint32_t kAccSynchronized = 0x0020;
static constexpr uint32_t kAccSuper =        0x0020;
static constexpr uint32_t kAccVolatile =     0x0040;
static constexpr uint32_t kAccBridge =       0x0040;
static constexpr uint32_t kAccTransient =    0x0080;
static constexpr uint32_t kAccVarargs =      0x0080;
static constexpr uint32_t kAccNative =       0x0100;
static constexpr uint32_t kAccInterface =    0x0200;
static constexpr uint32_t kAccAbstract =     0x0400;
static constexpr uint32_t kAccStrict =       0x0800;
static constexpr uint32_t kAccSynthetic =    0x1000;
static constexpr uint32_t kAccAnnotation =   0x2000;
static constexpr uint32_t kAccEnum =         0x4000;

static constexpr uint32_t kAccPublicApi =             0x10000000;
static constexpr uint32_t kAccCorePlatformApi =       0x20000000;

static constexpr uint32_t kAccFastNative =            0x00080000;
static constexpr uint32_t kAccCriticalNative =        0x00100000;
static constexpr uint32_t kAccNterpInvokeFastPathFlag = 0x00200000;

// =====================================================================
// Android 16 (SDK 36) ART layout notes:
//
// The `ArtMethod` struct layout changed again in Android 16. The
// `access_flags_` field offset is NOT stable across ART versions and
// must be discovered at runtime.
//
// The implementation lives in BoxCore.cpp, where the JNI environment
// is available. This header only declares it, so other translation
// units (JniHook.cpp, etc.) can call it without causing a duplicate
// definition error.
// =====================================================================
namespace art_probe {
    // Returns the byte offset of `access_flags_` inside ArtMethod,
    // or 0 if discovery failed.
    uint32_t discover_art_method_flags_offset();
} // namespace art_probe

#endif // ARTHOOK_ART_METHOD_H
