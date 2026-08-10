cmake_minimum_required(VERSION 3.22.1)

project(miruplay_libass LANGUAGES CXX)

add_library(miruplay_libass SHARED
    miruplay_libass.cpp
)

target_compile_features(miruplay_libass PRIVATE cxx_std_17)
target_compile_options(miruplay_libass PRIVATE
    -Wall
    -Wextra
)

find_library(android-lib android REQUIRED)
find_library(log-lib log REQUIRED)
find_library(dl-lib dl REQUIRED)

target_link_libraries(miruplay_libass PRIVATE
    ${android-lib}
    ${log-lib}
    ${dl-lib}
)

target_link_options(miruplay_libass PRIVATE
    "-Wl,-z,max-page-size=16384"
)
