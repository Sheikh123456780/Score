//
// Created by efrit on 2026/6/24
// Updated for Android 16 compatibility
//
#include <stdio.h>

#ifndef VIRTUAL_APP_ARTMETHOD_H
#define VIRTUAL_APP_ARTMETHOD_H

#define __ANDROID_API_L__       21
#define __ANDROID_API_L_MR1__   22
#define __ANDROID_API_M__       23
#define __ANDROID_API_N__       24
#define __ANDROID_API_N_MR1__   25
#define __ANDROID_API_O__       26
#define __ANDROID_API_O_MR1__   27
#define __ANDROID_API_P__       28
#define __ANDROID_API_Q__       29
#define __ANDROID_API_R__       30
#define __ANDROID_API_S__       31
#define __ANDROID_API_S_V2__    32
#define __ANDROID_API_Tiramisu__ 33
#define __ANDROID_API_U__       34
#define __ANDROID_API_V__       35
#define __ANDROID_API_16__      36  // Android 16

static constexpr uint32_t kAccPublic =       0x0001;  // class, field, method, ic
static constexpr uint32_t kAccPrivate =      0x0002;  // field, method, ic
static constexpr uint32_t kAccProtected =    0x0004;  // field, method, ic
static constexpr uint32_t kAccStatic =       0x0008;  // field, method, ic
static constexpr uint32_t kAccFinal =        0x0010;  // class, field, method, ic
static constexpr uint32_t kAccSynchronized = 0x0020;  // method (only allowed on natives)
static constexpr uint32_t kAccSuper =        0x0020;  // class (not used in dex)
static constexpr uint32_t kAccVolatile =     0x0040;  // field
static constexpr uint32_t kAccBridge =       0x0040;  // method (1.5)
static constexpr uint32_t kAccTransient =    0x0080;  // field
static constexpr uint32_t kAccVarargs =      0x0080;  // method (1.5)
static constexpr uint32_t kAccNative =       0x0100;  // method
static constexpr uint32_t kAccInterface =    0x0200;  // class, ic
static constexpr uint32_t kAccAbstract =     0x0400;  // class, method, ic
static constexpr uint32_t kAccStrict =       0x0800;  // method
static constexpr uint32_t kAccSynthetic =    0x1000;  // class, field, method, ic
static constexpr uint32_t kAccAnnotation =   0x2000;  // class, ic (1.5)
static constexpr uint32_t kAccEnum =         0x4000;  // class, field, ic (1.5)

// Native method flags
static constexpr uint32_t kAccFastNative =            0x00080000;  // method (runtime; native only)
static constexpr uint32_t kAccCriticalNative =        0x00100000;  // method (runtime; native only)
static constexpr uint32_t kAccNterpInvokeFastPathFlag = 0x00200000;  // method (runtime)

// Visibility flags for hidden API access
static constexpr uint32_t kAccPublicApi =             0x10000000;  // field, method
static constexpr uint32_t kAccCorePlatformApi =       0x20000000;  // field, method

// Additional flags for newer Android versions
static constexpr uint32_t kAccNativeRuntime =         0x00000000;  // Placeholder for runtime-specific flags

#endif //VIRTUAL_APP_ARTMETHOD_H