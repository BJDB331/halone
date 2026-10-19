package dev.halone.testkit.capture

import dev.halone.glasses.transport.HidTransport
import dev.halone.testkit.engine.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** A passive window onto one TCP connection, over a socket or the in-app stack. */
interface Tap : AutoCloseable {
    val bytesIn: Long
    val alive: Boolean

    /** Sends and records [bytes]. */
    suspend fun send(bytes: ByteArray): Boolean
}

/**
 * Reads [transport] for the whole session so the device never backs up, but only
 * writes chunks to [file] while the session is recording, unless [alwaysRecord].
 * [listener] sees every chunk, recorded or not, on the reader thread.
 */
class TcpTap(
    name: String,
    private val transport: HidTransport,
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
        synchronized(writeLock) { record(DIRECTION_OUT, bytes, bytes.size) }
        transport.write(bytes, WRITE_TIMEOUT_MS) == bytes.size
    }

    private fun readLoop() {
        val buffer = ByteArray(READ_CHUNK)
        while (alive) {
            val n = transport.read(buffer, READ_TIMEOUT_MS)
            if (n == 0) continue
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
        runCatching { transport.close() }
        thread.join(1_000)
        synchronized(writeLock) { runCatching { out.close() } }
    }

    private companion object {
        const val DIRECTION_IN = 0
        const val DIRECTION_OUT = 1
        const val READ_CHUNK = 16 * 1024
        const val READ_TIMEOUT_MS = 250
        const val WRITE_TIMEOUT_MS = 2_000
        const val BUFFER = 64 * 1024
    }
}
