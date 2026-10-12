cmake_minimum_required(VERSION 3.22.1)
project(halone_timing CXX)

add_library(halone_timing SHARED frame_timestamps.cpp)
target_link_libraries(halone_timing EGL)
