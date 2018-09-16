/*
 * Contains various useful macros and constants to ease JNI plumbing.
 */
#ifndef JNI_UTIL_H
#define JNI_UTIL_H

static const char RUNTIME_EXC_CLASS[] = "java/lang/RuntimeException";
static const char OOM_ERR_CLASS[] = "java/lang/OutOfMemoryError";

#define THROW_EXC(env, type, msg) { \
    jclass clazz = (*env)->FindClass(env, type); \
    if (clazz != NULL) { \
        (*env)->ThrowNew(env, clazz, msg); \
    } \
    (*env)->DeleteLocalRef(env, clazz); \
}

#endif /* JNI_UTIL_H */
