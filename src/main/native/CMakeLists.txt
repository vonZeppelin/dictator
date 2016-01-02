cmake_minimum_required(VERSION 2.8 FATAL_ERROR)

project(Dictator C)

find_package(JNI REQUIRED)
if (CMAKE_C_COMPILER_ID MATCHES "GNU|(Apple)?Clang")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall -O3 -msse2 -pipe -ffast-math -funroll-loops -fvisibility=hidden")

    # Ugly way to have config.h on Mac
    if (NOT EXISTS ${CMAKE_SOURCE_DIR}/lame/config.h)
        execute_process(COMMAND ./configure --disable-decoder
            WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}/lame
        )
    endif()
elseif (MSVC)
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} /arch:SSE2 /W4")

    # Ugly way to have config.h on Win
    if (NOT EXISTS ${CMAKE_SOURCE_DIR}/lame/config.h)
        file(COPY ${CMAKE_SOURCE_DIR}/lame/configMS.h
            DESTINATION ${CMAKE_SOURCE_DIR}/lame/config.h
        )
    endif()
else()
    message(FATAL_ERROR "Unknown compiler!")
endif()
if (UNIX)
    set(WEBRTC_DEFS WEBRTC_POSIX)
else()
    set(WEBRTC_DEFS WEBRTC_WIN)
endif()

# Subset of the WebRTC project with VAD module
add_library(webrtc STATIC
    webrtc/webrtc/common_audio/signal_processing/auto_corr_to_refl_coef.c
    webrtc/webrtc/common_audio/signal_processing/auto_correlation.c
    webrtc/webrtc/common_audio/signal_processing/complex_fft.c
    webrtc/webrtc/common_audio/signal_processing/complex_bit_reverse.c
    webrtc/webrtc/common_audio/signal_processing/copy_set_operations.c
    webrtc/webrtc/common_audio/signal_processing/cross_correlation.c
    webrtc/webrtc/common_audio/signal_processing/division_operations.c
    webrtc/webrtc/common_audio/signal_processing/dot_product_with_scale.c
    webrtc/webrtc/common_audio/signal_processing/downsample_fast.c
    webrtc/webrtc/common_audio/signal_processing/energy.c
    webrtc/webrtc/common_audio/signal_processing/filter_ar.c
    webrtc/webrtc/common_audio/signal_processing/filter_ar_fast_q12.c
    webrtc/webrtc/common_audio/signal_processing/filter_ma_fast_q12.c
    webrtc/webrtc/common_audio/signal_processing/get_hanning_window.c
    webrtc/webrtc/common_audio/signal_processing/get_scaling_square.c
    webrtc/webrtc/common_audio/signal_processing/ilbc_specific_functions.c
    webrtc/webrtc/common_audio/signal_processing/levinson_durbin.c
    webrtc/webrtc/common_audio/signal_processing/lpc_to_refl_coef.c
    webrtc/webrtc/common_audio/signal_processing/min_max_operations.c
    webrtc/webrtc/common_audio/signal_processing/randomization_functions.c
    webrtc/webrtc/common_audio/signal_processing/refl_coef_to_lpc.c
    webrtc/webrtc/common_audio/signal_processing/real_fft.c
    webrtc/webrtc/common_audio/signal_processing/resample.c
    webrtc/webrtc/common_audio/signal_processing/resample_48khz.c
    webrtc/webrtc/common_audio/signal_processing/resample_by_2.c
    webrtc/webrtc/common_audio/signal_processing/resample_by_2_internal.c
    webrtc/webrtc/common_audio/signal_processing/resample_fractional.c
    webrtc/webrtc/common_audio/signal_processing/spl_init.c
    webrtc/webrtc/common_audio/signal_processing/spl_sqrt.c
    webrtc/webrtc/common_audio/signal_processing/spl_sqrt_floor.c
    webrtc/webrtc/common_audio/signal_processing/splitting_filter.c
    webrtc/webrtc/common_audio/signal_processing/sqrt_of_one_minus_x_squared.c
    webrtc/webrtc/common_audio/signal_processing/vector_scaling_operations.c
    webrtc/webrtc/common_audio/vad/webrtc_vad.c
    webrtc/webrtc/common_audio/vad/vad_core.c
    webrtc/webrtc/common_audio/vad/vad_filterbank.c
    webrtc/webrtc/common_audio/vad/vad_gmm.c
    webrtc/webrtc/common_audio/vad/vad_sp.c
)
target_include_directories(webrtc
    PRIVATE webrtc
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
    PRIVATE lame lame/include lame/libmp3lame lame/mpglib
)
target_compile_definitions(lame
    PRIVATE HAVE_CONFIG_H
)

add_library(dictator SHARED
    audio.c
)
target_include_directories(dictator
    PRIVATE webrtc lame/include ${JNI_INCLUDE_DIRS}
)
target_link_libraries(dictator webrtc lame)
