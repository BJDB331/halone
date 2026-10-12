package dev.halone.glasses.drivers.xreal

import dev.halone.glasses.api.AxisConvention
import dev.halone.glasses.api.TrackingSample
import dev.halone.glasses.math.Vec3

internal sealed interface XrealPacket {
    data class Sensor(val sample: TrackingSample.Inertial) : XrealPacket
    data class CommandReply(val msgId: Int, val raw: ByteArray) : XrealPacket
    data class Unrecognised(val reason: String) : XrealPacket
}

/**
 * Parses the 64-byte sensor frame. Scaling is carried in the packet itself
 * (multiplier/divisor per sensor), so nothing here is hardcoded per firmware.
 */
internal object XrealSensorPacket {
    const val SIZE = 64

    private const val OFF_TEMPERATURE = 2
    private const val OFF_TIMESTAMP = 4
    private const val OFF_GYRO_MULT = 12
    private const val OFF_GYRO_DIV = 14
    private const val OFF_GYRO_X = 18
    private const val OFF_ACCEL_MULT = 27
    private const val OFF_ACCEL_DIV = 29
    private const val OFF_ACCEL_X = 33
    private const val OFF_MAG_MULT = 42
    private const val OFF_MAG_DIV = 44
    private const val OFF_MAG_X = 48

    private const val GRAVITY = 9.81f
    private val DEG_TO_RAD = (Math.PI / 180.0).toFloat()

    fun parse(buf: ByteArray, length: Int, axes: AxisConvention): XrealPacket {
        if (length < SIZE) return XrealPacket.Unrecognised("short packet ($length bytes)")

        if (buf[0] == XrealProtocol.IMU_HEAD) {
            return XrealPacket.CommandReply(buf[7].toInt() and 0xFF, buf)
        }

        val s0 = buf[0].toInt() and 0xFF
        val s1 = buf[1].toInt() and 0xFF
        if (s0 != 0x01 || s1 != 0x02) {
            return XrealPacket.Unrecognised("signature %02x %02x".format(s0, s1))
        }

        val gyroScale = scale(buf, OFF_GYRO_MULT, OFF_GYRO_DIV) * DEG_TO_RAD
        val accelScale = scale(buf, OFF_ACCEL_MULT, OFF_ACCEL_DIV) * GRAVITY

        return XrealPacket.Sensor(
            TrackingSample.Inertial(
                timestampNs = u64(buf, OFF_TIMESTAMP),
                gyro = axes.apply(vec24(buf, OFF_GYRO_X, gyroScale)),
                accel = axes.apply(vec24(buf, OFF_ACCEL_X, accelScale)),
                magnet = null,
                magnetRaw = axes.apply(rawMagnetCounts(buf)),
            )
        )
    }

    /** Unsigned and biased at 0x8000, unlike the two's-complement gyro and accel. */
    fun rawMagnetCounts(buf: ByteArray): Vec3 = Vec3(
        (u16(buf, OFF_MAG_X) - 0x8000).toFloat(),
        (u16(buf, OFF_MAG_X + 2) - 0x8000).toFloat(),
        (u16(buf, OFF_MAG_X + 4) - 0x8000).toFloat(),
    )

    fun temperatureRaw(buf: ByteArray): Int = u16(buf, OFF_TEMPERATURE)

    private fun scale(b: ByteArray, multAt: Int, divAt: Int): Float {
        val mult = u16(b, multAt)
        val div = u32(b, divAt)
        return if (div == 0L) 0f else mult.toFloat() / div.toFloat()
    }

    private fun vec24(b: ByteArray, at: Int, scale: Float) =
        Vec3(i24(b, at) * scale, i24(b, at + 3) * scale, i24(b, at + 6) * scale)

    private fun u16(b: ByteArray, at: Int) =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun u32(b: ByteArray, at: Int): Long {
        var v = 0L
        for (i in 0 until 4) v = v or ((b[at + i].toLong() and 0xFF) shl (8 * i))
        return v
    }

    private fun u64(b: ByteArray, at: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = v or ((b[at + i].toLong() and 0xFF) shl (8 * i))
        return v
    }

    private fun i24(b: ByteArray, at: Int): Int {
        val raw = (b[at].toInt() and 0xFF) or
            ((b[at + 1].toInt() and 0xFF) shl 8) or
            ((b[at + 2].toInt() and 0xFF) shl 16)
        return if (raw and 0x800000 != 0) raw or 0xFF000000.toInt() else raw
    }
}
