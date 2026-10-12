package dev.halone.glasses.drivers.xreal

import java.util.zip.CRC32

/**
 * Packet framing for XREAL Air-series glasses.
 *
 * Confirmed on an Air 2 Pro; see `docs/protocol/xreal-hid.md` for the wire
 * format, the measurements behind it, and the two traps (reply payloads sit one
 * byte later than command payloads, and the MCU multiplexes unsolicited packets
 * onto the same endpoint).
 */
internal object XrealProtocol {
    const val IMU_HEAD = 0xAA.toByte()
    const val MCU_HEAD = 0xFD.toByte()

    const val IMU_MSG_GET_CAL_DATA_LENGTH = 0x14
    const val IMU_MSG_GET_CAL_DATA = 0x15
    const val IMU_MSG_START_IMU_DATA = 0x19
    const val IMU_MSG_GET_STATIC_ID = 0x1A

    /**
     * IMU replies echo the command framing, so unlike the MCU there is no extra
     * status byte: msgid at 7, payload at 8, and `length` counts the 3 bytes of
     * (msgid + the two length bytes themselves) plus the payload.
     */
    const val IMU_REPLY_MSGID_OFFSET = 7
    const val IMU_REPLY_DATA_OFFSET = 8
    const val IMU_LENGTH_OFFSET = 5
    private const val IMU_LENGTH_OVERHEAD = 3

    const val MCU_MSG_R_VSYNC = 0x01
    const val MCU_MSG_W_VSYNC = 0x02
    const val MCU_MSG_R_BRIGHTNESS = 0x03
    const val MCU_MSG_W_BRIGHTNESS = 0x04
    const val MCU_MSG_R_DISP_MODE = 0x07
    const val MCU_MSG_W_DISP_MODE = 0x08

    /**
     * Round trip carrying three timestamps: ours going out, and the device's on
     * receiving and on replying. Four numbers with the two one-way delays
     * separated is what makes the clock offset solvable rather than merely
     * estimable.
     */
    const val MCU_MSG_W_HEARTBEAT = 0x1A

    /** Unsolicited, one per display refresh once [MCU_MSG_W_VSYNC] is on. */
    const val MCU_MSG_E_VSYNC = 0x6C0B
    const val MCU_MSG_E_KEY = 0x6C05
    const val MCU_MSG_E_PSENSOR = 0x6C04

    const val HEARTBEAT_HOST_SENT_OFFSET = 0
    const val HEARTBEAT_DEVICE_RECEIVED_OFFSET = 8
    const val HEARTBEAT_DEVICE_SENT_OFFSET = 16

    /**
     * **[measured]** The vsync interface does not use the MCU's framing and
     * does not match either message table in the vendor's libraries. What it
     * sends, once every 256 refreshes, is:
     *
     * ```
     * fd 23 0e 00 00 00 00 00  90 c9 6e 85 0d 0e 00 00  90 4d fe 00 ...
     * ^  ^  ^                  ^------- u64 ---------^  ^--- u32 --^
     * |  |  payload length 14  device time of a refresh  refresh period
     * |  counter, +1 per beacon
     * head
     * ```
     *
     * The reading is self-validating: consecutive timestamps are 4.266668 s
     * apart, which is 256 periods to within a part in 10^4, the timestamp is a
     * plausible uptime in nanoseconds, and the period is 16.666 ms.
     */
    private const val VSYNC_COUNTER_OFFSET = 1
    private const val VSYNC_TIMESTAMP_OFFSET = 8
    private const val VSYNC_PERIOD_OFFSET = 16
    private const val VSYNC_PACKET_BYTES = 24

    /** One timing beacon, exactly as the wire carries it. */
    data class VsyncBeacon(val counter: Int, val deviceTimeNs: Long, val periodNs: Long)

    fun vsyncBeacon(packet: ByteArray, size: Int = packet.size): VsyncBeacon? {
        if (size < VSYNC_PACKET_BYTES || packet[0] != MCU_HEAD) return null
        val period = u32At(packet, VSYNC_PERIOD_OFFSET)
        val time = u64At(packet, VSYNC_TIMESTAMP_OFFSET)
        if (period <= 0 || time <= 0) return null
        return VsyncBeacon(
            counter = packet[VSYNC_COUNTER_OFFSET].toInt() and 0xFF,
            deviceTimeNs = time,
            periodNs = period,
        )
    }

    /** Payload offset in a command we send. */
    const val MCU_DATA_OFFSET = 22

    /**
     * Replies carry one extra byte compared with commands (length is
     * `18 + dataLen` against `17 + dataLen`): a status byte at 22, payload at 23.
     * Reading the payload at 22 silently returns the status, which always
     * looks like a plausible `0`.
     */
    const val MCU_REPLY_STATUS_OFFSET = 22
    const val MCU_REPLY_DATA_OFFSET = 23

    const val MCU_MSGID_OFFSET = 15

    private fun checksum(packet: ByteArray, length: Int): Int {
        val crc = CRC32()
        crc.update(packet, 5, length)
        return crc.value.toInt()
    }

    fun imuCommand(msgId: Int, data: ByteArray = ByteArray(0)): ByteArray {
        val packetLen = 3 + data.size
        val out = ByteArray(5 + packetLen)
        out[0] = IMU_HEAD
        writeU16(out, 5, packetLen)
        out[7] = msgId.toByte()
        data.copyInto(out, 8)
        writeU32(out, 1, checksum(out, packetLen))
        return out
    }

    fun mcuCommand(msgId: Int, data: ByteArray = ByteArray(0)): ByteArray {
        val packetLen = 17 + data.size
        val out = ByteArray(5 + packetLen)
        out[0] = MCU_HEAD
        writeU16(out, 5, packetLen)
        writeU64(out, 7, 0L)
        writeU16(out, MCU_MSGID_OFFSET, msgId)
        data.copyInto(out, MCU_DATA_OFFSET)
        writeU32(out, 1, checksum(out, packetLen))
        return out
    }

    fun startImuStream() = imuCommand(IMU_MSG_START_IMU_DATA, byteArrayOf(0x01))
    fun stopImuStream() = imuCommand(IMU_MSG_START_IMU_DATA, byteArrayOf(0x00))
    fun readCalibrationLength() = imuCommand(IMU_MSG_GET_CAL_DATA_LENGTH)
    fun readCalibrationSegment() = imuCommand(IMU_MSG_GET_CAL_DATA)
    fun readDisplayMode() = mcuCommand(MCU_MSG_R_DISP_MODE)
    fun writeDisplayMode(mode: Int) = mcuCommand(MCU_MSG_W_DISP_MODE, byteArrayOf(mode.toByte()))
    fun readBrightness() = mcuCommand(MCU_MSG_R_BRIGHTNESS)
    fun readVsyncEnabled() = mcuCommand(MCU_MSG_R_VSYNC)
    fun writeVsyncEnabled(on: Boolean) =
        mcuCommand(MCU_MSG_W_VSYNC, byteArrayOf(if (on) 1 else 0))

    fun heartbeat(hostTimeNs: Long): ByteArray {
        val data = ByteArray(24)
        writeU64(data, HEARTBEAT_HOST_SENT_OFFSET, hostTimeNs)
        return mcuCommand(MCU_MSG_W_HEARTBEAT, data)
    }

    fun writeBrightness(level: Int) = mcuCommand(MCU_MSG_W_BRIGHTNESS, byteArrayOf(level.toByte()))

    fun imuMsgId(reply: ByteArray): Int = reply[IMU_REPLY_MSGID_OFFSET].toInt() and 0xFF

    /**
     * Payload length of an IMU reply, or -1 if the framing does not hold up.
     * Returning -1 rather than throwing keeps a malformed frame skippable:
     * on this endpoint an unexpected frame is far more likely to be a stale
     * sensor sample than a real error.
     */
    fun imuPayloadLength(reply: ByteArray, received: Int): Int {
        if (received < IMU_REPLY_DATA_OFFSET) return -1
        val declared = (reply[IMU_LENGTH_OFFSET].toInt() and 0xFF) or
            ((reply[IMU_LENGTH_OFFSET + 1].toInt() and 0xFF) shl 8)
        val payload = declared - IMU_LENGTH_OVERHEAD
        if (payload < 0) return -1
        if (IMU_REPLY_DATA_OFFSET + payload > received) return -1
        return payload
    }

    fun u32At(b: ByteArray, at: Int): Long {
        var v = 0L
        for (i in 0 until 4) v = v or ((b[at + i].toLong() and 0xFF) shl (8 * i))
        return v
    }

    fun u64At(b: ByteArray, at: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = v or ((b[at + i].toLong() and 0xFF) shl (8 * i))
        return v
    }

    fun mcuMsgId(reply: ByteArray): Int =
        (reply[MCU_MSGID_OFFSET].toInt() and 0xFF) or
            ((reply[MCU_MSGID_OFFSET + 1].toInt() and 0xFF) shl 8)

    private fun writeU16(b: ByteArray, at: Int, v: Int) {
        b[at] = (v and 0xFF).toByte()
        b[at + 1] = ((v ushr 8) and 0xFF).toByte()
    }

    private fun writeU32(b: ByteArray, at: Int, v: Int) {
        for (i in 0 until 4) b[at + i] = ((v ushr (8 * i)) and 0xFF).toByte()
    }

    private fun writeU64(b: ByteArray, at: Int, v: Long) {
        for (i in 0 until 8) b[at + i] = ((v ushr (8 * i)) and 0xFF).toByte()
    }

    fun hex(b: ByteArray, length: Int = b.size): String =
        (0 until minOf(length, b.size)).joinToString(" ") { "%02x".format(b[it]) }
}
