package dev.halone.glasses.drivers.xrealone

import dev.halone.glasses.api.AxisConvention
import dev.halone.glasses.api.TrackingSample
import dev.halone.glasses.math.Vec3

/**
 * The 128-byte report payload on port 52998. IMU reports arrive at 1 kHz and
 * magnetometer reports at 400 Hz, each with the other's fields set to NaN.
 */
internal object XrealOneReport {
    const val SIZE = 128

    const val TYPE_MAGNETOMETER = 0x04
    const val TYPE_IMU = 0x0B

    private const val OFF_TIMESTAMP = 0x08
    private const val OFF_TYPE = 0x18
    private const val OFF_GYRO = 0x1C
    private const val OFF_ACCEL = 0x28
    private const val OFF_FRAME = 0x45

    fun type(b: ByteArray, at: Int): Int = u32le(b, at + OFF_TYPE).toInt()

    fun frameCounter(b: ByteArray, at: Int): Int =
        (b[at + OFF_FRAME].toInt() and 0xFF) or
            ((b[at + OFF_FRAME + 1].toInt() and 0xFF) shl 8) or
            ((b[at + OFF_FRAME + 2].toInt() and 0xFF) shl 16)

    fun timestampNs(b: ByteArray, at: Int): Long = u64le(b, at + OFF_TIMESTAMP)

    /** Null unless this is a finite IMU report. */
    fun inertial(b: ByteArray, at: Int, axes: AxisConvention): TrackingSample.Inertial? {
        if (type(b, at) != TYPE_IMU) return null
        val gyro = vec3(b, at + OFF_GYRO) ?: return null
        val accel = vec3(b, at + OFF_ACCEL) ?: return null
        return TrackingSample.Inertial(
            timestampNs = timestampNs(b, at),
            gyro = axes.apply(gyro),
            accel = axes.apply(accel),
        )
    }

    private fun vec3(b: ByteArray, at: Int): Vec3? {
        val x = f32le(b, at)
        val y = f32le(b, at + 4)
        val z = f32le(b, at + 8)
        return if (x.isFinite() && y.isFinite() && z.isFinite()) Vec3(x, y, z) else null
    }

    private fun f32le(b: ByteArray, at: Int) = Float.fromBits(u32le(b, at).toInt())

    private fun u32le(b: ByteArray, at: Int): Long {
        var v = 0L
        for (i in 0 until 4) v = v or ((b[at + i].toLong() and 0xFF) shl (8 * i))
        return v
    }

    private fun u64le(b: ByteArray, at: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = v or ((b[at + i].toLong() and 0xFF) shl (8 * i))
        return v
    }
}
