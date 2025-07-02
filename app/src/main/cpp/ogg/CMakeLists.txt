# Minimal CMakeLists.txt for libogg (static library)
cmake_minimum_required(VERSION 3.10.2)
project(ogg)

file(GLOB OGG_SOURCES
    src/*.c
)

add_library(ogg STATIC ${OGG_SOURCES})

target_include_directories(ogg PUBLIC include)
