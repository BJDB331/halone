package dev.halone.glasses.drivers.rayneo

import dev.halone.glasses.transport.HidTransport
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * A pair of GT-series glasses as far as the driver can tell: acknowledges
 * every command, answers device info, and streams [imuFrames] in a loop while
 * the stream is on — which `ReplayHidTransport` cannot do, because it has no
 * notion of the stream being off, and a reader thread over it would spin.
 *
 * Reads block for up to their timeout, as a real endpoint's do.
 */
internal class FakeRayneoGlasses(
    private val board: Int = RayneoProtocol.BOARD_GT_MAX,
    private val imuFrames: List<ByteArray> = listOf(RayneoCaptures.IMU_AT_REST),
    /** False to play an interface that never answers. */
    private val answers: Boolean = true,
    /** True to play firmware that ignores commands without hidapi's leading report id. */
    private val wantsReportId: Boolean = false,
    override val name: String = "fake-rayneo",
) : HidTransport {

    val written = CopyOnWriteArrayList<ByteArray>()
    private val pending = LinkedBlockingQueue<ByteArray>()

    @Volatile
    var streaming = false
        private set

    @Volatile
    var closed = false
        private set

    private var next = 0

    /** What the wearer's mode button sends. */
    fun pressModeButton() {
        pending.put(RayneoCaptures.ack(RayneoProtocol.CMD_ANCHOR_MODE, board))
    }

    /** Opcode and value of every command written, in order. */
    fun commands(): List<Pair<Int, Int>> = written.map {
        val at = if (it.size > 64) 1 else 0
        (it[at + 1].toInt() and 0xFF) to (it[at + 2].toInt() and 0xFF)
    }

    override fun write(packet: ByteArray, timeoutMs: Int): Int {
        if (closed) return -1
        written += packet.copyOf()
        if (!answers) return packet.size
        val prefixed = packet.size > 64
        if (prefixed != wantsReportId) return packet.size
        val opcode = packet[if (prefixed) 2 else 1].toInt() and 0xFF
        when (opcode) {
            RayneoProtocol.CMD_STREAM_ON -> streaming = true
            RayneoProtocol.CMD_STREAM_OFF -> streaming = false
        }
        pending.put(
            if (opcode == RayneoProtocol.CMD_DEVICE_INFO) RayneoCaptures.deviceInfo(board)
            else RayneoCaptures.ack(opcode, board)
        )
        return packet.size
    }

    override fun read(into: ByteArray, timeoutMs: Int): Int {
        if (closed) return -1
        // As measured on a GT Max: the IN endpoint's packets are a full 64
        // bytes, so a transfer asking for more never completes on a reply.
        if (into.size > RayneoProtocol.REPORT_SIZE) {
            Thread.sleep(timeoutMs.toLong())
            return -1
        }
        val frame = pending.poll() ?: if (streaming && imuFrames.isNotEmpty()) {
            Thread.sleep(1)
            imuFrames[next++ % imuFrames.size]
        } else {
            pending.poll(timeoutMs.toLong(), TimeUnit.MILLISECONDS) ?: return 0
        }
        val n = minOf(frame.size, into.size)
        frame.copyInto(into, 0, 0, n)
        return n
    }

    override fun close() {
        closed = true
    }
}
