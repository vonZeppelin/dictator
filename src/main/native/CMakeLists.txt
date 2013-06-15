cmake_minimum_required(VERSION 2.8 FATAL_ERROR)

project("Dictator" C)

find_package(JNI REQUIRED)
if (CMAKE_C_COMPILER_ID MATCHES "GNU|(Apple)?Clang")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -msse2 -Wall -fvisibility=hidden")
elseif (MSVC)
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} /arch:SSE2 /W4")
else()
    message(FATAL_ERROR "Unknown compiler!")
endif()
if (UNIX)
    set(WEBRTC_DEFS "WEBRTC_POSIX")
else()
    set(WEBRTC_DEFS "WEBRTC_WIN")
endif()

add_library(webrtc STATIC
    "webrtc/typedefs.h"
    "webrtc/common_audio/signal_processing/include/real_fft.h"
    "webrtc/common_audio/signal_processing/include/signal_processing_library.h"
    "webrtc/common_audio/signal_processing/include/spl_inl.h"
    "webrtc/common_audio/signal_processing/auto_corr_to_refl_coef.c"
    "webrtc/common_audio/signal_processing/auto_correlation.c"
    "webrtc/common_audio/signal_processing/complex_fft.c"
    "webrtc/common_audio/signal_processing/complex_fft_tables.h"
    "webrtc/common_audio/signal_processing/complex_bit_reverse.c"
    "webrtc/common_audio/signal_processing/copy_set_operations.c"
    "webrtc/common_audio/signal_processing/cross_correlation.c"
    "webrtc/common_audio/signal_processing/division_operations.c"
    "webrtc/common_audio/signal_processing/dot_product_with_scale.c"
    "webrtc/common_audio/signal_processing/downsample_fast.c"
    "webrtc/common_audio/signal_processing/energy.c"
    "webrtc/common_audio/signal_processing/filter_ar.c"
    "webrtc/common_audio/signal_processing/filter_ar_fast_q12.c"
    "webrtc/common_audio/signal_processing/filter_ma_fast_q12.c"
    "webrtc/common_audio/signal_processing/get_hanning_window.c"
    "webrtc/common_audio/signal_processing/get_scaling_square.c"
    "webrtc/common_audio/signal_processing/ilbc_specific_functions.c"
    "webrtc/common_audio/signal_processing/levinson_durbin.c"
    "webrtc/common_audio/signal_processing/lpc_to_refl_coef.c"
    "webrtc/common_audio/signal_processing/min_max_operations.c"
    "webrtc/common_audio/signal_processing/randomization_functions.c"
    "webrtc/common_audio/signal_processing/refl_coef_to_lpc.c"
    "webrtc/common_audio/signal_processing/real_fft.c"
    "webrtc/common_audio/signal_processing/resample.c"
    "webrtc/common_audio/signal_processing/resample_48khz.c"
    "webrtc/common_audio/signal_processing/resample_by_2.c"
    "webrtc/common_audio/signal_processing/resample_by_2_internal.c"
    "webrtc/common_audio/signal_processing/resample_by_2_internal.h"
    "webrtc/common_audio/signal_processing/resample_fractional.c"
    "webrtc/common_audio/signal_processing/spl_init.c"
    "webrtc/common_audio/signal_processing/spl_sqrt.c"
    "webrtc/common_audio/signal_processing/spl_sqrt_floor.c"
    "webrtc/common_audio/signal_processing/splitting_filter.c"
    "webrtc/common_audio/signal_processing/sqrt_of_one_minus_x_squared.c"
    "webrtc/common_audio/signal_processing/vector_scaling_operations.c"
    "webrtc/common_audio/vad/include/webrtc_vad.h"
    "webrtc/common_audio/vad/webrtc_vad.c"
    "webrtc/common_audio/vad/vad_core.c"
    "webrtc/common_audio/vad/vad_core.h"
    "webrtc/common_audio/vad/vad_filterbank.c"
    "webrtc/common_audio/vad/vad_filterbank.h"
    "webrtc/common_audio/vad/vad_gmm.c"
    "webrtc/common_audio/vad/vad_gmm.h"
    "webrtc/common_audio/vad/vad_sp.c"
    "webrtc/common_audio/vad/vad_sp.h"
)
target_compile_definitions(webrtc
    PRIVATE ${WEBRTC_DEFS}
)
target_include_directories(webrtc
    PRIVATE .
)

add_library(dictator SHARED
    dictator_Native.h
    jni_util.h
    audio.c
)
target_include_directories(dictator
    PRIVATE . ${JNI_INCLUDE_DIRS}
)
target_link_libraries(dictator webrtc)