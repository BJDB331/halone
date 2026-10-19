package dev.halone.glasses.transport

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import javax.net.SocketFactory

/**
 * A TCP connection exposed as raw bytes. Unlike a HID endpoint a read returns
 * whatever part of the stream has arrived, so framing is the driver's job.
 *
 * [read] returns 0 on timeout and -1 once the connection is gone.
 */
class SocketTransport(
    override val name: String,
    private val socket: Socket,
) : HidTransport {

    private val input = socket.getInputStream()
    private val output = socket.getOutputStream()
    private val writeLock = Any()
    private var currentTimeoutMs = -1

    override fun write(packet: ByteArray, timeoutMs: Int): Int = synchronized(writeLock) {
        try {
            output.write(packet)
            output.flush()
            packet.size
        } catch (_: IOException) {
            -1
        }
    }

    override fun read(into: ByteArray, timeoutMs: Int): Int {
        try {
            if (timeoutMs != currentTimeoutMs) {
                socket.soTimeout = timeoutMs.coerceAtLeast(1)
                currentTimeoutMs = timeoutMs
            }
            return input.read(into)
        } catch (_: SocketTimeoutException) {
            return 0
        } catch (_: IOException) {
            return -1
        }
    }

    override fun close() {
        runCatching { socket.close() }
    }
}

/**
 * Opens TCP ports on whichever of [hosts] answers first, then sticks to it so
 * every port of one device lands on the same link.
 *
 * [factoryFor] picks the socket factory per host, which on Android is how a
 * connection is bound to the glasses' own network instead of the default route.
 */
class SocketTransportProvider(
    private val hosts: List<String>,
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val factoryFor: (host: String) -> SocketFactory = { SocketFactory.getDefault() },
) : HidTransportProvider, AutoCloseable {

    private val opened = mutableListOf<SocketTransport>()

    @Volatile
    var connectedHost: String? = null
        private set

    override fun open(interfaceId: Int): Result<HidTransport> {
        val port = interfaceId
        val ordered = listOfNotNull(connectedHost) + (hosts - setOfNotNull(connectedHost))
        val failures = mutableListOf<String>()

        for (host in ordered) {
            var socket: Socket? = null
            try {
                socket = factoryFor(host).createSocket().apply {
                    tcpNoDelay = true
                    keepAlive = true
                    connect(InetSocketAddress(host, port), connectTimeoutMs)
                }
            } catch (e: IOException) {
                runCatching { socket?.close() }
                failures += "$host: ${e.message ?: e.javaClass.simpleName}"
                continue
            }
            connectedHost = host
            val transport = SocketTransport("tcp:$host:$port", socket)
            synchronized(opened) { opened += transport }
            return Result.success(transport)
        }

        return Result.failure(IOException("could not reach port $port (${failures.joinToString("; ")})"))
    }

    override fun close() {
        synchronized(opened) {
            opened.forEach { it.close() }
            opened.clear()
        }
    }

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 2_000
    }
}
