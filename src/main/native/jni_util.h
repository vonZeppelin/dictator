/*
 * Contains various useful macros and constants to ease JNI plumbing.
 * Redefines JNIEXPORT for GCC / LLVM compilers to control exported symbols visibility (fixed in Java 8+).
 * MUST be included after any <jni.h> header and doesn't include one itself on purpose.
 */
#ifndef JNI_UTIL_H
#define JNI_UTIL_H

const char RUNTIME_EXC_CLASS[] = "java/lang/RuntimeException";

#ifndef __has_attribute
    #define __has_attribute(x) 0
#endif
#if (defined(__GNUC__) && __GNUC__ >= 4) || (defined(__has_attribute) && __has_attribute(visibility))
    #undef JNIEXPORT
    #define JNIEXPORT __attribute__((visibility("default")))
#endif

#define THROW_EXC(env, type, msg) { \
    jclass clazz = (*env)->FindClass(env, type); \
    if (clazz != NULL) { \
        (*env)->ThrowNew(env, clazz, msg); \
    } \
    (*env)->DeleteLocalRef(env, clazz); \
}

#endif // JNI_UTIL_H
