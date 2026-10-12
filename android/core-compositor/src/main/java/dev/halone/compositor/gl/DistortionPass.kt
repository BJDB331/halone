package dev.halone.compositor.gl

import android.opengl.GLES20
import dev.halone.compositor.DistortionMesh
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

/**
 * Renders one eye offscreen, then draws it to the display through the lens
 * correction mesh.
 *
 * Two passes rather than displacing vertices in place, because the correction
 * belongs to the *display* and not to any object in the scene: panels, chrome
 * and cursor all need it, and a panel is a single large quad whose interior a
 * per-vertex warp would leave untouched. Rendering to a texture and warping the
 * texture corrects everything that was drawn, once.
 *
 * The offscreen target is slightly larger than the eye viewport — see
 * [DistortionMesh.requiredOverfill] — because the warp samples outside the
 * nominal frame at the corners. Without that margin the edges have nothing to
 * read and the frame ends in a black fringe that swims with head motion.
 */
internal class DistortionPass {

    private var program = 0
    private var aPosition = 0
    private var aTexCoord = 0
    private var uTexture = 0
    private var uReproject = 0
    private var uRotationStart = 0
    private var uRotationEnd = 0
    private var uTanScale = 0
    private var uTanOffset = 0

    private var reprojecting = false
    private val rotationStart = FloatArray(9)
    private val rotationEnd = FloatArray(9)
    private val tanScale = floatArrayOf(1f, 1f)
    private val tanOffset = floatArrayOf(0f, 0f)

    private var framebuffer = 0
    private var texture = 0
    private var depthBuffer = 0

    var textureWidth = 0
        private set
    var textureHeight = 0
        private set

    private var positions = Gl.floatBuffer(FloatArray(0))
    private var texCoords = Gl.floatBuffer(FloatArray(0))
    private var indices: ShortBuffer = ByteBuffer.allocateDirect(0)
        .order(ByteOrder.nativeOrder()).asShortBuffer()
    private var indexCount = 0

    val ready: Boolean get() = framebuffer != 0 && indexCount > 0

    /** The offscreen target, for reading back what was drawn into it. */
    val framebufferId: Int get() = framebuffer

    fun initialise() {
        program = Gl.program(VERTEX_SHADER, FRAGMENT_SHADER)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        uTexture = GLES20.glGetUniformLocation(program, "uTexture")
        uReproject = GLES20.glGetUniformLocation(program, "uReproject")
        uRotationStart = GLES20.glGetUniformLocation(program, "uRotationStart")
        uRotationEnd = GLES20.glGetUniformLocation(program, "uRotationEnd")
        uTanScale = GLES20.glGetUniformLocation(program, "uTanScale")
        uTanOffset = GLES20.glGetUniformLocation(program, "uTanOffset")
    }

    /**
     * How a texture coordinate in the offscreen image maps to a view-space
     * tangent, which is what lets the warp treat it as a direction.
     */
    fun setTangentMapping(scale: FloatArray, offset: FloatArray) {
        scale.copyInto(tanScale)
        offset.copyInto(tanOffset)
    }

    /**
     * The rotations that carry a display ray back into the rendered frame, for
     * the first and last rows lit. Every row between them interpolates.
     */
    fun setReprojection(start: FloatArray, end: FloatArray) {
        start.copyInto(rotationStart)
        end.copyInto(rotationEnd)
        reprojecting = true
    }

    fun clearReprojection() {
        reprojecting = false
    }

    fun setMesh(mesh: DistortionMesh) {
        positions = Gl.floatBuffer(mesh.positions)
        texCoords = Gl.floatBuffer(mesh.texCoords)
        indices = ByteBuffer.allocateDirect(mesh.indices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(mesh.indices)
            .apply { position(0) }
        indexCount = mesh.indices.size
    }

    /** (Re)creates the offscreen target. Safe to call when the size is unchanged. */
    fun resize(width: Int, height: Int) {
        if (width == textureWidth && height == textureHeight && framebuffer != 0) return
        release()
        textureWidth = width
        textureHeight = height

        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        texture = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null,
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        // Clamped so a sample that still lands outside repeats the edge pixel
        // rather than wrapping the opposite side of the frame into the corner.
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLES20.glGenRenderbuffers(1, ids, 0)
        depthBuffer = ids[0]
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, depthBuffer)
        GLES20.glRenderbufferStorage(
            GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, width, height
        )

        GLES20.glGenFramebuffers(1, ids, 0)
        framebuffer = ids[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, texture, 0,
        )
        GLES20.glFramebufferRenderbuffer(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT,
            GLES20.GL_RENDERBUFFER, depthBuffer,
        )
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            release()
            error("offscreen target incomplete: 0x%x".format(status))
        }
    }

    /** Directs drawing into the offscreen target and clears it. */
    fun begin() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        GLES20.glViewport(0, 0, textureWidth, textureHeight)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
    }

    /** Draws the offscreen image to [x] of the real framebuffer, through the mesh. */
    fun drawTo(x: Int, y: Int, width: Int, height: Int) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(x, y, width, height)

        // The mesh is a flat screen-space object; depth would only let it
        // occlude itself along the seams between cells.
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glUniform1i(uTexture, 0)
        GLES20.glUniform1f(uReproject, if (reprojecting) 1f else 0f)
        if (reprojecting) {
            GLES20.glUniformMatrix3fv(uRotationStart, 1, false, rotationStart, 0)
            GLES20.glUniformMatrix3fv(uRotationEnd, 1, false, rotationEnd, 0)
            GLES20.glUniform2f(uTanScale, tanScale[0], tanScale[1])
            GLES20.glUniform2f(uTanOffset, tanOffset[0], tanOffset[1])
        }

        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, positions)
        GLES20.glEnableVertexAttribArray(aTexCoord)
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 0, texCoords)

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, indices)

        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aTexCoord)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    fun release() {
        if (framebuffer != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
        if (texture != 0) GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
        if (depthBuffer != 0) GLES20.glDeleteRenderbuffers(1, intArrayOf(depthBuffer), 0)
        framebuffer = 0; texture = 0; depthBuffer = 0
        textureWidth = 0; textureHeight = 0
    }

    private companion object {
        const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            varying float vRow;
            void main() {
                vTexCoord = aTexCoord;
                // Raster position, not texture position: what a row is lit at
                // depends on where it sits on the *display*.
                vRow = 0.5 - 0.5 * aPosition.y;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        // highp throughout: the warp works in tangents of a 40-degree field, and
        // mediump quantises that coarsely enough to band the image.
        const val FRAGMENT_SHADER = """
            precision highp float;
            uniform sampler2D uTexture;
            uniform float uReproject;
            uniform mat3 uRotationStart;
            uniform mat3 uRotationEnd;
            uniform vec2 uTanScale;
            uniform vec2 uTanOffset;
            varying vec2 vTexCoord;
            varying float vRow;

            vec2 carryBack(vec2 uv, mat3 rotation) {
                vec3 ray = vec3(uv * uTanScale + uTanOffset, -1.0);
                vec3 turned = rotation * ray;
                float depth = max(-turned.z, 0.001);
                return (turned.xy / depth - uTanOffset) / uTanScale;
            }

            void main() {
                vec2 uv = vTexCoord;
                if (uReproject > 0.5) {
                    uv = mix(carryBack(uv, uRotationStart), carryBack(uv, uRotationEnd), vRow);
                }
                gl_FragColor = texture2D(uTexture, uv);
            }
        """
    }
}
