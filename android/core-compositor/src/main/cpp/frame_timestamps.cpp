#include <jni.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <cstring>

// EGL_ANDROID_get_frame_timestamps has no Java binding, which is the only
// reason this library exists.

namespace {

PFNEGLGETNEXTFRAMEIDANDROIDPROC getNextFrameId = nullptr;
PFNEGLGETFRAMETIMESTAMPSANDROIDPROC getFrameTimestamps = nullptr;

const EGLint kNames[] = {
    EGL_RENDERING_COMPLETE_TIME_ANDROID,
    EGL_COMPOSITION_LATCH_TIME_ANDROID,
    EGL_DISPLAY_PRESENT_TIME_ANDROID,
};
constexpr int kNameCount = sizeof(kNames) / sizeof(kNames[0]);

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_halone_compositor_gl_FrameTimestamps_nativeEnable(JNIEnv*, jobject) {
    EGLDisplay display = eglGetCurrentDisplay();
    EGLSurface surface = eglGetCurrentSurface(EGL_DRAW);
    if (display == EGL_NO_DISPLAY || surface == EGL_NO_SURFACE) return JNI_FALSE;

    const char* extensions = eglQueryString(display, EGL_EXTENSIONS);
    if (extensions == nullptr || strstr(extensions, "EGL_ANDROID_get_frame_timestamps") == nullptr) {
        return JNI_FALSE;
    }
    getNextFrameId = reinterpret_cast<PFNEGLGETNEXTFRAMEIDANDROIDPROC>(
        eglGetProcAddress("eglGetNextFrameIdANDROID"));
    getFrameTimestamps = reinterpret_cast<PFNEGLGETFRAMETIMESTAMPSANDROIDPROC>(
        eglGetProcAddress("eglGetFrameTimestampsANDROID"));
    if (getNextFrameId == nullptr || getFrameTimestamps == nullptr) return JNI_FALSE;

    return eglSurfaceAttrib(display, surface, EGL_TIMESTAMPS_ANDROID, EGL_TRUE) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_halone_compositor_gl_FrameTimestamps_nativeNextFrameId(JNIEnv*, jobject) {
    if (getNextFrameId == nullptr) return -1;
    EGLuint64KHR id = 0;
    if (!getNextFrameId(eglGetCurrentDisplay(), eglGetCurrentSurface(EGL_DRAW), &id)) return -1;
    return static_cast<jlong>(id);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_halone_compositor_gl_FrameTimestamps_nativeQuery(JNIEnv* env, jobject, jlong frameId, jlongArray out) {
    if (getFrameTimestamps == nullptr || env->GetArrayLength(out) < kNameCount) return JNI_FALSE;
    EGLnsecsANDROID values[kNameCount];
    if (!getFrameTimestamps(eglGetCurrentDisplay(), eglGetCurrentSurface(EGL_DRAW),
                            static_cast<EGLuint64KHR>(frameId), kNameCount, kNames, values)) {
        return JNI_FALSE;
    }
    jlong copy[kNameCount];
    for (int i = 0; i < kNameCount; ++i) copy[i] = static_cast<jlong>(values[i]);
    env->SetLongArrayRegion(out, 0, kNameCount, copy);
    return JNI_TRUE;
}
