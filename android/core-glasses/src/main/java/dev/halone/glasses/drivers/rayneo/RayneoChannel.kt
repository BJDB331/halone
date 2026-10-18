package dev.halone.glasses.drivers.rayneo

import dev.halone.glasses.transport.HidTransport
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The one endpoint the GT series has, and the one thread that reads it.
 *
 * Commands, their acknowledgements and the ~457 Hz sensor stream all share a
 * single HID interface, so nothing but this reader may call `read`: a second
 * reader would steal acknowledgements from the stream, or samples from a
 * caller waiting on an acknowledgement. Writes are serialised separately, and
 * [send] never waits, so it is safe from the reader thread itself.
 */
internal class RayneoChannel(
    private val transport: HidTransport,
    /** Every report that is not an awaited acknowledgement. Called on the reader thread. */
    private val onReport: (RayneoReport) -> Unit,
    /** Called once, on the reader thread, if the endpoint stops answering while it should be busy. */
    private val onLost: (String) -> Unit = {},
    private val framing: RayneoProtocol.Framing = RayneoProtocol.Framing.BARE,
) : Closeable {

    private class Waiter(val opcode: Int) {
        val latch = CountDownLatch(1)

        @Volatile
        var reply: RayneoReport.Ack? = null
    }

    private val waiter = AtomicReference<Waiter?>(null)
    private val writeLock = Any()

    /** One request at a time: replies carry only the opcode, so two in flight could not be told apart. */
    private val requestLock = Any()

    @Volatile
    private var running = false
    private var thread: Thread? = null

    /**
     * True while reports are expected continuously, so silence means the
     * device has gone rather than merely that it has nothing to say.
     */
    @Volatile
    var expectTraffic = false

    fun start() {
        if (running) return
        running = true
        thread = Thread(::readLoop, "rayneo-hid").apply {
            priority = Thread.MAX_PRIORITY
            isDaemon = true
            start()
        }
    }

    /** Writes a command without waiting for its acknowledgement. */
    fun send(cmd: Int, value: Int = 0): Boolean = synchronized(writeLock) {
        val written = transport.write(RayneoProtocol.command(cmd, value, framing))
        lastWriteResult = written
        written >= 0
    }

    // ---- bring-up diagnostics: what the endpoint actually did ----

    @Volatile
    var lastWriteResult = 0
        private set

    @Volatile
    var reportsRead = 0
        private set

    @Volatile
    var readErrors = 0
        private set

    /** The first few reports, raw, whether or not they parsed. */
    val firstReports = java.util.concurrent.CopyOnWriteArrayList<String>()

    fun describe(): String =
        "write=$lastWriteResult, reports=$reportsRead, empty reads=$readErrors" +
            if (firstReports.isEmpty()) "" else ", first: ${firstReports.joinToString(" | ")}"

    /**
     * Writes a command and waits for the acknowledgement that echoes its
     * opcode, or returns null after [timeoutMs]. Must not be called from the
     * reader thread, which is the one that would deliver the reply.
     */
    fun request(cmd: Int, value: Int = 0, timeoutMs: Long = REQUEST_TIMEOUT_MS): RayneoReport.Ack? {
        check(Thread.currentThread() !== thread) { "request() on the reader thread would wait on itself" }
        synchronized(requestLock) {
            val w = Waiter(cmd)
            waiter.set(w)
            try {
                if (!send(cmd, value)) return null
                w.latch.await(timeoutMs, TimeUnit.MILLISECONDS)
                return w.reply
            } finally {
                waiter.compareAndSet(w, null)
            }
        }
    }

    private fun readLoop() {
        // Reused: at ~457 Hz a buffer per read is pointless garbage.
        val buffer = ByteArray(RayneoProtocol.REPORT_SIZE + 1)
        var silent = 0

        while (running) {
            val n = transport.read(buffer, READ_TIMEOUT_MS)
            if (n <= 0) {
                readErrors++
                // Android reports a timeout and an error identically, so only
                // a long run of nothing while the stream is on counts as loss.
                if (expectTraffic && ++silent >= MAX_SILENT_READS) {
                    expectTraffic = false
                    onLost("no reports for ${MAX_SILENT_READS * READ_TIMEOUT_MS / 1000} s")
                }
                continue
            }
            silent = 0
            if (++reportsRead <= DIAGNOSTIC_REPORTS) firstReports += RayneoProtocol.hex(buffer, minOf(n, 12))

            val report = RayneoProtocol.parse(buffer, n)
            if (report is RayneoReport.Ack) {
                val w = waiter.get()
                if (w != null && w.opcode == report.opcode && waiter.compareAndSet(w, null)) {
                    w.reply = report
                    w.latch.countDown()
                    continue
                }
            }
            onReport(report)
        }
    }

    override fun close() {
        running = false
        expectTraffic = false
        thread?.takeIf { it !== Thread.currentThread() }?.join(READ_TIMEOUT_MS * 3L)
        thread = null
    }

    companion object {
        const val READ_TIMEOUT_MS = 100
        const val REQUEST_TIMEOUT_MS = 500L

        const val DIAGNOSTIC_REPORTS = 3

        /** About five seconds of silence while streaming. */
        const val MAX_SILENT_READS = 50
    }
}
