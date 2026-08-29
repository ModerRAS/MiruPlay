cmake_minimum_required(VERSION 3.22.1)

project(miruplay_dsp LANGUAGES CXX)

add_library(miruplay_dsp SHARED
    miruplay_dsp.cpp
    fir_neon.cpp
    fft_neon.cpp
)

target_compile_features(miruplay_dsp PRIVATE cxx_std_17)

# Common warnings
target_compile_options(miruplay_dsp PRIVATE
    -Wall
    -Wextra
    -Werror=return-type
    -O3
    -ffast-math
    -fno-exceptions
)

# ABI-specific NEON flags
if(ANDROID_ABI STREQUAL "armeabi-v7a")
    # NEON is optional on armv7, enable with softfp
    target_compile_options(miruplay_dsp PRIVATE
        -march=armv7-a
        -mfloat-abi=softfp
        -mfpu=neon
    )
    target_compile_definitions(miruplay_dsp PRIVATE MIRU_DSP_ARMV7_NEON=1)
elseif(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_options(miruplay_dsp PRIVATE
        -march=armv8-a
    )
    target_compile_definitions(miruplay_dsp PRIVATE MIRU_DSP_ARM64_NEON=1)
endif()

find_library(android-lib android REQUIRED)
find_library(log-lib log REQUIRED)
find_library(dl-lib dl REQUIRED)

target_link_libraries(miruplay_dsp PRIVATE
    ${android-lib}
    ${log-lib}
    ${dl-lib}
)

target_link_options(miruplay_dsp PRIVATE
    "-Wl,-z,max-page-size=16384"
)
