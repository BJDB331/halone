package dev.halone.shell

import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import org.json.JSONObject

/**
 * TCP for apps with `network.tcp`, behind `sockets.*`. Each socket belongs to
 * the window that opened it and closes with it. Received bytes go out as
 * `socket.data` events of at most [CHUNK_BYTES], and a socket stops reading
 * once [WINDOW] of them are unacknowledged (`sockets.ack`), so a slow page
 * cannot make the shell buffer without limit. Bytes are base64 on the wire,
 * which the bridge's string ports need.
 */
class Sockets(
    /** Where events and replies are posted from: the thread the bridge is served on. */
    private val onMain: (Runnable) -> Unit = Runnable::run,
    private val open: (host: String, port: Int, timeoutMs: Int, tls: Boolean) -> Socket = ::openSocket,
) {
    /** A socket event for window [owner]: `socket.data` or `socket.closed`. */
    var onEvent: ((owner: String, name: String, data: JSONObject) -> Unit)? = null

    private class Connection(val id: Int, val owner: String, val socket: Socket) {
        val credit = Semaphore(WINDOW)
        val writer: ExecutorService = Executors.newSingleThreadExecutor()

        @Volatile var closed = false
    }

    private val connections = ConcurrentHashMap<Int, Connection>()
    private val nextId = AtomicInteger(1)
    private val connecting: ExecutorService = Executors.newCachedThreadPool()

    fun connect(owner: String, host: String, port: Int, timeoutMs: Int, tls: Boolean, done: (Result<Int>) -> Unit) {
        if (host.isBlank()) throw HaloneException("invalid-params", "host is empty")
        if (port !in 1..65535) throw HaloneException("invalid-params", "port must be between 1 and 65535")
        if (connections.values.count { it.owner == owner } >= MAX_PER_WINDOW) {
            throw HaloneException("too-many-sockets", "a window may have $MAX_PER_WINDOW sockets open")
        }
        connecting.execute {
            val result = runCatching { open(host, port, timeoutMs.coerceIn(1, MAX_TIMEOUT_MS), tls) }
                .recoverCatching { throw HaloneException("connect-failed", "cannot connect to $host:$port: ${it.message ?: it}") }
                .map { socket ->
                    val connection = Connection(nextId.getAndIncrement(), owner, socket)
                    connections[connection.id] = connection
                    Thread({ read(connection) }, "halone-socket-${connection.id}").apply { isDaemon = true }.start()
                    connection.id
                }
            onMain { done(result) }
        }
    }

    /** Sends [base64] bytes; [done] hears once they are handed to the network. */
    fun write(owner: String, id: Int, base64: String, done: (Result<Unit>) -> Unit) {
        val connection = find(owner, id)
        val bytes = runCatching { Base64.getDecoder().decode(base64) }.getOrElse { throw HaloneException("invalid-params", "data must be base64") }
        connection.writer.execute {
            val result = runCatching {
                connection.socket.getOutputStream().apply {
                    write(bytes)
                    flush()
                }
                Unit
            }.recoverCatching { throw HaloneException("socket-closed", "socket $id is closed") }
            onMain { done(result) }
        }
    }

    /** The page has handled one `socket.data` event. */
    fun ack(owner: String, id: Int) {
        val connection = find(owner, id)
        if (connection.credit.availablePermits() < WINDOW) connection.credit.release()
    }

    fun close(owner: String, id: Int) = shut(find(owner, id), error = null, tell = false)

    /** Closes the sockets of windows not in [open]. */
    fun windowsChanged(open: Set<String>) {
        connections.values.filter { it.owner !in open }.forEach { shut(it, error = null, tell = false) }
    }

    fun closeAll() {
        connections.values.forEach { shut(it, error = null, tell = false) }
        connecting.shutdownNow()
    }

    fun openCount(owner: String) = connections.values.count { it.owner == owner }

    private fun find(owner: String, id: Int): Connection =
        connections[id]?.takeIf { it.owner == owner } ?: throw HaloneException("no-such-socket", "no such socket: $id")

    private fun read(connection: Connection) {
        val buffer = ByteArray(CHUNK_BYTES)
        val error = runCatching {
            val input = connection.socket.getInputStream()
            while (true) {
                connection.credit.acquire()
                val count = input.read(buffer)
                if (count < 0) break
                val data = Base64.getEncoder().encodeToString(buffer.copyOf(count))
                onMain { if (!connection.closed) onEvent?.invoke(connection.owner, "socket.data", JSONObject().put("socketId", connection.id).put("data", data)) }
            }
        }.exceptionOrNull()
        shut(connection, error = error?.takeIf { !connection.closed }?.let { it.message ?: it.toString() }, tell = true)
    }

    private fun shut(connection: Connection, error: String?, tell: Boolean) {
        val first = connections.remove(connection.id) != null
        connection.closed = true
        runCatching { connection.socket.close() }
        connection.credit.release(WINDOW)
        connection.writer.shutdown()
        if (first && tell) {
            onMain {
                onEvent?.invoke(
                    connection.owner,
                    "socket.closed",
                    JSONObject().put("socketId", connection.id).put("error", error ?: JSONObject.NULL),
                )
            }
        }
    }

    companion object {
        const val CHUNK_BYTES = 64 * 1024
        const val WINDOW = 8
        const val MAX_PER_WINDOW = 16
        const val MAX_TIMEOUT_MS = 60_000

        fun openSocket(host: String, port: Int, timeoutMs: Int, tls: Boolean): Socket {
            val plain = Socket().apply {
                tcpNoDelay = true
                connect(InetSocketAddress(host, port), timeoutMs)
            }
            if (!tls) return plain
            val secure = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(plain, host, port, true) as SSLSocket
            secure.sslParameters = secure.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
            secure.startHandshake()
            return secure
        }
    }
}
