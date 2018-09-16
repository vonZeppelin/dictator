#include <stdlib.h>
#include "dictator_Native.h"
#include "common_audio/vad/include/webrtc_vad.h"
#include "lame.h"
#include "jni_util.h"

static void output_noop(const char *format, va_list ap) {
    return;
}

JNIEXPORT jlong JNICALL Java_dictator_Native_createVAD(JNIEnv *env, jclass clazz, jint aggressiveness) {
    VadInst *handle = WebRtcVad_Create();
    if (handle == NULL) {
        THROW_EXC(env, OOM_ERR_CLASS, "Couldn't create VAD instance");
        return (jlong) NULL;
    }

    if (WebRtcVad_Init(handle) != 0) {
        THROW_EXC(env, RUNTIME_EXC_CLASS, "Couldn't initialize VAD instance");
        goto err;
    }
    if (WebRtcVad_set_mode(handle, aggressiveness) != 0) {
        THROW_EXC(env, RUNTIME_EXC_CLASS, "Couldn't set VAD aggressiveness");
        goto err;
    }
    return (jlong) handle;

    err:
        WebRtcVad_Free(handle);
        return (jlong) NULL;
}

JNIEXPORT void JNICALL Java_dictator_Native_freeVAD(JNIEnv *env, jclass clazz, jlong handle) {
    WebRtcVad_Free((VadInst*) handle);
}

JNIEXPORT jboolean JNICALL Java_dictator_Native_processVAD(JNIEnv *env, jclass clazz, jlong handle,
                                                           jint sampleRate, jbyteArray audio) {
    int result;
    size_t frameLen = (*env)->GetArrayLength(env, audio) / 2;
    /* TODO typecast vs memcpy */
    int16_t *audioBytes = (*env)->GetPrimitiveArrayCritical(env, audio, NULL);
    if (audioBytes == NULL) {
        /* OOM is already generated by GetPrimitiveArrayCritical */
        return JNI_FALSE;
    }
    result = WebRtcVad_Process((VadInst*) handle, sampleRate, audioBytes, frameLen);
    (*env)->ReleasePrimitiveArrayCritical(env, audio, audioBytes, JNI_ABORT);

    if (result == -1) {
        THROW_EXC(env, RUNTIME_EXC_CLASS, "Couldn't process audio");
        return JNI_FALSE;
    }
    return result == 1 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL Java_dictator_Native_createLAME(JNIEnv *env, jclass clazz, jint sampleRate, jint brate) {
    int error;
    lame_t lame = lame_init();
    if (lame == NULL) {
        THROW_EXC(env, OOM_ERR_CLASS, "Couldn't create LAME instance");
        return (jlong) NULL;
    }

    /* silence LAME */
    lame_set_errorf(lame, output_noop);
    lame_set_debugf(lame, output_noop);
    lame_set_msgf(lame, output_noop);

    lame_set_write_id3tag_automatic(lame, 0);
    error = lame_set_bWriteVbrTag(lame, 0);
    error |= lame_set_num_channels(lame, 1);
    error |= lame_set_in_samplerate(lame, sampleRate);
    error |= lame_set_brate(lame, brate);
    error |= lame_set_quality(lame, 5);
    error |= lame_init_params(lame);
    if (error == LAME_NOERROR) {
        return (jlong) lame;
    } else {
        lame_close(lame);
        THROW_EXC(env, RUNTIME_EXC_CLASS, "Couldn't init LAME instance");
        return (jlong) NULL;
    }
}

JNIEXPORT jbyteArray JNICALL Java_dictator_Native_encodeLAME(JNIEnv *env, jclass clazz,
                                                             jlong handle, jbyteArray audio) {
    int ret1;
    short int *audioBytes;
    jbyteArray output = NULL;
    lame_t lame = (lame_t) handle;
    int nsamples = (*env)->GetArrayLength(env, audio) / 2;
    int mp3buf1Size = (int) (1.25 * nsamples + 7200);
    unsigned char *mp3buf1 = malloc(mp3buf1Size);
    if (mp3buf1 == NULL) {
        THROW_EXC(env, OOM_ERR_CLASS, "Couldn't allocate mp3 buffer");
        return NULL;
    }

    /* TODO typecast vs memcpy */
    audioBytes = (*env)->GetPrimitiveArrayCritical(env, audio, NULL);
    if (audioBytes == NULL) {
        /* OOM is already generated by GetPrimitiveArrayCritical */
        goto exit;
    }
    /* TODO remove? */
    lame_init_bitstream(lame);
    ret1 = lame_encode_buffer(lame, audioBytes, audioBytes, nsamples, mp3buf1, mp3buf1Size);
    (*env)->ReleasePrimitiveArrayCritical(env, audio, audioBytes, JNI_ABORT);

    if (ret1 >= 0) {
        /* allocated on stack, but OK */
        unsigned char mp3buf2[7200];
        int ret2 = lame_encode_flush(lame, mp3buf2, 7200);
        if (ret2 >= 0) {
            output = (*env)->NewByteArray(env, ret1 + ret2);
            (*env)->SetByteArrayRegion(env, output, 0, ret1, (jbyte*) mp3buf1);
            (*env)->SetByteArrayRegion(env, output, ret1, ret2, (jbyte*) mp3buf2);
            goto exit;
        }
    }
    THROW_EXC(env, RUNTIME_EXC_CLASS, "Couldn't encode audio data");

    exit:
        free(mp3buf1);
        return output;
}

JNIEXPORT void JNICALL Java_dictator_Native_freeLAME(JNIEnv *env, jclass clazz, jlong handle) {
    lame_close((lame_global_flags*) handle);
}
