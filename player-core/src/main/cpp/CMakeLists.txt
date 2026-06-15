cmake_minimum_required(VERSION 3.22.1)
project(miruplay_vlcbridge)

add_library(
    miruplay_vlcbridge
    SHARED
    libvlc_snapshot_bridge.cpp
    libvlc_frame_probe_bridge.cpp
    libvlc_output_callbacks_bridge.cpp
    libvlc_vmem_stream_bridge.cpp
)

find_library(log-lib log)
find_library(dl-lib dl)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(glesv2-lib GLESv2)

target_link_libraries(
    miruplay_vlcbridge
    ${log-lib}
    ${dl-lib}
    ${android-lib}
    ${egl-lib}
    ${glesv2-lib}
)
