#include "dictator_Native.h"
#include "webrtc/common_audio/vad/include/webrtc_vad.h"
#include "jni_util.h"

JNIEXPORT jlong JNICALL Java_dictator_Native_createVAD(JNIEnv *env, jclass clazz, jint aggressiveness) {
    VadInst* handle = NULL;
    if (WebRtcVad_Create(&handle) != 0) {
        THROW_EXC(env, RUNTIME_EXC_CLASS, "Couldn't create VAD instance");
        return (jlong) NULL;
    }
    if (WebRtcVad_Init(handle) != 0) {
        THROW_EXC(env, RUNTIME_EXC_CLASS, "Couldn't initialize VAD instance");
        return (jlong) NULL;
    }
    if (WebRtcVad_set_mode(handle, aggressiveness) != 0) {
        THROW_EXC(env, RUNTIME_EXC_CLASS, "Couldn't set VAD aggressiveness");
        return (jlong) NULL;
    }
    return (jlong) handle;
}

JNIEXPORT void JNICALL Java_dictator_Native_freeVAD(JNIEnv *env, jclass clazz, jlong handle) {
    WebRtcVad_Free((VadInst*) handle);
}
