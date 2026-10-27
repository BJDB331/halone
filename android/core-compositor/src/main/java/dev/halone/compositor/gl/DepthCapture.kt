package dev.halone.compositor.gl

import android.opengl.GLES20
import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.FloatBuffer

/**
 * Copies a window's picture into a small square for the depth model: drawn
 * into an offscreen target and read back, rows top down. Render thread only.
 *
 * The read-back is asynchronous: [start] queues the copy into a pixel buffer
 * behind a fence and returns at once, and [collect] picks it up on a later
 * frame once the GPU has got there. A plain glReadPixels waits for every
 * draw before it to finish, which cost the compositor a frame each time.
 */
internal class DepthCapture {
    private var program = 0
    private var aPosition = 0
    private var uTexTransform = 0
    /** The same, sampling a `GL_TEXTURE_2D`: a picture held in a [FrameRing]. */
    private var program2D = 0
    private var aPosition2D = 0
    private var uTexTransform2D = 0
    private var framebuffer = 0
    private var target = 0
    var size = 0
        private set
    private lateinit var vertices: FloatBuffer
    private val saved = IntArray(4)
    private val savedFramebuffer = IntArray(1)
    private var pixelBuffer = 0
    private var fence = 0L

    /** A copy has been started and not collected yet. */
    val pending: Boolean get() = fence != 0L

    val initialised: Boolean get() = framebuffer != 0

    fun initialise(size: Int) {
        this.size = size
        program = Gl.program(VERTEX, FRAGMENT)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        uTexTransform = GLES20.glGetUniformLocation(program, "uTexTransform")
        program2D = Gl.program(VERTEX, FRAGMENT_2D)
        aPosition2D = GLES20.glGetAttribLocation(program2D, "aPosition")
        uTexTransform2D = GLES20.glGetUniformLocation(program2D, "uTexTransform")
        vertices = Gl.floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))

        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        target = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, target)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, size, size, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glGenFramebuffers(1, ids, 0)
        framebuffer = ids[0]
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, savedFramebuffer, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, target, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, savedFramebuffer[0])

        GLES20.glGenBuffers(1, ids, 0)
        pixelBuffer = ids[0]
        GLES20.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pixelBuffer)
        GLES20.glBufferData(GLES30.GL_PIXEL_PACK_BUFFER, size * size * 4, null, GLES30.GL_STREAM_READ)
        GLES20.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0)
    }

    /** Forgets everything without freeing it, for when the GL context it lived in is gone. */
    fun lost() {
        fence = 0L
        pixelBuffer = 0
        framebuffer = 0
        target = 0
        program = 0
        program2D = 0
    }

    /** Frees everything, so [initialise] can run again at another size. */
    fun release() {
        if (fence != 0L) GLES30.glDeleteSync(fence)
        fence = 0L
        if (pixelBuffer != 0) GLES20.glDeleteBuffers(1, intArrayOf(pixelBuffer), 0)
        if (framebuffer != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
        if (target != 0) GLES20.glDeleteTextures(1, intArrayOf(target), 0)
        if (program != 0) GLES20.glDeleteProgram(program)
        if (program2D != 0) GLES20.glDeleteProgram(program2D)
        program2D = 0
        pixelBuffer = 0
        framebuffer = 0
        target = 0
        program = 0
    }

    /**
     * Draws external texture [textureId] (with its [texTransform]) into the
     * square and starts copying it out; [collect] it on a later frame.
     */
    fun start(textureId: Int, texTransform: FloatArray, external: Boolean = true) {
        if (pending) return
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, saved, 0)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, savedFramebuffer, 0)
        val depthTest = GLES20.glIsEnabled(GLES20.GL_DEPTH_TEST)
        val blend = GLES20.glIsEnabled(GLES20.GL_BLEND)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        GLES20.glViewport(0, 0, size, size)
        val position = if (external) aPosition else aPosition2D
        GLES20.glUseProgram(if (external) program else program2D)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(if (external) Gl.TEXTURE_EXTERNAL_OES else GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniformMatrix4fv(if (external) uTexTransform else uTexTransform2D, 1, false, texTransform, 0)
        vertices.position(0)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(position)
        GLES20.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pixelBuffer)
        GLES30.glReadPixels(0, 0, size, size, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, 0)
        GLES20.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0)
        fence = GLES30.glFenceSync(GLES30.GL_SYNC_GPU_COMMANDS_COMPLETE, 0)
        // Sent now, so the GPU gets to it while this frame is still drawing.
        GLES20.glFlush()

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, savedFramebuffer[0])
        GLES20.glViewport(saved[0], saved[1], saved[2], saved[3])
        if (depthTest) GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        if (blend) GLES20.glEnable(GLES20.GL_BLEND)
    }

    /**
     * Copies the started picture into [into] if the GPU has finished it, and
     * says whether it had; never waits.
     */
    fun collect(into: ByteBuffer): Boolean {
        if (fence == 0L) return false
        val status = GLES30.glClientWaitSync(fence, 0, 0L)
        if (status != GLES30.GL_ALREADY_SIGNALED && status != GLES30.GL_CONDITION_SATISFIED) return false
        GLES30.glDeleteSync(fence)
        fence = 0L
        val bytes = size * size * 4
        GLES20.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pixelBuffer)
        val mapped = GLES30.glMapBufferRange(GLES30.GL_PIXEL_PACK_BUFFER, 0, bytes, GLES30.GL_MAP_READ_BIT) as? ByteBuffer
        val ok = mapped != null
        if (mapped != null) {
            into.position(0)
            mapped.position(0)
            into.put(mapped)
            into.position(0)
            GLES30.glUnmapBuffer(GLES30.GL_PIXEL_PACK_BUFFER)
        }
        GLES20.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0)
        return ok
    }

    private companion object {
        // Read-back starts at the bottom row, so the bottom of the target
        // takes the top of the picture: the buffer comes out rows top down.
        val VERTEX = """
            #version 300 es
            in vec2 aPosition;
            uniform mat4 uTexTransform;
            out vec2 vTexCoord;
            void main() {
                vec2 uv = vec2((aPosition.x + 1.0) * 0.5, (1.0 - aPosition.y) * 0.5);
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
            void main() { fragColor = vec4(texture(uTexture, vTexCoord).rgb, 1.0); }
        """.trimIndent()

        val FRAGMENT_2D = """
            #version 300 es
            precision mediump float;
            uniform sampler2D uTexture;
            in vec2 vTexCoord;
            out vec4 fragColor;
            void main() { fragColor = vec4(texture(uTexture, vTexCoord).rgb, 1.0); }
        """.trimIndent()
    }
}
