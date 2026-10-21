package dev.halone.compositor.gl

import android.opengl.GLES20
import dev.halone.compositor.StereoLayout
import dev.halone.compositor.StereoRegion
import dev.halone.glasses.math.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

internal object Gl {
    /** From GL_OES_EGL_image_external; not in the GLES20 constants. */
    const val TEXTURE_EXTERNAL_OES = 0x8D65

    fun program(vertex: String, fragment: String): Int {
        val vs = shader(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = shader(GLES20.GL_FRAGMENT_SHADER, fragment)
        val id = GLES20.glCreateProgram()
        GLES20.glAttachShader(id, vs)
        GLES20.glAttachShader(id, fs)
        GLES20.glLinkProgram(id)

        val status = IntArray(1)
        GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetProgramInfoLog(id)
            GLES20.glDeleteProgram(id)
            error("link failed: $log")
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return id
    }

    private fun shader(type: Int, source: String): Int {
        val id = GLES20.glCreateShader(type)
        GLES20.glShaderSource(id, source)
        GLES20.glCompileShader(id)
        val status = IntArray(1)
        GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetShaderInfoLog(id)
            GLES20.glDeleteShader(id)
            error("compile failed: $log\n$source")
        }
        return id
    }

    fun floatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(values)
            .apply { position(0) }
}

/**
 * A quad in 3D carrying a `SurfaceTexture`. One of these per panel.
 *
 * Same external-texture sampling as the flat spike, but with a real MVP so the
 * panel can sit at a pose in the world rather than filling the screen.
 */
internal class TexturedQuad3D {
    private var program = 0
    private var aPosition = 0
    private var aTexCoord = 0
    private var uMvp = 0
    private var uTexTransform = 0
    private var uScanRotation = 0
    private var uClipPerRadian = 0
    private var uSizePx = 0
    private var uRectPx = 0
    private var uCropV = 0
    private var uRadiusPx = 0
    private var uAlpha = 0
    private var uStereo = 0
    private var uStereoRect = 0
    private var uEye = 0
    private var uDepthPx = 0
    private var uConvergence = 0
    private lateinit var vertices: FloatBuffer

    fun initialise() {
        program = Gl.program(VERTEX, FRAGMENT)
        uAlpha = GLES20.glGetUniformLocation(program, "uAlpha")
        uStereo = GLES20.glGetUniformLocation(program, "uStereo")
        uStereoRect = GLES20.glGetUniformLocation(program, "uStereoRect")
        uEye = GLES20.glGetUniformLocation(program, "uEye")
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uTexTransform = GLES20.glGetUniformLocation(program, "uTexTransform")
        uScanRotation = GLES20.glGetUniformLocation(program, "uScanRotation")
        uClipPerRadian = GLES20.glGetUniformLocation(program, "uClipPerRadian")
        uSizePx = GLES20.glGetUniformLocation(program, "uSizePx")
        uRectPx = GLES20.glGetUniformLocation(program, "uRectPx")
        uCropV = GLES20.glGetUniformLocation(program, "uCropV")
        uRadiusPx = GLES20.glGetUniformLocation(program, "uRadiusPx")
        uDepthPx = GLES20.glGetUniformLocation(program, "uDepthPx")
        uConvergence = GLES20.glGetUniformLocation(program, "uConvergence")
        // The depth map lives on unit 1 for good: two sampler types sharing
        // unit 0 would fail every draw, converted or not.
        GLES20.glUseProgram(program)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uDepth"), 1)
        vertices = Gl.floatBuffer(QUAD)
    }

    fun newTexture(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(Gl.TEXTURE_EXTERNAL_OES, ids[0])
        GLES20.glTexParameteri(Gl.TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(Gl.TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(Gl.TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(Gl.TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return ids[0]
    }

    fun draw(
        mvp: FloatArray,
        textureId: Int,
        texTransform: FloatArray,
        scanYaw: Float = 0f,
        scanPitch: Float = 0f,
        clipPerRadianX: Float = 0f,
        clipPerRadianY: Float = 0f,
        widthPx: Float = 1f,
        heightPx: Float = 1f,
        cornerRadiusPx: Float = 0f,
        alpha: Float = 1f,
        rectPx: FloatArray? = null,
        cropV: Float = 1f,
        stereo: StereoRegion? = null,
        eye: Int = 0,
        /** Most an eye's picture moves for the nearest depth, in window pixels; 0 draws it flat. */
        depthPx: Float = 0f,
        /** A `GL_TEXTURE_2D` depth map over the whole window, 1 nearest; read when [depthPx] is set. */
        depthTexture: Int = 0,
        /** The depth, 0..1, drawn at the window's own distance: nearer comes out, farther goes in. */
        convergence: Float = 0.5f,
    ) {
        GLES20.glUseProgram(program)
        if (depthPx > 0f && depthTexture != 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, depthTexture)
        }
        GLES20.glUniform1f(uDepthPx, if (depthTexture != 0 && stereo == null) depthPx else 0f)
        GLES20.glUniform1f(uConvergence, convergence)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(Gl.TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uTexTransform, 1, false, texTransform, 0)
        GLES20.glUniform2f(uScanRotation, scanYaw, scanPitch)
        GLES20.glUniform2f(uClipPerRadian, clipPerRadianX, clipPerRadianY)
        GLES20.glUniform2f(uSizePx, widthPx, heightPx)
        if (rectPx != null) GLES20.glUniform4fv(uRectPx, 1, rectPx, 0)
        else GLES20.glUniform4f(uRectPx, 0f, 0f, widthPx, heightPx)
        GLES20.glUniform1f(uCropV, cropV)
        GLES20.glUniform1f(uRadiusPx, cornerRadiusPx)
        GLES20.glUniform1f(uAlpha, alpha)
        GLES20.glUniform1i(
            uStereo,
            when (stereo?.layout) {
                StereoLayout.SIDE_BY_SIDE -> 1
                StereoLayout.TOP_BOTTOM -> 2
                else -> 0
            },
        )
        if (stereo != null) GLES20.glUniform4f(uStereoRect, stereo.xPx, stereo.yPx, stereo.widthPx, stereo.heightPx)
        GLES20.glUniform1f(uEye, eye.toFloat())

        vertices.position(0)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, STRIDE, vertices)
        GLES20.glEnableVertexAttribArray(aPosition)
        vertices.position(3)
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, STRIDE, vertices)
        GLES20.glEnableVertexAttribArray(aTexCoord)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aTexCoord)
    }

    companion object {
        private const val STRIDE = 5 * 4

        /** Unit quad in the XY plane, normal facing +Z, so it faces a viewer at +Z. */
        private val QUAD = floatArrayOf(
            -0.5f, -0.5f, 0f, 0f, 0f,
            0.5f, -0.5f, 0f, 1f, 0f,
            -0.5f, 0.5f, 0f, 0f, 1f,
            0.5f, 0.5f, 0f, 1f, 1f,
        )

        private val VERTEX = """
            #version 300 es
            in vec3 aPosition;
            in vec2 aTexCoord;
            uniform mat4 uMvp;
            uniform mat4 uTexTransform;
            // Radians of head rotation that elapse across one full scanout,
            // as (yaw, pitch). Zero disables the correction.
            uniform vec2 uScanRotation;
            // Clip-space units per radian, from the projection's half-FoV.
            uniform vec2 uClipPerRadian;
            // The share of the texture shown, from its top: a layer cropped to
            // the band it is drawn in.
            uniform float uCropV;
            out vec2 vTexCoord;
            out highp vec2 vLocal;
            void main() {
                vec4 clip = uMvp * vec4(aPosition, 1.0);
                vLocal = aTexCoord;

                // The panel does not appear all at once: it is scanned out over a
                // whole frame, so the last row reaches the eye ~16.7ms after the
                // first while showing the same rendered pose. Against a
                // see-through view of the real world that difference is a shear,
                // not a lag. Offsetting each row by the rotation that will have
                // elapsed by the time it is actually lit cancels it.
                float v = (clip.y / clip.w + 1.0) * 0.5;      // 0 at first row, 1 at last
                clip.x += uScanRotation.x * v * uClipPerRadian.x * clip.w;
                clip.y += uScanRotation.y * v * uClipPerRadian.y * clip.w;

                gl_Position = clip;
                vec2 cropped = vec2(aTexCoord.x, 1.0 - (1.0 - aTexCoord.y) * uCropV);
                vTexCoord = (uTexTransform * vec4(cropped, 0.0, 1.0)).xy;
            }
        """.trimIndent()

        private val FRAGMENT = """
            #version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision mediump float;
            uniform samplerExternalOES uTexture;
            uniform vec2 uSizePx;
            // Where this quad sits in its window, in the window's pixels with y
            // down, so the corners are the window's even when it is drawn in parts.
            uniform vec4 uRectPx;
            uniform float uRadiusPx;
            uniform float uAlpha;
            uniform highp mat4 uTexTransform;
            // 0 none, 1 side by side, 2 top and bottom: inside uStereoRect, in
            // the window's pixels, each eye samples its own half stretched over
            // the whole rect. Only set for a part that covers the whole window.
            uniform int uStereo;
            uniform highp vec4 uStereoRect;
            uniform float uEye;
            // 2D to 3D: each eye samples the picture shifted by up to uDepthPx
            // window pixels, by uDepth (1 nearest, 0.5 at the window's own
            // distance, 0 farthest), in opposite directions per eye. Zero is off.
            uniform sampler2D uDepth;
            uniform float uDepthPx;
            uniform float uConvergence;
            in vec2 vTexCoord;
            in highp vec2 vLocal;
            out vec4 fragColor;
            // rounded-rect
            void main() {
                highp vec2 window = uRectPx.xy + vec2(vLocal.x, 1.0 - vLocal.y) * uRectPx.zw;
                float d = roundedRect(window - 0.5 * uSizePx, 0.5 * uSizePx, uRadiusPx);
                float coverage = clamp(0.5 - d / fwidth(d), 0.0, 1.0);
                highp vec2 texCoord = vTexCoord;
                if (uStereo != 0) {
                    highp vec2 local = (window - uStereoRect.xy) / uStereoRect.zw;
                    if (all(greaterThanEqual(local, vec2(0.0))) && all(lessThanEqual(local, vec2(1.0)))) {
                        if (uStereo == 1) local.x = 0.5 * (local.x + uEye);
                        else local.y = 0.5 * (local.y + uEye);
                        highp vec2 p = uStereoRect.xy + local * uStereoRect.zw;
                        texCoord = (uTexTransform * vec4(p.x / uSizePx.x, 1.0 - p.y / uSizePx.y, 0.0, 1.0)).xy;
                    }
                }
                if (uDepthPx > 0.0) {
                    // The left eye sees near things further right, so it reads
                    // from the left of where it draws. Looked up twice: the
                    // depth that matters is the one where the sample lands.
                    float side = uEye < 0.5 ? 1.0 : -1.0;
                    highp vec2 p = window;
                    for (int i = 0; i < 2; i++) {
                        float d = texture(uDepth, p / uSizePx).r;
                        p = vec2(window.x - side * (d - uConvergence) * 2.0 * uDepthPx, window.y);
                    }
                    p = clamp(p, vec2(0.5), uSizePx - 0.5);
                    texCoord = (uTexTransform * vec4(p.x / uSizePx.x, 1.0 - p.y / uSizePx.y, 0.0, 1.0)).xy;
                }
                fragColor = texture(uTexture, texCoord) * (coverage * uAlpha);
            }
        """.withRoundedRect()
    }
}

/**
 * Signed distance to a rounded rectangle centred on the origin, in whatever
 * units its arguments are in; negative inside. Shared by the panel mask and the
 * frame so that the frame follows the panel's corners exactly.
 */
private val ROUNDED_RECT = """
    float roundedRect(vec2 p, vec2 halfSize, float radius) {
        vec2 q = abs(p) - halfSize + radius;
        return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
    }
""".trimIndent()

private fun String.withRoundedRect() = trimIndent().replace("// rounded-rect", ROUNDED_RECT)

/**
 * A ring around a panel with rounded corners: [gapPx] clear of the panel's edge
 * and [thicknessPx] wide, in the panel's own pixels so it keeps its angle at any
 * distance. Colour is premultiplied, for blending over whatever is behind.
 */
internal class FrameQuad {
    private var program = 0
    private var aPosition = 0
    private var uMvp = 0
    private var uColor = 0
    private var uSizePx = 0
    private var uPanelPx = 0
    private var uRadiusPx = 0
    private var uRingPx = 0
    private lateinit var vertices: FloatBuffer

    fun initialise() {
        program = Gl.program(VERTEX, FRAGMENT)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uColor = GLES20.glGetUniformLocation(program, "uColor")
        uSizePx = GLES20.glGetUniformLocation(program, "uSizePx")
        uPanelPx = GLES20.glGetUniformLocation(program, "uPanelPx")
        uRadiusPx = GLES20.glGetUniformLocation(program, "uRadiusPx")
        uRingPx = GLES20.glGetUniformLocation(program, "uRingPx")
        vertices = Gl.floatBuffer(QUAD)
    }

    /** [mvp] maps the unit quad onto the panel grown by [marginPx] on every side. */
    fun draw(
        mvp: FloatArray,
        panelWidthPx: Float,
        panelHeightPx: Float,
        marginPx: Float,
        radiusPx: Float,
        gapPx: Float,
        thicknessPx: Float,
        r: Float,
        g: Float,
        b: Float,
        a: Float = 1f,
    ) {
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniform4f(uColor, r * a, g * a, b * a, a)
        GLES20.glUniform2f(uSizePx, panelWidthPx + 2f * marginPx, panelHeightPx + 2f * marginPx)
        GLES20.glUniform2f(uPanelPx, panelWidthPx, panelHeightPx)
        GLES20.glUniform1f(uRadiusPx, radiusPx)
        GLES20.glUniform2f(uRingPx, gapPx, gapPx + thicknessPx)

        vertices.position(0)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 3 * 4, vertices)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPosition)
    }

    companion object {
        private val QUAD = floatArrayOf(
            -0.5f, -0.5f, 0f,
            0.5f, -0.5f, 0f,
            -0.5f, 0.5f, 0f,
            0.5f, 0.5f, 0f,
        )

        private val VERTEX = """
            #version 300 es
            in vec3 aPosition;
            uniform mat4 uMvp;
            out vec2 vLocal;
            void main() {
                vLocal = aPosition.xy;
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
        """.trimIndent()

        private val FRAGMENT = """
            #version 300 es
            precision mediump float;
            uniform vec4 uColor;
            uniform vec2 uSizePx;
            uniform vec2 uPanelPx;
            uniform float uRadiusPx;
            uniform vec2 uRingPx;
            in vec2 vLocal;
            out vec4 fragColor;
            // rounded-rect
            void main() {
                float d = roundedRect(vLocal * uSizePx, 0.5 * uPanelPx, uRadiusPx);
                float w = fwidth(d);
                float inner = clamp((d - uRingPx.x) / w + 0.5, 0.0, 1.0);
                float outer = clamp((uRingPx.y - d) / w + 0.5, 0.0, 1.0);
                fragColor = uColor * min(inner, outer);
            }
        """.withRoundedRect()
    }
}

/** A quad showing an ordinary 2D texture with premultiplied alpha, such as a painted bitmap. */
internal class BitmapQuad {
    private var program = 0
    private var aPosition = 0
    private var aTexCoord = 0
    private var uMvp = 0
    private var uAlpha = 0
    private lateinit var vertices: FloatBuffer

    fun initialise() {
        program = Gl.program(VERTEX, FRAGMENT)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uAlpha = GLES20.glGetUniformLocation(program, "uAlpha")
        vertices = Gl.floatBuffer(QUAD)
    }

    fun draw(mvp: FloatArray, textureId: Int, alpha: Float = 1f) {
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniform1f(uAlpha, alpha)

        vertices.position(0)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, STRIDE, vertices)
        GLES20.glEnableVertexAttribArray(aPosition)
        vertices.position(3)
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, STRIDE, vertices)
        GLES20.glEnableVertexAttribArray(aTexCoord)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aTexCoord)
    }

    companion object {
        private const val STRIDE = 5 * 4

        /** Bitmap rows run top down, so the top of the quad samples row zero. */
        private val QUAD = floatArrayOf(
            -0.5f, -0.5f, 0f, 0f, 1f,
            0.5f, -0.5f, 0f, 1f, 1f,
            -0.5f, 0.5f, 0f, 0f, 0f,
            0.5f, 0.5f, 0f, 1f, 0f,
        )

        private val VERTEX = """
            #version 300 es
            in vec3 aPosition;
            in vec2 aTexCoord;
            uniform mat4 uMvp;
            out vec2 vTexCoord;
            void main() {
                vTexCoord = aTexCoord;
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
        """.trimIndent()

        private val FRAGMENT = """
            #version 300 es
            precision mediump float;
            uniform sampler2D uTexture;
            uniform float uAlpha;
            in vec2 vTexCoord;
            out vec4 fragColor;
            void main() { fragColor = texture(uTexture, vTexCoord) * uAlpha; }
        """.trimIndent()
    }
}

/**
 * A resize handle: an arc concentric with a window's rounded corner, carried
 * on along both edges. Drawn for the top-left corner, with the quad's own
 * coordinates running from [extentPx].x outside the corner to .y inside it;
 * the model matrix mirrors it into the other corners.
 */
internal class ArcQuad {
    private var program = 0
    private var aPosition = 0
    private var uMvp = 0
    private var uColor = 0
    private var uExtent = 0
    private var uRadius = 0
    private var uRing = 0
    private var uThickness = 0
    private lateinit var vertices: FloatBuffer

    fun initialise() {
        program = Gl.program(VERTEX, FRAGMENT)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uColor = GLES20.glGetUniformLocation(program, "uColor")
        uExtent = GLES20.glGetUniformLocation(program, "uExtent")
        uRadius = GLES20.glGetUniformLocation(program, "uRadius")
        uRing = GLES20.glGetUniformLocation(program, "uRing")
        uThickness = GLES20.glGetUniformLocation(program, "uThickness")
        vertices = Gl.floatBuffer(QUAD)
    }

    fun draw(
        mvp: FloatArray,
        outsidePx: Float,
        insidePx: Float,
        cornerRadiusPx: Float,
        gapPx: Float,
        thicknessPx: Float,
        alpha: Float,
    ) {
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniform4f(uColor, alpha, alpha, alpha, alpha)
        GLES20.glUniform2f(uExtent, outsidePx, insidePx)
        GLES20.glUniform1f(uRadius, cornerRadiusPx)
        GLES20.glUniform1f(uRing, cornerRadiusPx + gapPx)
        GLES20.glUniform1f(uThickness, thicknessPx)
        vertices.position(0)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 3 * 4, vertices)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPosition)
    }

    companion object {
        private val QUAD = floatArrayOf(
            -0.5f, -0.5f, 0f,
            0.5f, -0.5f, 0f,
            -0.5f, 0.5f, 0f,
            0.5f, 0.5f, 0f,
        )

        private val VERTEX = """
            #version 300 es
            in vec3 aPosition;
            uniform mat4 uMvp;
            out vec2 vLocal;
            void main() {
                vLocal = aPosition.xy;
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
        """.trimIndent()

        private val FRAGMENT = """
            #version 300 es
            precision mediump float;
            uniform vec4 uColor;
            uniform vec2 uExtent;
            uniform float uRadius;
            uniform float uRing;
            uniform float uThickness;
            in vec2 vLocal;
            out vec4 fragColor;
            void main() {
                // Pixels from the window's corner, x and y both growing into the window.
                vec2 q = vec2(mix(-uExtent.x, uExtent.y, vLocal.x + 0.5), mix(-uExtent.x, uExtent.y, 0.5 - vLocal.y));
                float edge = uRadius - uRing;
                float d;
                if (q.x < uRadius && q.y < uRadius) d = abs(length(q - vec2(uRadius)) - uRing);
                else if (q.y < uRadius) d = abs(q.y - edge);
                else if (q.x < uRadius) d = abs(q.x - edge);
                else d = 1000.0;
                float w = fwidth(d);
                fragColor = uColor * clamp((0.5 * uThickness - d) / w + 0.5, 0.0, 1.0);
            }
        """.trimIndent()
    }
}

/** Flat-coloured quad. */
internal class SolidQuad {
    private var program = 0
    private var aPosition = 0
    private var uMvp = 0
    private var uColor = 0
    private lateinit var vertices: FloatBuffer

    fun initialise() {
        program = Gl.program(VERTEX, FRAGMENT)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uColor = GLES20.glGetUniformLocation(program, "uColor")
        vertices = Gl.floatBuffer(QUAD)
    }

    fun draw(mvp: FloatArray, r: Float, g: Float, b: Float, a: Float = 1f) {
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniform4f(uColor, r, g, b, a)

        vertices.position(0)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 3 * 4, vertices)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPosition)
    }

    companion object {
        private val QUAD = floatArrayOf(
            -0.5f, -0.5f, 0f,
            0.5f, -0.5f, 0f,
            -0.5f, 0.5f, 0f,
            0.5f, 0.5f, 0f,
        )

        private val VERTEX = """
            #version 300 es
            in vec3 aPosition;
            uniform mat4 uMvp;
            void main() { gl_Position = uMvp * vec4(aPosition, 1.0); }
        """.trimIndent()

        private val FRAGMENT = """
            #version 300 es
            precision mediump float;
            uniform vec4 uColor;
            out vec4 fragColor;
            void main() { fragColor = uColor; }
        """.trimIndent()
    }
}

/**
 * A ribbon between two points in the world, turned to face the viewer, its
 * colour fading from one end to the other: the gyro pointer's beam.
 */
internal class BeamQuad {
    private var program = 0
    private var aPosition = 0
    private var aAlpha = 0
    private var uMvp = 0
    private var uColor = 0
    private val corners = FloatArray(4 * 4)
    private lateinit var vertices: FloatBuffer

    fun initialise() {
        program = Gl.program(VERTEX, FRAGMENT)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aAlpha = GLES20.glGetAttribLocation(program, "aAlpha")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uColor = GLES20.glGetUniformLocation(program, "uColor")
        vertices = Gl.floatBuffer(corners)
    }

    /**
     * From [from] to [to], world points around a viewer at the origin, [fromWidth]
     * and [toWidth] wide in metres, [fromAlpha] and [toAlpha] opaque. Expects
     * premultiplied blending.
     */
    fun draw(
        viewProjection: FloatArray,
        from: Vec3,
        to: Vec3,
        fromWidth: Float,
        toWidth: Float,
        fromAlpha: Float,
        toAlpha: Float,
        r: Float,
        g: Float,
        b: Float,
    ) {
        val along = to - from
        val middle = (from + to) * 0.5f
        var side = along.cross(middle)
        if (side.length < 1e-9f) return
        side = side * (1f / side.length)
        val a = side * (fromWidth / 2f)
        val c = side * (toWidth / 2f)
        put(0, from - a, fromAlpha)
        put(1, from + a, fromAlpha)
        put(2, to - c, toAlpha)
        put(3, to + c, toAlpha)
        vertices.position(0)
        vertices.put(corners)

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, viewProjection, 0)
        GLES20.glUniform3f(uColor, r, g, b)
        vertices.position(0)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, STRIDE, vertices)
        GLES20.glEnableVertexAttribArray(aPosition)
        vertices.position(3)
        GLES20.glVertexAttribPointer(aAlpha, 1, GLES20.GL_FLOAT, false, STRIDE, vertices)
        GLES20.glEnableVertexAttribArray(aAlpha)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aAlpha)
    }

    private fun put(index: Int, point: Vec3, alpha: Float) {
        corners[index * 4] = point.x
        corners[index * 4 + 1] = point.y
        corners[index * 4 + 2] = point.z
        corners[index * 4 + 3] = alpha
    }

    companion object {
        private const val STRIDE = 4 * 4

        private val VERTEX = """
            #version 300 es
            in vec3 aPosition;
            in float aAlpha;
            uniform mat4 uMvp;
            out float vAlpha;
            void main() {
                vAlpha = aAlpha;
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
        """.trimIndent()

        private val FRAGMENT = """
            #version 300 es
            precision mediump float;
            uniform vec3 uColor;
            in float vAlpha;
            out vec4 fragColor;
            void main() { fragColor = vec4(uColor * vAlpha, vAlpha); }
        """.trimIndent()
    }
}
