package dev.halone.compositor.gl

import android.opengl.GLES20
import android.opengl.Matrix
import dev.halone.compositor.ImmersiveGeometry
import dev.halone.compositor.ImmersiveLayer
import dev.halone.compositor.MediaProjection
import dev.halone.compositor.StereoLayout
import java.nio.FloatBuffer

/**
 * Draws an [ImmersiveLayer] over a whole eye by casting each fragment's ray
 * into it, so a sphere needs no mesh and a screen no quad. Rays come from the
 * inverse of the eye's view-projection: their directions alone pick a point
 * on a sphere, which is at infinity, while a screen is intersected from the
 * eye's own position so that each eye sees it at its true distance.
 */
internal class ImmersivePass {
    private var program = 0
    private var aPosition = 0
    private var uInverse = 0
    private var uTexTransform = 0
    private var uAnchor = 0
    private var uProjection = 0
    private var uEyeRect = 0
    private var uScreenHalfWidth = 0
    private var uScreenDistance = 0
    private var uEyeAspect = 0
    private var uForward = 0
    private var uSphereZoom = 0
    private var uEyePosition = 0
    private var uSphereRadius = 0
    private lateinit var vertices: FloatBuffer
    private val inverse = FloatArray(16)

    fun initialise() {
        program = Gl.program(VERTEX, FRAGMENT)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        uInverse = GLES20.glGetUniformLocation(program, "uInverse")
        uTexTransform = GLES20.glGetUniformLocation(program, "uTexTransform")
        uAnchor = GLES20.glGetUniformLocation(program, "uAnchor")
        uProjection = GLES20.glGetUniformLocation(program, "uProjection")
        uEyeRect = GLES20.glGetUniformLocation(program, "uEyeRect")
        uScreenHalfWidth = GLES20.glGetUniformLocation(program, "uScreenHalfWidth")
        uScreenDistance = GLES20.glGetUniformLocation(program, "uScreenDistance")
        uEyeAspect = GLES20.glGetUniformLocation(program, "uEyeAspect")
        uForward = GLES20.glGetUniformLocation(program, "uForward")
        uSphereZoom = GLES20.glGetUniformLocation(program, "uSphereZoom")
        uEyePosition = GLES20.glGetUniformLocation(program, "uEyePosition")
        uSphereRadius = GLES20.glGetUniformLocation(program, "uSphereRadius")
        vertices = Gl.floatBuffer(floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f))
    }

    /**
     * [headForward] is the world direction the head faces, which a sphere's
     * zoom is centred on for both eyes; [eyePosition] is this eye's, in the
     * world, with the head's centre at the origin.
     */
    fun draw(
        layer: ImmersiveLayer,
        textureId: Int,
        texTransform: FloatArray,
        viewProjection: FloatArray,
        headForward: FloatArray,
        eyePosition: FloatArray,
        eye: Int,
    ) {
        if (!Matrix.invertM(inverse, 0, viewProjection, 0)) return
        val distance = ImmersiveGeometry.SCREEN_DISTANCE_METRES
        val halfWidth = distance * Math.tan(Math.toRadians(layer.screenWidthDegrees / 2.0)).toFloat()
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(Gl.TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniformMatrix4fv(uInverse, 1, false, inverse, 0)
        GLES20.glUniformMatrix4fv(uTexTransform, 1, false, texTransform, 0)
        GLES20.glUniform1f(uAnchor, layer.anchorYawRadians)
        GLES20.glUniform1i(
            uProjection,
            when (layer.projection) {
                MediaProjection.FLAT -> 0
                MediaProjection.EQUIRECT_180 -> 1
                MediaProjection.EQUIRECT_360 -> 2
            },
        )
        GLES20.glUniform4fv(uEyeRect, 1, ImmersiveGeometry.eyeRect(layer.stereo, eye), 0)
        GLES20.glUniform1f(uScreenHalfWidth, halfWidth)
        GLES20.glUniform1f(uScreenDistance, distance)
        GLES20.glUniform1f(uEyeAspect, ImmersiveGeometry.eyeAspect(layer.videoWidthPx, layer.videoHeightPx, layer.stereo))
        GLES20.glUniform3fv(uForward, 1, headForward, 0)
        GLES20.glUniform1f(uSphereZoom, layer.sphereZoom)
        GLES20.glUniform3fv(uEyePosition, 1, eyePosition, 0)
        GLES20.glUniform1f(uSphereRadius, if (layer.stereo == StereoLayout.MONO) layer.sphereDistanceMetres else 0f)
        vertices.position(0)
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
        GLES20.glDisableVertexAttribArray(aPosition)
    }

    companion object {
        private val VERTEX = """
            #version 300 es
            in vec2 aPosition;
            out vec2 vNdc;
            void main() {
                vNdc = aPosition;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """.trimIndent()

        private val FRAGMENT = """
            #version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision highp float;
            uniform samplerExternalOES uTexture;
            uniform mat4 uInverse;
            uniform mat4 uTexTransform;
            uniform float uAnchor;
            // 0 a flat screen, 1 a half sphere, 2 a whole one.
            uniform int uProjection;
            // The eye's part of the frame: u, v, width, height, with v down.
            uniform vec4 uEyeRect;
            uniform float uScreenHalfWidth;
            uniform float uScreenDistance;
            uniform float uEyeAspect;
            uniform vec3 uForward;
            uniform float uSphereZoom;
            uniform vec3 uEyePosition;
            // 0 for a sphere at infinity.
            uniform float uSphereRadius;
            in vec2 vNdc;
            out vec4 fragColor;
            const float PI = 3.14159265;

            // Into the layer's frame, whose forward is the world's turned by the anchor.
            vec3 toLayer(vec3 v) {
                float c = cos(uAnchor);
                float s = sin(uAnchor);
                return vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
            }

            vec3 widen(vec3 d, vec3 f) {
                float c = clamp(dot(d, f), -1.0, 1.0);
                vec3 side = d - f * c;
                float s = length(side);
                if (s < 1e-6) return d;
                float angle = min(acos(c) / uSphereZoom, PI);
                return f * cos(angle) + side / s * sin(angle);
            }

            void main() {
                vec4 nearPoint = uInverse * vec4(vNdc, -1.0, 1.0);
                vec4 farPoint = uInverse * vec4(vNdc, 1.0, 1.0);
                vec3 origin = toLayer(nearPoint.xyz / nearPoint.w);
                vec3 dir = normalize(toLayer(farPoint.xyz / farPoint.w) - origin);
                vec2 uv;
                if (uProjection == 0) {
                    if (dir.z >= 0.0) { fragColor = vec4(0.0); return; }
                    vec3 hit = origin + dir * ((-uScreenDistance - origin.z) / dir.z);
                    uv = vec2(0.5 + hit.x / (2.0 * uScreenHalfWidth), 0.5 - hit.y * uEyeAspect / (2.0 * uScreenHalfWidth));
                    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) { fragColor = vec4(0.0); return; }
                } else {
                    if (uSphereZoom < 1.0) dir = widen(dir, normalize(toLayer(uForward)));
                    if (uSphereRadius > 0.0) {
                        vec3 eye = toLayer(uEyePosition);
                        float b = dot(eye, dir);
                        float t = -b + sqrt(b * b - dot(eye, eye) + uSphereRadius * uSphereRadius);
                        dir = normalize(eye + dir * t);
                    }
                    float longitude = atan(dir.x, -dir.z);
                    float latitude = asin(clamp(dir.y, -1.0, 1.0));
                    if (uProjection == 1) {
                        if (abs(longitude) > 0.5 * PI) { fragColor = vec4(0.0); return; }
                        uv = vec2(0.5 + longitude / PI, 0.5 - latitude / PI);
                    } else {
                        uv = vec2(0.5 + longitude / (2.0 * PI), 0.5 - latitude / PI);
                    }
                }
                vec2 image = uEyeRect.xy + uv * uEyeRect.zw;
                vec2 texCoord = (uTexTransform * vec4(image.x, 1.0 - image.y, 0.0, 1.0)).xy;
                fragColor = vec4(texture(uTexture, texCoord).rgb, 1.0);
            }
        """.trimIndent()
    }
}
