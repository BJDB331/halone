package dev.halone.testkit.ab

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import dev.halone.glasses.math.Quaternion
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

/**
 * A world-locked cylinder of lines around the wearer, a brighter horizon and a
 * cross straight ahead, drawn green on black. Black shows as clear on these
 * optics, so the lines sit over the real room and any slide or tilt against it
 * is easy to see.
 */
class SceneRenderer(private val view: HeadView) : GLSurfaceView.Renderer {

    @Volatile
    var params: SceneParams? = null

    private var program = 0
    private var aPosition = 0
    private var uMatrix = 0
    private var uColor = 0
    private lateinit var grid: FloatBuffer
    private lateinit var horizon: FloatBuffer
    private lateinit var cross: FloatBuffer
    private var aspect = 16f / 9f

    private val projection = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val mvp = FloatArray(16)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = link(VERTEX, FRAGMENT)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        uMatrix = GLES20.glGetUniformLocation(program, "uMatrix")
        uColor = GLES20.glGetUniformLocation(program, "uColor")
        grid = buffer(gridLines())
        horizon = buffer(ring(0f))
        cross = buffer(floatArrayOf(-0.15f, 0f, -RADIUS, 0.15f, 0f, -RADIUS, 0f, -0.15f, -RADIUS, 0f, 0.15f, -RADIUS))
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspect = width.toFloat() / height
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val p = params ?: return

        Matrix.perspectiveM(projection, 0, p.verticalFovDegrees.toFloat(), aspect, 0.1f, 100f)
        toViewMatrix(view.head(p).conjugate(), viewMatrix)
        Matrix.multiplyMM(mvp, 0, projection, 0, viewMatrix, 0)

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMatrix, 1, false, mvp, 0)
        GLES20.glLineWidth(3f)
        draw(grid, 0.1f, 0.6f, 0.2f)
        GLES20.glLineWidth(5f)
        draw(horizon, 0.3f, 1f, 0.4f)
        draw(cross, 1f, 1f, 1f)
    }

    private fun draw(lines: FloatBuffer, r: Float, g: Float, b: Float) {
        GLES20.glUniform4f(uColor, r, g, b, 1f)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 0, lines)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, lines.capacity() / 3)
    }

    private fun gridLines(): FloatArray {
        val out = ArrayList<Float>()
        for (i in 0 until 360 step 15) {
            val a = Math.toRadians(i.toDouble()).toFloat()
            val x = RADIUS * sin(a)
            val z = -RADIUS * cos(a)
            out += listOf(x, -1.5f, z, x, 1.5f, z)
        }
        for (y in listOf(-1f, -0.5f, 0.5f, 1f)) out += ring(y).toList()
        return out.toFloatArray()
    }

    private fun ring(y: Float): FloatArray {
        val out = ArrayList<Float>()
        val steps = 72
        for (i in 0 until steps) {
            for (j in listOf(i, i + 1)) {
                val a = (2 * Math.PI * j / steps).toFloat()
                out += listOf(RADIUS * sin(a), y, -RADIUS * cos(a))
            }
        }
        return out.toFloatArray()
    }

    private fun buffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(values)
            position(0)
        }

    private fun toViewMatrix(q: Quaternion, out: FloatArray) {
        val (w, x, y, z) = listOf(q.w, q.x, q.y, q.z)
        out[0] = 1 - 2 * (y * y + z * z); out[4] = 2 * (x * y - w * z); out[8] = 2 * (x * z + w * y); out[12] = 0f
        out[1] = 2 * (x * y + w * z); out[5] = 1 - 2 * (x * x + z * z); out[9] = 2 * (y * z - w * x); out[13] = 0f
        out[2] = 2 * (x * z - w * y); out[6] = 2 * (y * z + w * x); out[10] = 1 - 2 * (x * x + y * y); out[14] = 0f
        out[3] = 0f; out[7] = 0f; out[11] = 0f; out[15] = 1f
    }

    private fun link(vertex: String, fragment: String): Int {
        fun compile(type: Int, source: String) = GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, source)
            GLES20.glCompileShader(it)
        }
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, compile(GLES20.GL_VERTEX_SHADER, vertex))
            GLES20.glAttachShader(it, compile(GLES20.GL_FRAGMENT_SHADER, fragment))
            GLES20.glLinkProgram(it)
        }
    }

    private companion object {
        const val RADIUS = 3f
        const val VERTEX = "uniform mat4 uMatrix; attribute vec3 aPosition; void main() { gl_Position = uMatrix * vec4(aPosition, 1.0); }"
        const val FRAGMENT = "precision mediump float; uniform vec4 uColor; void main() { gl_FragColor = uColor; }"
    }
}
