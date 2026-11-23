cmake_minimum_required(VERSION 3.22.1)
project("yinterm_pty")

add_library(
    yinterm_pty
    SHARED
    yinterm_pty.cpp
)

target_compile_options(
    yinterm_pty
    PRIVATE
    -O3
    -Wall
    -Wextra
)

target_link_libraries(
    yinterm_pty
    log
)