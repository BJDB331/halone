package dev.halone.glasses.drivers.rayneo

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reports for the RayNeo tests.
 *
 * **These are synthetic.** Nobody on this project has had a GT or GT Max on
 * the bench yet, so they are assembled from the layout published by the two
 * earlier GT projects (see `docs/protocol/rayneo-hid.md`), with values taken
 * from their at-rest observations. `docs/adding-a-device.md` is right that
 * real bytes are what catch real mistakes: whoever first plugs in a GT Max
 * should replace these with hex strings captured from it, the way
 * `XrealCaptures` holds Air 2 Pro bytes, and keep the tests passing.
 */
internal object RayneoCaptures {

    /** Glasses worn level and still: gravity on sensor +Z, a little gyro bias. */
    val IMU_AT_REST: ByteArray = imu(
        tick = 1_000_000L,
        accel = floatArrayOf(0.05f, -0.12f, 9.84f),
        gyroDeg = floatArrayOf(0.4f, -0.3f, 0.2f),
        magnet = floatArrayOf(21f, -14f, 38f),
        temperature = 39.4f,
    )

    fun imu(
        tick: Long,
        accel: FloatArray = floatArrayOf(0f, 0f, 9.81f),
        gyroDeg: FloatArray = floatArrayOf(0f, 0f, 0f),
        magnet: FloatArray = floatArrayOf(0f, 0f, 0f),
        temperature: Float = 39f,
    ): ByteArray {
        val b = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        b.put(0, 0x99.toByte()).put(1, 0x65.toByte()).put(2, 0x41.toByte())
        accel.forEachIndexed { i, v -> b.putFloat(4 + 4 * i, v) }
        gyroDeg.forEachIndexed { i, v -> b.putFloat(16 + 4 * i, v) }
        b.putFloat(28, temperature)
        b.putFloat(32, magnet[0])
        b.putFloat(36, magnet[1])
        b.putInt(40, tick.toInt())
        b.putInt(48, tick.toInt())
        b.putFloat(52, magnet[2])
        return b.array()
    }

    /** `99 c8 <board> 00 <counter> <opcode>`, plus device-info payload when given. */
    fun ack(
        opcode: Int,
        board: Int = RayneoProtocol.BOARD_GT_MAX,
        build: String? = null,
        brightness: Int? = null,
    ): ByteArray {
        val b = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        b.put(0, 0x99.toByte()).put(1, 0xC8.toByte()).put(2, board.toByte())
        b.putInt(4, 0x0001_2345)
        b.put(8, opcode.toByte())
        build?.toByteArray(Charsets.US_ASCII)?.forEachIndexed { i, c -> b.put(23 + i, c) }
        brightness?.let { b.put(41, it.toByte()) }
        return b.array()
    }

    fun deviceInfo(board: Int = RayneoProtocol.BOARD_GT_MAX, brightness: Int = 8) =
        ack(RayneoProtocol.CMD_DEVICE_INFO, board, build = "Sep 22 2026", brightness = brightness)
}
