# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.
cmake_minimum_required(VERSION 3.4.1)

# ========== HIDE ALL SYMBOLS ==========
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fvisibility=hidden")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden")

# Release flags - aggressive stripping
set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -fvisibility=hidden -ffunction-sections -fdata-sections")
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -fvisibility=hidden -ffunction-sections -fdata-sections")
set(CMAKE_SHARED_LINKER_FLAGS_RELEASE "${CMAKE_SHARED_LINKER_FLAGS_RELEASE} -Wl,--gc-sections -Wl,--strip-all -Wl,--exclude-libs,ALL")
# ======================================

include_directories(JniHook)
include_directories(./)

aux_source_directory(./ SRC1)
aux_source_directory(Utils SRC2)
aux_source_directory(Hook SRC3)
aux_source_directory(JniHook SRC4)
add_compile_options(-w)

add_library( # Sets the name of the library.
        shayk
        # Sets the library as a shared library.
        SHARED
        # Provides a relative path to your source file(s).
        BoxCore.cpp
        hidden_api.cpp
        SandHook/ElfImg.cpp
        Hook/DexFileHook.cpp
        ${SRC1}
        ${SRC2}
        ${SRC3}
        ${SRC4}
        )

find_library( # Sets the name of the path variable.
        log-lib
        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

target_link_libraries( # Specifies the target library.
        shayk
        # Links the target library to the log library
        # included in the NDK.
        ${log-lib}
        z
        )
