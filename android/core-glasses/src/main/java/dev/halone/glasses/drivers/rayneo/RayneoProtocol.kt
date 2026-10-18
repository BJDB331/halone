package dev.halone.glasses.drivers.rayneo

import dev.halone.glasses.math.Vec3

/**
 * One 64-byte report from the glasses, understood or not.
 *
 * Everything the GT series sends starts with `0x99`; the second byte says what
 * it is. See `docs/protocol/rayneo-hid.md`.
 */
internal sealed interface RayneoReport {

    /**
     * A nine-axis sample, still in the sensor's own frame and units — the
     * session does the remap, so this stays a plain transcription of the wire.
     */
    data class Imu(
        /** Device tick, 100 µs per count, wrapping at 2³². */
        val tick: Long,
        /** m/s². */
        val accel: Vec3,
        /** Degrees per second, not radians: a resting bias of ~1 would be absurd in rad/s. */
        val gyroDegPerSec: Vec3,
        /** µT. */
        val magnetMicrotesla: Vec3,
        val temperatureC: Float,
    ) : RayneoReport

    /**
     * The glasses' answer to a command — or, for [RayneoProtocol.CMD_ANCHOR_MODE],
     * sent unprompted when the wearer presses the mode button.
     */
    class Ack(
        val board: Int,
        val opcode: Int,
        /** The whole report, for commands whose reply carries data (device info). */
        val raw: ByteArray,
    ) : RayneoReport

    data class Unrecognised(val reason: String) : RayneoReport
}

/**
 * The RayNeo GT-series USB HID protocol, as far as it is known.
 *
 * Byte-level only: no threads and no transport, so all of it can be tested
 * against hand-built frames. Sources and what has and has not been seen on
 * hardware are in `docs/protocol/rayneo-hid.md`.
 */
internal object RayneoProtocol {

    const val VENDOR_ID = 0x3941

    /** Normal operation. GT and GT Max share it; the board id tells them apart. */
    const val PRODUCT_ID = 0xAF50

    /** Firmware-update mode. Recognised only so it is never mistaken for a working device. */
    const val PRODUCT_ID_DFU = 0xAF51

    const val REPORT_SIZE = 64

    const val BOARD_GT = 0x40
    const val BOARD_GT_MAX = 0x41

    const val CMD_DEVICE_INFO = 0x00
    const val CMD_STREAM_ON = 0x01
    const val CMD_STREAM_OFF = 0x02

    /**
     * Which of the glasses' own screen-anchoring modes to use — see
     * [AnchorMode]. The glasses also send this opcode, unprompted, when the
     * wearer presses the mode button.
     */
    const val CMD_ANCHOR_MODE = 0x08

    const val CMD_BRIGHTNESS = 0x09

    /** Brightness the glasses ship at. Whether it is also the ceiling is not known. */
    const val BRIGHTNESS_FACTORY = 8

    private const val TAG_COMMAND: Byte = 0x66
    private const val TAG_INPUT = 0x99
    private const val KIND_ACK = 0xC8
    private const val KIND_IMU = 0x65

    private const val OFF_BOARD = 2
    private const val OFF_ACK_OPCODE = 8

    private const val OFF_ACCEL = 4
    private const val OFF_GYRO = 16
    private const val OFF_TEMPERATURE = 28
    private const val OFF_MAG_X = 32
    private const val OFF_MAG_Y = 36
    private const val OFF_TICK = 40

    /** Not next to X and Y. Two independent captures agree on it. */
    private const val OFF_MAG_Z = 52

    /** Device-info reply: the firmware build date, as ASCII ("Sep 22 2026"). */
    private const val OFF_INFO_BUILD = 23
    private const val INFO_BUILD_MAX = 18

    /** Device-info reply: the current brightness level. */
    const val OFF_INFO_BRIGHTNESS = 41

    /** Nanoseconds per device tick. */
    const val TICK_NS = 100_000L

    /**
     * The glasses' own head-tracked display modes, run by an on-board 3DoF
     * chip. Halone does its own world-locking, so it wants [FOLLOW]: the image
     * fixed to the face, like any plain display. Left in [PINNED] or [STEADY]
     * the glasses would counter-rotate the image as well, and every head turn
     * would be compensated twice.
     */
    enum class AnchorMode { FOLLOW, PINNED, STEADY }

    /**
     * The command byte for [mode] on [board]: the two boards number pinned and
     * steady differently.
     *
     * - GT, measured by the macOS project: 0 follow, 1 pinned, 2 steady.
     * - GT Max **[measured, by eye]**: 1 is not pinned — a wearer saw it act
     *   like steady — so pinned and steady are taken as swapped, 2 pinned.
     *   0 is follow on both: Halone's own world-locking is right on a GT Max,
     *   which it would not be if 0 stabilised the image as well.
     */
    fun anchorValue(mode: AnchorMode, board: Int): Int = when (mode) {
        AnchorMode.FOLLOW -> 0
        AnchorMode.PINNED -> if (board == BOARD_GT) 1 else 2
        AnchorMode.STEADY -> if (board == BOARD_GT) 2 else 1
    }

    /**
     * A 64-byte output report: `66 <cmd> <value> 00…`.
     *
     * hidapi-based tools prepend a `0x00` report id, which hidapi strips; the
     * descriptor declares no report ids, so on a raw endpoint the frame starts
     * at the tag.
     */
    fun command(cmd: Int, value: Int = 0, framing: Framing = Framing.BARE): ByteArray {
        val at = if (framing == Framing.REPORT_ID) 1 else 0
        return ByteArray(REPORT_SIZE + at).also {
            it[at] = TAG_COMMAND
            it[at + 1] = cmd.toByte()
            it[at + 2] = value.toByte()
        }
    }

    /**
     * How a command goes on the wire. [BARE] is what the descriptor implies;
     * [REPORT_ID] is the 65-byte frame hidapi callers build, kept as a
     * fallback until real GT Max hardware says which one it wants.
     */
    enum class Framing { BARE, REPORT_ID }

    fun parse(buf: ByteArray, length: Int): RayneoReport {
        // Tolerate a leading report id, which some host stacks deliver.
        val at = if (length >= 2 && buf[0] == 0.toByte() && u8(buf, 1) == TAG_INPUT) 1 else 0
        val n = length - at
        if (n < 4) return RayneoReport.Unrecognised("short report ($length bytes)")
        if (u8(buf, at) != TAG_INPUT) return RayneoReport.Unrecognised("tag %02x".format(u8(buf, at)))

        val board = u8(buf, at + OFF_BOARD)
        return when (u8(buf, at + 1)) {
            KIND_ACK -> RayneoReport.Ack(
                board = board,
                opcode = if (n > OFF_ACK_OPCODE) u8(buf, at + OFF_ACK_OPCODE) else -1,
                raw = buf.copyOfRange(at, at + n),
            )
            KIND_IMU -> parseImu(buf, at, n)
            else -> RayneoReport.Unrecognised("kind %02x".format(u8(buf, at + 1)))
        }
    }

    private fun parseImu(buf: ByteArray, at: Int, n: Int): RayneoReport {
        if (n < REPORT_SIZE) return RayneoReport.Unrecognised("short IMU report ($n bytes)")
        val accel = vec(buf, at + OFF_ACCEL)
        val gyro = vec(buf, at + OFF_GYRO)
        val magnet = Vec3(
            f32(buf, at + OFF_MAG_X),
            f32(buf, at + OFF_MAG_Y),
            f32(buf, at + OFF_MAG_Z),
        )
        val temperature = f32(buf, at + OFF_TEMPERATURE)
        if (!accel.isFinite() || !gyro.isFinite() || !magnet.isFinite() || !temperature.isFinite()) {
            return RayneoReport.Unrecognised("non-finite IMU values")
        }
        return RayneoReport.Imu(
            tick = u32(buf, at + OFF_TICK),
            accel = accel,
            gyroDegPerSec = gyro,
            magnetMicrotesla = magnet,
            temperatureC = temperature,
        )
    }

    /** The firmware build date from a device-info reply, or null if it is not printable. */
    fun buildDate(info: RayneoReport.Ack): String? {
        val raw = info.raw
        if (raw.size <= OFF_INFO_BUILD) return null
        val end = minOf(raw.size, OFF_INFO_BUILD + INFO_BUILD_MAX)
        val text = buildString {
            for (i in OFF_INFO_BUILD until end) {
                val c = u8(raw, i)
                if (c == 0) break
                if (c !in 0x20..0x7E) return null
                append(c.toChar())
            }
        }.trim()
        return text.ifEmpty { null }
    }

    fun brightness(info: RayneoReport.Ack): Int? =
        if (info.raw.size > OFF_INFO_BRIGHTNESS) u8(info.raw, OFF_INFO_BRIGHTNESS) else null

    fun boardName(board: Int): String = when (board) {
        BOARD_GT -> "GT"
        BOARD_GT_MAX -> "GT Max"
        else -> "board 0x%02x".format(board)
    }

    fun hex(bytes: ByteArray, limit: Int = bytes.size): String =
        bytes.take(limit).joinToString(" ") { "%02x".format(it) }

    private fun Vec3.isFinite() = x.isFinite() && y.isFinite() && z.isFinite()

    private fun vec(b: ByteArray, at: Int) = Vec3(f32(b, at), f32(b, at + 4), f32(b, at + 8))

    private fun f32(b: ByteArray, at: Int): Float = Float.fromBits(u32(b, at).toInt())

    private fun u8(b: ByteArray, at: Int) = b[at].toInt() and 0xFF

    private fun u32(b: ByteArray, at: Int): Long {
        var v = 0L
        for (i in 0 until 4) v = v or ((b[at + i].toLong() and 0xFF) shl (8 * i))
        return v
    }
}
