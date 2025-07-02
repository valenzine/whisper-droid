# Minimal CMakeLists.txt for libopus (static library)
cmake_minimum_required(VERSION 3.10.2)
project(opus)

file(GLOB OPUS_SOURCES
    src/*.c
    celt/*.c
    silk/*.c
    silk/float/*.c
)

add_library(opus STATIC ${OPUS_SOURCES})


target_include_directories(opus PUBLIC include celt silk silk/float)

# Fix for Android/NDK: select temporary allocation mode
target_compile_definitions(opus PRIVATE NONTHREADSAFE_PSEUDOSTACK OPUS_CUSTOM_NOSTATIC=static OPUS_BUILD)
