package dev.halone.compositor.gl

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import dev.halone.compositor.RecordingTarget
import java.util.concurrent.atomic.AtomicIntegerArray

/**
 * Feeds a [RecordingTarget] from its own thread and GL context, which shares
 * textures with the compositor's. The render thread only copies the eye into
 * a free texture and fences it; drawing into the encoder's surface, and
 * waiting on the encoder, happen here. With both textures still in flight a
 * frame is dropped rather than waited for, so a slow encoder can never stall
 * the glasses.
 */
internal class RecordingStream(val target: RecordingTarget, private val log: (String) -> Unit) {
    private val textures = IntArray(SLOTS)
    private val inFlight = AtomicIntegerArray(SLOTS)
    private var width = 0
    private var height = 0
    private val thread = HandlerThread("halone-recording").apply { start() }
    private val handler = Handler(thread.looper)
    private lateinit var display: EGLDisplay
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private val quad = BitmapQuad()
    private val mvp = FloatArray(16).also {
        Matrix.setIdentityM(it, 0)
        Matrix.scaleM(it, 0, 2f, -2f, 1f)
    }

    @Volatile
    var failed = false
        private set

    /** On the render thread, with the compositor's context current. False if the encoder's surface cannot be drawn to. */
    fun start(): Boolean {
        display = EGL14.eglGetCurrentDisplay()
        val shared = EGL14.eglGetCurrentContext()
        val config = recordableConfig() ?: return false.also { log("  recording: no EGL config for the encoder") }
        context = EGL14.eglCreateContext(display, config, shared, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        if (context == EGL14.EGL_NO_CONTEXT) return false.also { log("  recording: no shared context (0x%x)".format(EGL14.eglGetError())) }
        surface = EGL14.eglCreateWindowSurface(display, config, target.surface, intArrayOf(EGL14.EGL_NONE), 0)
        if (surface == EGL14.EGL_NO_SURFACE) {
            log("  recording: no surface for the encoder (0x%x)".format(EGL14.eglGetError()))
            EGL14.eglDestroyContext(display, context)
            return false
        }
        handler.post {
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                log("  recording: surface would not become current (0x%x)".format(EGL14.eglGetError()))
                failed = true
                return@post
            }
            quad.initialise()
        }
        log("  recording: the left eye at ${target.width}x${target.height}")
        return true
    }

    /** On the render thread: copies the eye if a texture is free, and hands it to the recording thread. */
    fun offer(region: EyeRegion, timestampNs: Long) {
        if (failed) return
        val slot = (0 until SLOTS).firstOrNull { inFlight.compareAndSet(it, 0, 1) } ?: return
        if (textures[slot] == 0 || width != region.width || height != region.height) allocate(region.width, region.height)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, region.framebuffer)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[slot])
        GLES20.glCopyTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, region.x, region.y, region.width, region.height)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        val fence = GLES30.glFenceSync(GLES30.GL_SYNC_GPU_COMMANDS_COMPLETE, 0)
        GLES20.glFlush()
        val texture = textures[slot]
        handler.post { draw(slot, texture, fence, timestampNs) }
    }

    private fun draw(slot: Int, texture: Int, fence: Long, timestampNs: Long) {
        try {
            if (failed) return
            GLES30.glWaitSync(fence, 0, GLES30.GL_TIMEOUT_IGNORED)
            GLES30.glDeleteSync(fence)
            GLES20.glViewport(0, 0, target.width, target.height)
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            quad.draw(mvp, texture)
            EGLExt.eglPresentationTimeANDROID(display, surface, timestampNs)
            if (!EGL14.eglSwapBuffers(display, surface)) {
                log("  recording: the encoder took no frame (0x%x)".format(EGL14.eglGetError()))
                failed = true
            }
        } finally {
            inFlight.set(slot, 0)
        }
    }

    /** On the render thread. The recording thread lets go of the surface before the encoder is released. */
    fun stop() {
        failed = true
        val done = java.util.concurrent.CountDownLatch(1)
        handler.post {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            done.countDown()
        }
        done.await(STOP_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        thread.quitSafely()
        GLES20.glDeleteTextures(SLOTS, textures, 0)
    }

    private fun allocate(width: Int, height: Int) {
        if (textures[0] != 0) GLES20.glDeleteTextures(SLOTS, textures, 0)
        GLES20.glGenTextures(SLOTS, textures, 0)
        for (texture in textures) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }
        this.width = width
        this.height = height
    }

    /** A video encoder's surface wants a config marked recordable; Qualcomm's ignores frames from one that is not. */
    private fun recordableConfig(): EGLConfig? {
        val attributes = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val found = IntArray(1)
        return if (EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, found, 0) && found[0] > 0) configs[0] else null
    }

    private companion object {
        const val SLOTS = 2
        const val EGL_RECORDABLE_ANDROID = 0x3142
        const val STOP_TIMEOUT_MS = 2_000L
    }
}
