package dev.halone.testkit.capture

import dev.halone.testkit.engine.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.Socket
import java.net.SocketTimeoutException

/** A passive window onto one TCP connection. */
interface Tap : AutoCloseable {
    val bytesIn: Long
    val alive: Boolean

    /** Sends and records [bytes]. */
    suspend fun send(bytes: ByteArray): Boolean
}

/**
 * Reads [socket] for the whole session so the device never backs up, but only
 * writes chunks to [file] while the session is recording, unless [alwaysRecord].
 * [listener] sees every chunk, recorded or not, on the reader thread.
 */
class TcpTap(
    name: String,
    private val socket: Socket,
    private val session: Session,
    file: File,
    private val alwaysRecord: Boolean,
    private val listener: ((ByteArray, Int) -> Unit)?,
) : Tap {

    private val out = DataOutputStream(BufferedOutputStream(FileOutputStream(file), BUFFER))
    private val writeLock = Any()

    @Volatile
    override var bytesIn = 0L
        private set

    @Volatile
    override var alive = true
        private set

    private val thread = Thread(::readLoop, "tap-$name").apply {
        isDaemon = true
        start()
    }

    override suspend fun send(bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (!alive) return@withContext false
        try {
            synchronized(writeLock) { record(DIRECTION_OUT, bytes, bytes.size) }
            socket.getOutputStream().apply {
                write(bytes)
                flush()
            }
            true
        } catch (_: IOException) {
            false
        }
    }

    private fun readLoop() {
        val buffer = ByteArray(READ_CHUNK)
        val input = try {
            socket.soTimeout = READ_TIMEOUT_MS
            socket.getInputStream()
        } catch (_: IOException) {
            alive = false
            return
        }
        while (alive) {
            val n = try {
                input.read(buffer)
            } catch (_: SocketTimeoutException) {
                continue
            } catch (_: IOException) {
                -1
            }
            if (n < 0) break
            bytesIn += n
            listener?.invoke(buffer, n)
            if (alwaysRecord || session.recording) {
                synchronized(writeLock) { record(DIRECTION_IN, buffer, n) }
            }
        }
        alive = false
    }

    private fun record(direction: Int, bytes: ByteArray, length: Int) {
        try {
            out.writeLong(session.now())
            out.writeByte(direction)
            out.writeInt(length)
            out.write(bytes, 0, length)
        } catch (_: IOException) {
        }
    }

    override fun close() {
        alive = false
        runCatching { socket.close() }
        thread.join(1_000)
        synchronized(writeLock) { runCatching { out.close() } }
    }

    private companion object {
        const val DIRECTION_IN = 0
        const val DIRECTION_OUT = 1
        const val READ_CHUNK = 16 * 1024
        const val READ_TIMEOUT_MS = 250
        const val BUFFER = 64 * 1024
    }
}
