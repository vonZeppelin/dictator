#include "dictator_Native.h"
#include "webrtc/webrtc/common_audio/vad/include/webrtc_vad.h"
#include "jni_util.h"

JNIEXPORT jlong JNICALL Java_dictator_Native_createVAD(JNIEnv *env, jclass clazz, jint aggressiveness) {
    VadInst* handle = NULL;
    if (WebRtcVad_Create(&handle)) {
        THROW_EXC(env, RUNTIME_EXC_CLASS, "Couldn't create VAD instance");
        goto err;
    }
    if (WebRtcVad_Init(handle)) {
        THROW_EXC(env, RUNTIME_EXC_CLASS, "Couldn't initialize VAD instance");
        goto err;
    }
    if (WebRtcVad_set_mode(handle, aggressiveness)) {
        THROW_EXC(env, RUNTIME_EXC_CLASS, "Couldn't set VAD aggressiveness");
        goto err;
    }
    return (jlong) handle;
    err:
        if (handle != NULL) {
            WebRtcVad_Free(handle);
        }
        return (jlong) NULL;
}

JNIEXPORT void JNICALL Java_dictator_Native_freeVAD(JNIEnv *env, jclass clazz, jlong handle) {
    WebRtcVad_Free((VadInst*) handle);
}

JNIEXPORT jboolean JNICALL Java_dictator_Native_processVAD(JNIEnv *env, jclass clazz, jlong handle,
                                                           jint sampleRate, jbyteArray audio, jint frameLen) {
    jboolean isCopy;
    jbyte *audioBytes = (*env)->GetPrimitiveArrayCritical(env, audio, &isCopy);
    if (audioBytes == NULL) {
        THROW_EXC(env, RUNTIME_EXC_CLASS, "Couldn't get audio data");
        return JNI_FALSE;
    }
    // TODO typecast vs memcpy
    int result = WebRtcVad_Process((VadInst*) handle, sampleRate, (int16_t*) audioBytes, frameLen);
    (*env)->ReleasePrimitiveArrayCritical(env, audio, audioBytes, JNI_ABORT);

    if (result == -1) {
        THROW_EXC(env, RUNTIME_EXC_CLASS, "Couldn't process audio");
        return JNI_FALSE;
    }
    return result == 1 ? JNI_TRUE : JNI_FALSE;
}
