package dev.halone.testkit.profiles

import dev.halone.testkit.capture.ImuListener

/**
 * The minimum of the XREAL One report stream needed on the phone: framing and
 * the gyro and accelerometer of `0x0B` reports, in the hardware's own frame.
 * Everything else is left to whoever analyses the capture. See
 * `docs/protocol/xreal-one.md`.
 */
class XrealOneStream(private val listener: ImuListener) {

    private var buffer = ByteArray(64 * 1024)
    private var end = 0

    var reports = 0L
        private set

    fun feed(bytes: ByteArray, length: Int) {
        if (end + length > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, end + length))
        System.arraycopy(bytes, 0, buffer, end, length)
        end += length

        var at = 0
        while (end - at >= HEADER) {
            val magic = ((buffer[at].toInt() and 0xFF) shl 8) or (buffer[at + 1].toInt() and 0xFF)
            val size = u32be(at + 2)
            if (size > MAX_PAYLOAD) {
                at++
                continue
            }
            if (end - at < HEADER + size) break
            if (magic == MAGIC_REPORT && size == REPORT_SIZE.toLong()) onReport(at + HEADER)
            at += HEADER + size.toInt()
        }
        System.arraycopy(buffer, at, buffer, 0, end - at)
        end -= at
    }

    private fun onReport(at: Int) {
        reports++
        if (u32le(at + OFF_TYPE).toInt() != TYPE_IMU) return
        val gx = f32(at + OFF_GYRO)
        val gy = f32(at + OFF_GYRO + 4)
        val gz = f32(at + OFF_GYRO + 8)
        val ax = f32(at + OFF_ACCEL)
        val ay = f32(at + OFF_ACCEL + 4)
        val az = f32(at + OFF_ACCEL + 8)
        if (gx.isFinite() && gy.isFinite() && gz.isFinite() && ax.isFinite() && ay.isFinite() && az.isFinite()) {
            listener.onImu(u64le(at + OFF_TIMESTAMP), gx, gy, gz, ax, ay, az)
        }
    }

    private fun f32(at: Int) = Float.fromBits(u32le(at).toInt())

    private fun u32be(at: Int): Long =
        ((buffer[at].toLong() and 0xFF) shl 24) or ((buffer[at + 1].toLong() and 0xFF) shl 16) or
            ((buffer[at + 2].toLong() and 0xFF) shl 8) or (buffer[at + 3].toLong() and 0xFF)

    private fun u32le(at: Int): Long {
        var v = 0L
        for (i in 0 until 4) v = v or ((buffer[at + i].toLong() and 0xFF) shl (8 * i))
        return v
    }

    private fun u64le(at: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = v or ((buffer[at + i].toLong() and 0xFF) shl (8 * i))
        return v
    }

    private companion object {
        const val HEADER = 6
        const val MAGIC_REPORT = 0x2836
        const val REPORT_SIZE = 128
        const val MAX_PAYLOAD = 4096L
        const val TYPE_IMU = 0x0B
        const val OFF_TIMESTAMP = 0x08
        const val OFF_TYPE = 0x18
        const val OFF_GYRO = 0x1C
        const val OFF_ACCEL = 0x28
    }
}
