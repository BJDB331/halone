package dev.halone.glasses.drivers.xrealone

import dev.halone.glasses.transport.HidTransport
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Request/reply over port 52999, plus the unsolicited events that share it.
 *
 * Only messages in [ALLOWED_REQUESTS] can be sent. The firmware accepts far
 * more, including network and USB-function toggles that can leave the glasses
 * unreachable, and nobody testing this owns a pair to recover.
 */
internal class XrealOneControl(
    private val transport: HidTransport,
    private val onEvent: (magic: Int, payload: ByteArray) -> Unit,
    private val onClosed: (reason: String) -> Unit,
) : AutoCloseable {

    private val nextTransaction = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Long, CompletableFuture<ByteArray>>()
    private val framer = XrealOneFramer(MAX_PAYLOAD)

    /**
     * Requests go out on a thread of their own because callers reach this
     * from the UI: display mode and brightness on the main thread, where
     * Android refuses socket traffic outright (`NetworkOnMainThreadException`).
     * Waiting for the reply is not network traffic, so callers may still block.
     */
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "xreal-one-control-writer").apply { isDaemon = true } }

    @Volatile
    private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread(::readLoop, "xreal-one-control").apply {
            isDaemon = true
            start()
        }
    }

    fun request(magic: Int, body: ByteArray, timeoutMs: Long = DEFAULT_TIMEOUT_MS): Result<XrealOneProtocol.Value> {
        if (magic !in ALLOWED_REQUESTS) {
            return Result.failure(IllegalArgumentException("message 0x%04x is not on the allowlist".format(magic)))
        }
        if (!running) return Result.failure(IllegalStateException("control channel is closed"))

        val transaction = nextTransaction.getAndIncrement() and 0x7FFFFFFF
        val key = key(magic, transaction.toLong())
        val reply = CompletableFuture<ByteArray>()
        pending[key] = reply

        try {
            val packet = XrealOneProtocol.request(magic, transaction, body)
            val written = try {
                writer.submit<Int> { transport.write(packet) }.get(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: Exception) {
                return Result.failure(IllegalStateException("write of 0x%04x failed: ${e.message}".format(magic)))
            }
            if (written != packet.size) {
                return Result.failure(IllegalStateException("write of 0x%04x failed".format(magic)))
            }
            val payload = try {
                reply.get(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                return Result.failure(IllegalStateException("no reply to 0x%04x within ${timeoutMs}ms".format(magic)))
            }
            return XrealOneProtocol.decodeReply(payload)
        } finally {
            pending.remove(key)
        }
    }

    private fun readLoop() {
        val chunk = ByteArray(READ_CHUNK)
        while (running) {
            val n = transport.read(chunk, READ_TIMEOUT_MS)
            if (n < 0) {
                if (running) {
                    running = false
                    onClosed("control connection closed")
                }
                break
            }
            if (n == 0) continue
            framer.append(chunk, n)
            framer.drain(::dispatch)
        }
        pending.values.forEach { it.completeExceptionally(IllegalStateException("control channel closed")) }
    }

    private fun dispatch(magic: Int, data: ByteArray, offset: Int, length: Int) {
        if (length >= XrealOneProtocol.TRANSACTION_SIZE) {
            val transaction = XrealOneProtocol.u32be(data, offset) and 0x7FFFFFFF
            pending[key(magic, transaction)]?.let { reply ->
                reply.complete(data.copyOfRange(offset + XrealOneProtocol.TRANSACTION_SIZE, offset + length))
                return
            }
        }
        onEvent(magic, data.copyOfRange(offset, offset + length))
    }

    override fun close() {
        running = false
        writer.shutdownNow()
        runCatching { transport.close() }
        thread?.join(500)
        thread = null
    }

    private fun key(magic: Int, transaction: Long) = (magic.toLong() shl 32) or transaction

    companion object {
        const val DEFAULT_TIMEOUT_MS = 1_000L

        /** The config reply is about 220 KB of JSON. */
        const val CONFIG_TIMEOUT_MS = 8_000L

        private const val MAX_PAYLOAD = 4 * 1024 * 1024
        private const val READ_CHUNK = 16 * 1024
        private const val READ_TIMEOUT_MS = 200

        val ALLOWED_REQUESTS = setOf(
            XrealOneProtocol.MSG_GET_CONFIG,
            XrealOneProtocol.MSG_GET_ID,
            XrealOneProtocol.MSG_GET_SW_VERSION,
            XrealOneProtocol.MSG_GET_BRIGHTNESS,
            XrealOneProtocol.MSG_SET_BRIGHTNESS,
            XrealOneProtocol.MSG_GET_INPUT_MODE,
            XrealOneProtocol.MSG_SET_INPUT_MODE,
        )
    }
}
