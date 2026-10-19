package dev.halone.glasses.drivers.xrealone

import java.io.ByteArrayOutputStream

/**
 * Wire format of the One series' TCP services. See `docs/protocol/xreal-one.md`.
 *
 * Every message is `magic:u16 BE, length:u32 BE, payload[length]`. Control
 * requests and their replies start the payload with a `u32 BE` transaction id;
 * unsolicited events do not.
 */
internal object XrealOneProtocol {
    const val HOST_NCM = "169.254.2.1"
    const val HOST_ECM = "169.254.1.1"
    const val PORT_REPORTS = 52998
    const val PORT_CONTROL = 52999

    const val HEADER_SIZE = 6
    const val TRANSACTION_SIZE = 4
    private const val REQUEST_FLAG = 0x80000000L

    const val MSG_REPORT = 0x2836
    const val MSG_KEY_STATE = 0x272E
    const val MSG_GET_BRIGHTNESS = 0x271B
    const val MSG_SET_BRIGHTNESS = 0x271C
    const val MSG_GET_SW_VERSION = 0x271D
    const val MSG_GET_CONFIG = 0x271F
    const val MSG_GET_ID = 0x2729
    const val MSG_GET_INPUT_MODE = 0x2821
    const val MSG_SET_INPUT_MODE = 0x2822

    const val INPUT_MODE_MONO = 0
    const val INPUT_MODE_SBS = 1

    fun request(magic: Int, transactionId: Int, body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(HEADER_SIZE + TRANSACTION_SIZE + body.size)
        out.writeU16(magic)
        out.writeU32(TRANSACTION_SIZE.toLong() + body.size)
        out.writeU32((transactionId.toLong() and 0x7FFFFFFF) or REQUEST_FLAG)
        out.write(body)
        return out.toByteArray()
    }

    /** Field 3 left at its default. */
    fun getBody(): ByteArray = byteArrayOf(0x18, 0x00)

    /** Field 3 holding a message whose field 1 is [value]. */
    fun setBody(value: Int): ByteArray {
        val inner = ByteArrayOutputStream().apply {
            write(0x08)
            writeVarint(value.toLong())
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            write(0x1A)
            writeVarint(inner.size.toLong())
            write(inner)
        }.toByteArray()
    }

    sealed interface Value {
        data class Number(val value: Long) : Value
        class Bytes(val value: ByteArray) : Value
    }

    /**
     * A reply is field 4 wrapping `{1: status, 2: value}`. Protobuf omits
     * defaults, so an absent status is success and an absent value is zero.
     */
    fun decodeReply(body: ByteArray): Result<Value> = runCatching {
        val outer = ProtoReader(body).fields()
        val nested = outer[4] as? Value.Bytes ?: error("reply has no field 4")
        val fields = ProtoReader(nested.value).fields()
        val status = (fields[1] as? Value.Number)?.value ?: 0L
        if (status != 0L) throw RejectedException(status)
        fields[2] ?: Value.Number(0)
    }

    class RejectedException(val status: Long) : IllegalStateException("glasses rejected the request with status $status")

    private class ProtoReader(private val bytes: ByteArray) {
        private var at = 0

        fun fields(): Map<Int, Value> {
            val out = mutableMapOf<Int, Value>()
            while (at < bytes.size) {
                val tag = varint()
                val field = (tag ushr 3).toInt()
                out[field] = when ((tag and 0x7).toInt()) {
                    WIRE_VARINT -> Value.Number(varint())
                    WIRE_LENGTH -> {
                        val length = varint().toInt()
                        require(length >= 0 && at + length <= bytes.size) { "field $field overruns the message" }
                        Value.Bytes(bytes.copyOfRange(at, at + length)).also { at += length }
                    }
                    WIRE_FIXED64 -> skip(8)
                    WIRE_FIXED32 -> skip(4)
                    else -> error("unsupported wire type in tag $tag")
                }
            }
            return out
        }

        private fun skip(n: Int): Value {
            require(at + n <= bytes.size) { "truncated fixed-width field" }
            at += n
            return Value.Bytes(ByteArray(0))
        }

        private fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                require(at < bytes.size) { "truncated varint" }
                val b = bytes[at++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
                require(shift < 64) { "varint too long" }
            }
        }
    }

    private const val WIRE_VARINT = 0
    private const val WIRE_FIXED64 = 1
    private const val WIRE_LENGTH = 2
    private const val WIRE_FIXED32 = 5

    private fun ByteArrayOutputStream.writeU16(v: Int) {
        write((v ushr 8) and 0xFF)
        write(v and 0xFF)
    }

    private fun ByteArrayOutputStream.writeU32(v: Long) {
        for (shift in intArrayOf(24, 16, 8, 0)) write(((v ushr shift) and 0xFF).toInt())
    }

    private fun ByteArrayOutputStream.writeVarint(v: Long) {
        var rest = v
        while (rest and 0x7FL.inv() != 0L) {
            write(((rest and 0x7F) or 0x80).toInt())
            rest = rest ushr 7
        }
        write(rest.toInt())
    }

    fun u16be(b: ByteArray, at: Int) =
        ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

    fun u32be(b: ByteArray, at: Int): Long =
        ((b[at].toLong() and 0xFF) shl 24) or ((b[at + 1].toLong() and 0xFF) shl 16) or
            ((b[at + 2].toLong() and 0xFF) shl 8) or (b[at + 3].toLong() and 0xFF)

    fun hex(b: ByteArray): String = b.joinToString(" ") { "%02x".format(it) }
}

/**
 * Reassembles messages from a TCP stream, which splits and merges them at
 * arbitrary points. Payloads are handed out as views into an internal buffer,
 * valid only for the duration of the callback, so the 1.4 kHz report stream
 * costs no allocation per message.
 */
internal class XrealOneFramer(private val maxPayload: Int) {

    private var buffer = ByteArray(INITIAL_CAPACITY)
    private var start = 0
    private var end = 0

    var droppedBytes = 0L
        private set

    fun append(src: ByteArray, length: Int) {
        if (length <= 0) return
        ensureRoom(length)
        System.arraycopy(src, 0, buffer, end, length)
        end += length
    }

    fun drain(onMessage: (magic: Int, data: ByteArray, offset: Int, length: Int) -> Unit) {
        while (end - start >= XrealOneProtocol.HEADER_SIZE) {
            val magic = XrealOneProtocol.u16be(buffer, start)
            val length = XrealOneProtocol.u32be(buffer, start + 2)
            if (length > maxPayload) {
                start++
                droppedBytes++
                continue
            }
            val total = XrealOneProtocol.HEADER_SIZE + length.toInt()
            if (end - start < total) break
            onMessage(magic, buffer, start + XrealOneProtocol.HEADER_SIZE, length.toInt())
            start += total
        }
        if (start == end) {
            start = 0
            end = 0
        }
    }

    private fun ensureRoom(incoming: Int) {
        if (end + incoming <= buffer.size) return
        val pending = end - start
        if (start > 0) {
            System.arraycopy(buffer, start, buffer, 0, pending)
            start = 0
            end = pending
        }
        if (end + incoming > buffer.size) {
            buffer = buffer.copyOf(maxOf(buffer.size * 2, end + incoming))
        }
    }

    private companion object {
        const val INITIAL_CAPACITY = 16 * 1024
    }
}
