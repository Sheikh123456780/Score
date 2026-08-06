# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# ========== PROJECT DEFINITION ==========
cmake_minimum_required(VERSION 3.10.2)
project(shayk)  # Required to suppress warnings

# ========== HIDE ALL SYMBOLS ==========
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fvisibility=hidden")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden -std=c++17")

# Release flags - aggressive stripping
set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -fvisibility=hidden -ffunction-sections -fdata-sections")
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -fvisibility=hidden -ffunction-sections -fdata-sections -std=c++17")
set(CMAKE_SHARED_LINKER_FLAGS_RELEASE "${CMAKE_SHARED_LINKER_FLAGS_RELEASE} -Wl,--gc-sections -Wl,--strip-all -Wl,--exclude-libs,ALL")
# ======================================

# ========== INCLUDE DIRECTORIES ==========
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/JniHook
    ${CMAKE_CURRENT_SOURCE_DIR}/Hook
    ${CMAKE_CURRENT_SOURCE_DIR}/Utils
    ${CMAKE_CURRENT_SOURCE_DIR}/SandHook
)

# ========== SUPPRESS ALL WARNINGS ==========
add_compile_options(-w)

# ========== SOURCE FILES ==========
set(SOURCES
    BoxCore.cpp
    hidden_api.cpp
    IO.cpp
    Utils/HexDump.cpp
    Utils/PointerCheck.cpp
    Utils/fake_dlfcn.cpp
    Hook/BaseHook.cpp
    Hook/BinderHook.cpp
    Hook/DexFileHook.cpp
    Hook/RuntimeHook.cpp
    Hook/UnixFileSystemHook.cpp
    Hook/VMClassLoaderHook.cpp
    JniHook/JniHook.cpp
    SandHook/ElfImg.cpp
)

# Check if SandHook/ElfImg.cpp exists before adding
if(NOT EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/SandHook/ElfImg.cpp)
    # Remove it from sources if it doesn't exist
    list(REMOVE_ITEM SOURCES SandHook/ElfImg.cpp)
endif()

# ========== BUILD LIBRARY ==========
add_library(
    shayk
    SHARED
    ${SOURCES}
)

# ========== FIND & LINK LIBRARIES ==========
find_library(log-lib log)
find_library(z-lib z)

target_link_libraries(
    shayk
    ${log-lib}
    ${z-lib}
    dl
    android
)

# ========== ANDROID 9+ COMPATIBILITY ==========
if(ANDROID_PLATFORM VERSION_LESS "28")
    set_target_properties(shayk PROPERTIES CXX_STANDARD 14)
endif()
