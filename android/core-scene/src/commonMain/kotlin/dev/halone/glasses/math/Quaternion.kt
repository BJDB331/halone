package dev.halone.glasses.math

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

data class Quaternion(val w: Float, val x: Float, val y: Float, val z: Float) {
    companion object {
        val IDENTITY = Quaternion(1f, 0f, 0f, 0f)

        /** Rotation of [degrees] about the canonical up axis (+Y). */
        fun fromYawRadians(radians: Float): Quaternion {
            if (radians == 0f) return IDENTITY
            val half = radians * 0.5f
            return Quaternion(kotlin.math.cos(half), 0f, kotlin.math.sin(half), 0f)
        }

        /** Rotation about +X, used to cancel the IMU's mounting pitch. */
        fun fromPitchDegrees(degrees: Float): Quaternion {
            if (degrees == 0f) return IDENTITY
            val half = toRadians(degrees.toDouble()).toFloat() * 0.5f
            return Quaternion(kotlin.math.cos(half), kotlin.math.sin(half), 0f, 0f)
        }
    }

    operator fun times(o: Quaternion) = Quaternion(
        w * o.w - x * o.x - y * o.y - z * o.z,
        w * o.x + x * o.w + y * o.z - z * o.y,
        w * o.y - x * o.z + y * o.w + z * o.x,
        w * o.z + x * o.y - y * o.x + z * o.w,
    )

    fun normalized(): Quaternion {
        val n = sqrt(w * w + x * x + y * y + z * z)
        return if (n < 1e-9f) IDENTITY else Quaternion(w / n, x / n, y / n, z / n)
    }

    fun conjugate() = Quaternion(w, -x, -y, -z)

    fun dot(o: Quaternion) = w * o.w + x * o.x + y * o.y + z * o.z

    /** The same rotation, expressed with the opposite sign. */
    fun negated() = Quaternion(-w, -x, -y, -z)

    /**
     * Rotates [v] by this quaternion — body frame to world frame, for a
     * quaternion that represents the body's orientation in the world.
     */
    fun rotate(v: Vec3): Vec3 {
        val t = Vec3(
            2f * (y * v.z - z * v.y),
            2f * (z * v.x - x * v.z),
            2f * (x * v.y - y * v.x),
        )
        return Vec3(
            v.x + w * t.x + (y * t.z - z * t.y),
            v.y + w * t.y + (z * t.x - x * t.z),
            v.z + w * t.z + (x * t.y - y * t.x),
        )
    }

    /**
     * Yaw, pitch and roll in degrees, in the canonical +Y-up device frame:
     * yaw about +Y, pitch about +X, roll about +Z, extracted in Y-X-Z order.
     *
     * Must stay consistent with [dev.halone.glasses.api.AxisConvention] — if the
     * frame says Y is up, yaw cannot be a rotation about Z.
     */
    fun toEulerDegrees(): Triple<Float, Float, Float> {
        val m12 = 2f * (x * y - w * z)
        val m22 = 1f - 2f * (x * x + z * z)
        val m31 = 2f * (x * z - w * y)
        val m32 = 2f * (y * z + w * x)
        val m33 = 1f - 2f * (x * x + y * y)
        val m11 = 1f - 2f * (y * y + z * z)
        val m13 = 2f * (x * z + w * y)

        val sinPitch = m32.coerceIn(-1f, 1f)
        val pitch = toDegrees(asin(sinPitch.toDouble())).toFloat()

        // Near a vertical pitch, yaw and roll collapse onto the same axis.
        return if (abs(sinPitch) < 0.9999999f) {
            Triple(
                toDegrees(atan2(-m31.toDouble(), m33.toDouble())).toFloat(),
                pitch,
                toDegrees(atan2(-m12.toDouble(), m22.toDouble())).toFloat(),
            )
        } else {
            Triple(
                toDegrees(atan2(m13.toDouble(), m11.toDouble())).toFloat(),
                pitch,
                0f,
            )
        }
    }
}
