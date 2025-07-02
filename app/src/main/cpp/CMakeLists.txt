# CMake configuration for native libopus integration

cmake_minimum_required(VERSION 3.10.2)

project("whisperdroid_opus")

# Set C++ standard and make sure we use static libcxx to avoid libc++_shared.so dependency
set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_ANDROID_STL_TYPE c++_static)

# Force all libraries to build as static to avoid shared library dependencies
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build shared libraries" FORCE)

# Path to libopus and libogg sources (assume you will place them in src/main/cpp/opus and src/main/cpp/ogg)
add_subdirectory(opus)
add_subdirectory(ogg)
add_subdirectory(opusfile)

# Add FDK-AAC for native AAC encoding - force static build
add_subdirectory(fdk-aac)

# JNI wrapper
add_library(
    opus_jni
    SHARED
    opus_jni.cpp
)

# Include directories for opus_jni target
target_include_directories(
    opus_jni
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/opus/include
    ${CMAKE_CURRENT_SOURCE_DIR}/ogg/include
    ${CMAKE_CURRENT_SOURCE_DIR}/opusfile/include
    ${CMAKE_CURRENT_SOURCE_DIR}/fdk-aac/libAACenc/include
    ${CMAKE_CURRENT_SOURCE_DIR}/fdk-aac/libSYS/include
    ${CMAKE_CURRENT_SOURCE_DIR}/fdk-aac/libFDK/include
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# Link with libopus and libogg
find_library(
    log-lib
    log
)

target_link_libraries(
    opus_jni
    opus
    ogg
    opusfile
    fdk-aac
    ${log-lib}
)
