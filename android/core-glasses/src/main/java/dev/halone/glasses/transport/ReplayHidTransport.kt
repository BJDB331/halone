package dev.halone.glasses.transport

/**
 * Feeds a driver from a canned script instead of hardware.
 *
 * Reads walk [inbound] in order and then repeat the last frame, so a capture of
 * a handful of real sensor packets can drive an arbitrarily long test. Writes
 * are recorded in [written] so a test can assert the driver sent the right
 * commands, and [replies] lets a test answer a specific command.
 */
class ReplayHidTransport(
    override val name: String = "replay",
    private val inbound: List<ByteArray> = emptyList(),
    /** Keyed by the exact bytes written; the value is queued as the next read. */
    private val replies: Map<String, ByteArray> = emptyMap(),
    private val repeatLast: Boolean = true,
) : HidTransport {

    val written = mutableListOf<ByteArray>()
    private var index = 0
    private val queued = ArrayDeque<ByteArray>()
    var closed = false
        private set

    override fun write(packet: ByteArray, timeoutMs: Int): Int {
        written += packet.copyOf()
        replies[packet.toHexKey()]?.let { queued.addLast(it) }
        return packet.size
    }

    override fun read(into: ByteArray, timeoutMs: Int): Int {
        queued.removeFirstOrNull()?.let { return it.copyTo(into) }

        val frame = when {
            index < inbound.size -> inbound[index++]
            repeatLast && inbound.isNotEmpty() -> inbound.last()
            else -> return 0
        }
        return frame.copyTo(into)
    }

    override fun close() {
        closed = true
    }

    private fun ByteArray.copyTo(into: ByteArray): Int {
        val n = minOf(size, into.size)
        copyInto(into, 0, 0, n)
        return n
    }

    companion object {
        fun ByteArray.toHexKey(): String = joinToString("") { "%02x".format(it) }

        fun hex(vararg spaced: String): List<ByteArray> = spaced.map { it.parseHex() }

        fun String.parseHex(): ByteArray =
            replace(Regex("[^0-9a-fA-F]"), "")
                .chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
    }
}
