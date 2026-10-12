package dev.halone.glasses.drivers.xreal

import dev.halone.glasses.transport.HidTransport
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * One reader for the MCU endpoint, because the device multiplexes everything
 * onto it.
 *
 * Command replies, button presses, proximity events and — once it is switched
 * on — a vsync event per display refresh all arrive on the same IN endpoint.
 * Reading it on demand worked while the only traffic was replies and the odd
 * button press: a caller wrote a command and drained until the msgid matched.
 * At 60 events a second that stops being viable, and two callers reading the
 * same endpoint would steal each other's replies.
 *
 * So a single thread reads, and everything else either waits on a reply slot or
 * receives an event. The thread also timestamps each packet on arrival, which
 * is the only place a host-clock reading of a device event can be taken.
 */
internal class XrealMcuChannel(
    private val transport: HidTransport,
    private val onEvent: (msgId: Int, packet: ByteArray, arrivedAtNs: Long) -> Unit,
) {
    private val pending = ConcurrentHashMap<Int, ArrayBlockingQueue<ByteArray>>()

    @Volatile
    private var running = false
    private var thread: Thread? = null

    @Volatile
    var packetsRead = 0L
        private set

    fun start() {
        if (running) return
        running = true
        thread = Thread(::readLoop, "xreal-mcu").apply {
            isDaemon = true
            start()
        }
    }

    fun close() {
        running = false
        thread?.join(READ_TIMEOUT_MS * 2L)
        thread = null
    }

    /**
     * Writes [packet] and waits for the reply carrying [msgId].
     *
     * The slot is registered before the write: a device that answers faster
     * than this thread can get back to waiting would otherwise have its reply
     * delivered to nobody and the caller would time out on a reply that did
     * arrive.
     */
    fun request(packet: ByteArray, msgId: Int, timeoutMs: Long): ByteArray? {
        val slot = ArrayBlockingQueue<ByteArray>(1)
        pending[msgId] = slot
        try {
            transport.write(packet)
            return slot.poll(timeoutMs, TimeUnit.MILLISECONDS)
        } finally {
            pending.remove(msgId)
        }
    }

    private fun readLoop() {
        val buffer = ByteArray(PACKET_SIZE)
        while (running) {
            val n = transport.read(buffer, READ_TIMEOUT_MS)
            val arrivedAtNs = System.nanoTime()
            if (n < XrealProtocol.MCU_REPLY_DATA_OFFSET) continue
            if (buffer[0] != XrealProtocol.MCU_HEAD) continue

            packetsRead++
            val msgId = XrealProtocol.mcuMsgId(buffer)
            val packet = buffer.copyOf(n)
            val slot = pending[msgId]
            if (slot != null) slot.offer(packet) else onEvent(msgId, packet, arrivedAtNs)
        }
    }

    private companion object {
        const val PACKET_SIZE = 64
        const val READ_TIMEOUT_MS = 150
    }
}
