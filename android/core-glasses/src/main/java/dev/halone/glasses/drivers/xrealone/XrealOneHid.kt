package dev.halone.glasses.drivers.xrealone

import dev.halone.glasses.transport.HidTransport
import java.util.zip.CRC32

/**
 * The One series' HID control channel (interface 0), framed as xr-tools
 * documents it. This channel also carries firmware updates and recovery, so it
 * is fenced off hard: [request] refuses anything outside [ALLOWED], and the
 * only write it can make is [enableNetwork] / [restoreNetwork], which touch the
 * NCM and ECM fields of the USB function word and nothing else.
 *
 * Frame, little-endian, padded to 1024 bytes:
 * `0xFD, crc32:u32, length:u16, requestId:u32, timestamp:u32, command[2], 5×0, payload`,
 * with the CRC over everything from `length` to the end of the payload and
 * `length` counting the same span. Replies add a status byte before the payload.
 */
class XrealOneHid(private val transport: HidTransport) {

    class Reply(val command: Int, val requestId: Long, val status: Int, val payload: ByteArray)

    /**
     * The USB function word. Each field is two bits; reading, 1 has been seen to
     * mean on. When writing, 0 leaves a field alone, 1 turns it on and 2 off.
     */
    data class UsbFunctions(val word: Long) {
        val ncm get() = field(NCM_SHIFT)
        val ecm get() = field(ECM_SHIFT)
        val networkOn get() = ncm == ON || ecm == ON

        private fun field(shift: Int) = ((word ushr shift) and 0x3).toInt()

        override fun toString() = "0x%08x (ncm=%d ecm=%d uac=%d hid=%d mtp=%d storage=%d uvc0=%d uvc1=%d enable=%d)".format(
            word, ncm, ecm, field(4), field(6), field(8), field(10), field(12), field(14), field(ENABLE_SHIFT),
        )
    }

    private var nextRequest = 1L

    fun usbFunctions(): Result<UsbFunctions> = request(CMD_GET_USB_CONFIG).mapCatching { reply ->
        require(reply.payload.size >= 4) { "short usb config reply (${reply.payload.size} bytes)" }
        UsbFunctions(u32(reply.payload, 0))
    }

    fun mcuVersion(): Result<String> = request(CMD_GET_MCU_VERSION).map { String(it.payload, Charsets.UTF_8).trim('\u0000', ' ') }

    fun dspVersion(): Result<String> = request(CMD_GET_DSP_VERSION).map { String(it.payload, Charsets.UTF_8).trim('\u0000', ' ') }

    /** Turns NCM and ECM on, leaving every other function as it is. The glasses re-enumerate afterwards. */
    fun enableNetwork(): Result<Unit> = setNetwork(ON)

    /** Turns NCM and ECM back off, for undoing [enableNetwork] when they started off. */
    fun restoreNetwork(): Result<Unit> = setNetwork(OFF)

    private fun setNetwork(value: Int): Result<Unit> {
        val word = (value.toLong() shl NCM_SHIFT) or (value.toLong() shl ECM_SHIFT) or (ON.toLong() shl ENABLE_SHIFT)
        val payload = ByteArray(4).also { put32(it, 0, word) }
        return request(CMD_SET_USB_CONFIG, payload).map { }
    }

    fun request(command: Int, payload: ByteArray = ByteArray(0), timeoutMs: Long = DEFAULT_TIMEOUT_MS): Result<Reply> {
        if (command !in ALLOWED) {
            return Result.failure(IllegalArgumentException("HID command 0x%04x is not on the allowlist".format(command)))
        }
        if (command == CMD_SET_USB_CONFIG && !onlyNetworkFields(payload)) {
            return Result.failure(IllegalArgumentException("refusing a USB config write that touches more than NCM and ECM"))
        }
        val requestId = nextRequest++
        val packet = frame(command, requestId, payload)
        if (transport.write(packet) != packet.size) return Result.failure(IllegalStateException("HID write failed"))

        val buffer = ByteArray(FRAME_SIZE)
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            val n = transport.read(buffer, READ_TIMEOUT_MS)
            if (n <= 0) continue
            val reply = parse(buffer, n) ?: continue
            if (reply.command != command || reply.requestId != requestId) continue
            if (reply.status != 0) return Result.failure(IllegalStateException("glasses answered 0x%04x with status %d".format(command, reply.status)))
            return Result.success(reply)
        }
        return Result.failure(IllegalStateException("no reply to HID command 0x%04x".format(command)))
    }

    companion object {
        const val CMD_GET_USB_CONFIG = 0x00D2
        const val CMD_SET_USB_CONFIG = 0x00D3
        const val CMD_GET_MCU_VERSION = 0x0026
        const val CMD_GET_DSP_VERSION = 0x0018

        val ALLOWED = setOf(CMD_GET_USB_CONFIG, CMD_SET_USB_CONFIG, CMD_GET_MCU_VERSION, CMD_GET_DSP_VERSION)

        const val HID_INTERFACE = 0
        const val FRAME_SIZE = 1024
        const val HEADER_SIZE = 22
        private const val CHECKSUMMED_FIELDS = 17
        private const val MAGIC = 0xFD
        private const val NCM_SHIFT = 0
        private const val ECM_SHIFT = 2
        private const val ENABLE_SHIFT = 16
        private const val ON = 1
        private const val OFF = 2
        private const val DEFAULT_TIMEOUT_MS = 1_500L
        private const val READ_TIMEOUT_MS = 100

        /** Bits a network write may set: NCM, ECM and the enable field. */
        private const val NETWORK_WRITE_MASK = (0x3L shl NCM_SHIFT) or (0x3L shl ECM_SHIFT) or (0x3L shl ENABLE_SHIFT)

        private fun onlyNetworkFields(payload: ByteArray) =
            payload.size == 4 && (u32(payload, 0) and NETWORK_WRITE_MASK.inv() and 0xFFFFFFFFL) == 0L

        fun frame(command: Int, requestId: Long, payload: ByteArray): ByteArray {
            val packet = ByteArray(FRAME_SIZE)
            packet[0] = MAGIC.toByte()
            put16(packet, 5, CHECKSUMMED_FIELDS + payload.size)
            put32(packet, 7, requestId)
            put32(packet, 11, 0)
            packet[15] = command.toByte()
            packet[16] = (command ushr 8).toByte()
            payload.copyInto(packet, HEADER_SIZE)
            val crc = CRC32().apply { update(packet, 5, CHECKSUMMED_FIELDS + payload.size) }.value
            put32(packet, 1, crc)
            return packet
        }

        /** Null for anything that is not a well-formed reply. */
        fun parse(b: ByteArray, n: Int): Reply? {
            if (n < HEADER_SIZE + 1 || (b[0].toInt() and 0xFF) != MAGIC) return null
            val length = u16(b, 5)
            val end = 5 + length
            if (length < CHECKSUMMED_FIELDS + 1 || end > n) return null
            val crc = CRC32().apply { update(b, 5, length) }.value
            if (crc != u32(b, 1)) return null
            val command = (b[15].toInt() and 0xFF) or ((b[16].toInt() and 0xFF) shl 8)
            return Reply(command, u32(b, 7), b[HEADER_SIZE].toInt() and 0xFF, b.copyOfRange(HEADER_SIZE + 1, end))
        }

        private fun u16(b: ByteArray, at: Int) = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

        private fun u32(b: ByteArray, at: Int): Long {
            var v = 0L
            for (i in 0 until 4) v = v or ((b[at + i].toLong() and 0xFF) shl (8 * i))
            return v
        }

        private fun put16(b: ByteArray, at: Int, v: Int) {
            b[at] = v.toByte()
            b[at + 1] = (v ushr 8).toByte()
        }

        private fun put32(b: ByteArray, at: Int, v: Long) {
            for (i in 0 until 4) b[at + i] = (v ushr (8 * i)).toByte()
        }
    }
}
