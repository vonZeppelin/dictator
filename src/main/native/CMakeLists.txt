cmake_minimum_required(VERSION 2.8 FATAL_ERROR)

project(Dictator)

find_package(Java REQUIRED)
find_package(JNI REQUIRED)
include(UseJava)

if (UNIX)
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall -Wpedantic -flto -fvisibility=hidden")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")
    set(WEBRTC_DEFS WEBRTC_POSIX)

    # Ugly way to have LAME config.h
    if (NOT EXISTS ${CMAKE_SOURCE_DIR}/lame/config.h)
        execute_process(COMMAND ./configure --disable-decoder
            WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}/lame
        )
    endif()
    if (APPLE)
        find_library(JNF_LIB JavaNativeFoundation PATH_SUFFIXES JavaVM.framework/Frameworks)
        find_library(COCOA_LIB Cocoa)
        set(EXTRA_LIBS ${JNF_LIB} ${COCOA_LIB})
        set(DICTATOR_SRC ui_macos.m)
        # set(CMAKE_OSX_DEPLOYMENT_TARGET 10.7)
    endif()
elseif (MSVC)
    # Is 32-bit platform?
    if (CMAKE_SIZEOF_VOID_P EQUAL 4)
        set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} /arch:SSE2")
    endif()
    set(WEBRTC_DEFS WEBRTC_WIN)

    # Ugly way to have LAME config.h
    if (NOT EXISTS ${CMAKE_SOURCE_DIR}/lame/config.h)
        configure_file(${CMAKE_SOURCE_DIR}/lame/configMS.h ${CMAKE_SOURCE_DIR}/lame/config.h
            COPYONLY
        )
    endif()
else()
    message(FATAL_ERROR "Unsupported target platform")
endif()

create_javah(TARGET jni_headers
    CLASSES dictator.Native
    CLASSPATH ${CMAKE_CURRENT_SOURCE_DIR}/../java
)

# Subset of the WebRTC project with VAD module
add_library(webrtc STATIC
    webrtc/common_audio/signal_processing/division_operations.c
    webrtc/common_audio/signal_processing/energy.c
    webrtc/common_audio/signal_processing/get_scaling_square.c
    webrtc/common_audio/signal_processing/resample_48khz.c
    webrtc/common_audio/signal_processing/resample_by_2_internal.c
    webrtc/common_audio/signal_processing/resample_fractional.c
    webrtc/common_audio/signal_processing/spl_init.c
    webrtc/common_audio/vad/vad_core.c
    webrtc/common_audio/vad/vad_filterbank.c
    webrtc/common_audio/vad/vad_gmm.c
    webrtc/common_audio/vad/vad_sp.c
    webrtc/common_audio/vad/webrtc_vad.c
    webrtc/rtc_base/checks.cc
)
target_include_directories(webrtc
    PUBLIC webrtc
)
target_compile_definitions(webrtc
    PRIVATE ${WEBRTC_DEFS}
)

add_library(lame STATIC
    lame/libmp3lame/vector/xmm_quantize_sub.c
    lame/libmp3lame/VbrTag.c
    lame/libmp3lame/bitstream.c
    lame/libmp3lame/encoder.c
    lame/libmp3lame/fft.c
    lame/libmp3lame/gain_analysis.c
    lame/libmp3lame/id3tag.c
    lame/libmp3lame/lame.c
    lame/libmp3lame/mpglib_interface.c
    lame/libmp3lame/newmdct.c
    lame/libmp3lame/presets.c
    lame/libmp3lame/psymodel.c
    lame/libmp3lame/quantize.c
    lame/libmp3lame/quantize_pvt.c
    lame/libmp3lame/reservoir.c
    lame/libmp3lame/set_get.c
    lame/libmp3lame/tables.c
    lame/libmp3lame/takehiro.c
    lame/libmp3lame/util.c
    lame/libmp3lame/vbrquantize.c
    lame/libmp3lame/version.c
)
target_include_directories(lame
    PUBLIC lame/include
    PRIVATE lame lame/libmp3lame lame/mpglib
)
target_compile_definitions(lame
    PRIVATE HAVE_CONFIG_H _CRT_SECURE_NO_WARNINGS
)

add_library(dictator SHARED
    audio.c
    ${DICTATOR_SRC}
)
target_include_directories(dictator
    PRIVATE ${JNI_INCLUDE_DIRS} ${CMAKE_CURRENT_BINARY_DIR} ${EXTRA_LIB}
)
target_link_libraries(dictator
    PRIVATE webrtc lame ${EXTRA_LIBS}
)
add_dependencies(dictator jni_headers)
