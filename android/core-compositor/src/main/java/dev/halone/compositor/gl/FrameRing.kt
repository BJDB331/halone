package dev.halone.compositor.gl

import android.opengl.GLES20
import java.nio.FloatBuffer
import kotlin.math.roundToInt

/**
 * The last few pictures of one window converted to 3D, copied out of its
 * external texture as they arrive, so that it can be shown a little late,
 * with the depth that was estimated from that very picture.
 *
 * Depth takes tens of milliseconds to estimate. Shown at once, a moving
 * picture runs ahead of its depth, and the depth's bumps trail the things
 * that made them: in a video, faces and bodies seem to warp as they move,
 * and after a cut the old shot's depth lands on the new one for a moment.
 * Held back by about as long as an estimate takes, every picture gets its
 * own depth instead.
 *
 * Render thread only.
 */
internal class FrameRing(private val capacity: Int = CAPACITY) {

    class Frame(val texture: Int) {
        /** Counts up from 1 with each picture copied in; 0 for an empty slot. */
        var sequence = 0L
            internal set

        /** When the picture arrived, on the [System.nanoTime] clock. */
        var arrivedNs = 0L
            internal set
    }

    private val frames = ArrayList<Frame>()
    private var framebuffer = 0
    private var newest = -1
    private var counter = 0L

    var width = 0
        private set
    var height = 0
        private set

    /** The picture last copied in, or null before the first. */
    fun newest(): Frame? = frames.getOrNull(newest)?.takeIf { it.sequence != 0L }

    /**
     * The picture to show at [timeNs]: the newest that arrived by then, or,
     * when none has yet, the oldest there is.
     */
    fun at(timeNs: Long): Frame? {
        var best: Frame? = null
        var oldest: Frame? = null
        for (frame in frames) {
            if (frame.sequence == 0L) continue
            if (frame.arrivedNs <= timeNs && (best == null || frame.sequence > best.sequence)) best = frame
            if (oldest == null || frame.sequence < oldest.sequence) oldest = frame
        }
        return best ?: oldest
    }

    /** When the picture numbered [sequence] arrived, if it is still held. */
    fun arrivedAt(sequence: Long): Long? = frames.firstOrNull { it.sequence == sequence }?.arrivedNs

    /**
     * Copies external texture [oesTexture], [widthPx] by [heightPx] with its
     * [texTransform], in as the newest picture, overwriting the oldest.
     */
    fun push(copier: FrameCopier, oesTexture: Int, texTransform: FloatArray, widthPx: Int, heightPx: Int, nowNs: Long) {
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(widthPx, heightPx))
        val w = (widthPx * scale).roundToInt().coerceAtLeast(1)
        val h = (heightPx * scale).roundToInt().coerceAtLeast(1)
        if (w != width || h != height || frames.isEmpty()) allocate(w, h)
        newest = (newest + 1) % frames.size
        val frame = frames[newest]
        copier.copy(oesTexture, texTransform, framebuffer, frame.texture, w, h)
        frame.sequence = ++counter
        frame.arrivedNs = nowNs
    }

    private fun allocate(w: Int, h: Int) {
        release()
        width = w
        height = h
        val ids = IntArray(capacity)
        GLES20.glGenTextures(capacity, ids, 0)
        for (id in ids) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            frames += Frame(id)
        }
        val fb = IntArray(1)
        GLES20.glGenFramebuffers(1, fb, 0)
        framebuffer = fb[0]
        newest = -1
    }

    fun release() {
        if (frames.isNotEmpty()) GLES20.glDeleteTextures(frames.size, frames.map { it.texture }.toIntArray(), 0)
        if (framebuffer != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
        frames.clear()
        framebuffer = 0
        newest = -1
        width = 0
        height = 0
    }

    companion object {
        /** About 100 ms at 60 Hz: more than any estimate should take. */
        const val CAPACITY = 6

        /** Pictures are kept no larger than this on their longer side, to bound memory: ~8 MB each. */
        const val MAX_SIDE = 2048
    }
}

/**
 * Copies an external texture, upright, into a `GL_TEXTURE_2D`, so that the
 * copy sampled at (u, v) is the original sampled through its transform at
 * (u, v): the copy needs no transform of its own. Render thread only.
 */
internal class FrameCopier {
    private var program = 0
    private var aPosition = 0
    private var uTexTransform = 0
    private lateinit var vertices: FloatBuffer
    private val saved = IntArray(4)
    private val savedFramebuffer = IntArray(1)

    fun initialise() {
        program = Gl.program(VERTEX, FRAGMENT)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        uTexTransform = GLES20.glGetUniformLocation(program, "uTexTransform")
        GLES20.glUseProgram(program)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)
        vertices = Gl.floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    }

    fun copy(oesTexture: Int, texTransform: FloatArray, framebuffer: Int, target: Int, w: Int, h: Int) {
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, saved, 0)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, savedFramebuffer, 0)
        val depthTest = GLES20.glIsEnabled(GLES20.GL_DEPTH_TEST)
        val blend = GLES20.glIsEnabled(GLES20.GL_BLEND)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, target, 0)
        GLES20.glViewport(0, 0, w, h)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(Gl.TEXTURE_EXTERNAL_OES, oesTexture)
        GLES20.glUniformMatrix4fv(uTexTransform, 1, false, texTransform, 0)
        vertices.position(0)
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPosition)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, savedFramebuffer[0])
        GLES20.glViewport(saved[0], saved[1], saved[2], saved[3])
        if (depthTest) GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        if (blend) GLES20.glEnable(GLES20.GL_BLEND)
    }

    private companion object {
        val VERTEX = """
            #version 300 es
            in vec2 aPosition;
            uniform mat4 uTexTransform;
            out vec2 vTexCoord;
            void main() {
                vec2 uv = (aPosition + 1.0) * 0.5;
                vTexCoord = (uTexTransform * vec4(uv, 0.0, 1.0)).xy;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """.trimIndent()

        val FRAGMENT = """
            #version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision mediump float;
            uniform samplerExternalOES uTexture;
            in vec2 vTexCoord;
            out vec4 fragColor;
            void main() { fragColor = texture(uTexture, vTexCoord); }
        """.trimIndent()
    }
}
