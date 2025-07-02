cmake_minimum_required(VERSION 3.10)
project(opusfile)

add_library(opusfile STATIC
    opusfile.c
    info.c
)

target_include_directories(opusfile PUBLIC 
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/../opus/include
)

target_link_libraries(opusfile PUBLIC opus ogg)
