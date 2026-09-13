cmake_minimum_required(VERSION 3.22.1)
project(apktool_zipalign LANGUAGES C CXX)

add_definitions(-DHAVE_PTHREADS=1)
add_definitions(-DHAVE_OFF64_T=1)
add_definitions(-DOS_PATH_SEPARATOR=47)
add_definitions(-DHAVE_SYS_SOCKET_H=1)
add_definitions(-DANDROID_SMP=1)

add_library(zipalign SHARED
    ZipAlign.cpp
    zipalign/ZipAlign.cpp
    zipalign/ZipEntry.cpp
    zipalign/ZipFile.cpp
    android/native/src/utils/SharedBuffer.cpp
    android/native/src/utils/ZipUtils.cpp
    android/native/src/utils/VectorImpl.cpp)

target_include_directories(zipalign BEFORE PUBLIC
    zipalign/include
    android/base/include
    android/core/include
    android/native/include)

target_compile_features(zipalign PRIVATE cxx_std_17)
target_compile_options(zipalign PRIVATE -O2 -fPIC)

# Keep the JNI library loadable on Android devices using 4 KiB or 16 KiB pages.
# NDK r29 already supports flexible page sizes; explicit flags keep the intent
# stable if toolchain defaults change.
target_link_options(zipalign PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384")

target_link_libraries(zipalign log z)
