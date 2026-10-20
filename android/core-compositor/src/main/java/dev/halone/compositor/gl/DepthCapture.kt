package dev.halone.compositor.gl

import android.opengl.GLES20
import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.FloatBuffer

/**
 * Copies a window's picture into a small square for the depth model: drawn
 * into an offscreen target and read back, rows top down. Render thread only.
 */
internal class DepthCapture {
    private var program = 0
    private var aPosition = 0
    private var uTexTransform = 0
    private var framebuffer = 0
    private var target = 0
    var size = 0
        private set
    private lateinit var vertices: FloatBuffer
    private val saved = IntArray(4)
    private val savedFramebuffer = IntArray(1)

    val initialised: Boolean get() = framebuffer != 0

    fun initialise(size: Int) {
        this.size = size
        program = Gl.program(VERTEX, FRAGMENT)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        uTexTransform = GLES20.glGetUniformLocation(program, "uTexTransform")
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
    }

    /** Draws external texture [textureId] (with its [texTransform]) into the square and reads it into [into]. */
    fun grab(textureId: Int, texTransform: FloatArray, into: ByteBuffer) {
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, saved, 0)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, savedFramebuffer, 0)
        val depthTest = GLES20.glIsEnabled(GLES20.GL_DEPTH_TEST)
        val blend = GLES20.glIsEnabled(GLES20.GL_BLEND)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        GLES20.glViewport(0, 0, size, size)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(Gl.TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniformMatrix4fv(uTexTransform, 1, false, texTransform, 0)
        vertices.position(0)
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPosition)
        into.position(0)
        GLES20.glReadPixels(0, 0, size, size, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, into)
        into.position(0)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, savedFramebuffer[0])
        GLES20.glViewport(saved[0], saved[1], saved[2], saved[3])
        if (depthTest) GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        if (blend) GLES20.glEnable(GLES20.GL_BLEND)
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
    }
}
