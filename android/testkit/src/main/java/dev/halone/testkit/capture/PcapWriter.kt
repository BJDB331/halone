package dev.halone.testkit.capture

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Link-level frames in classic pcap, for reading in Wireshark. The first
 * [fullFrames] frames are kept whole; after that, sensor-stream data is left
 * out, since the `.cap` files already hold it and it would dwarf everything else.
 */
class PcapWriter(file: File, private val skipPort: Int, private val fullFrames: Int = 3_000) : Closeable {
    private val out = DataOutputStream(BufferedOutputStream(FileOutputStream(file), 64 * 1024))
    private var written = 0
    private var closed = false

    init {
        out.writeInt(0xA1B2C3D4.toInt())
        out.writeShort(2)
        out.writeShort(4)
        out.writeInt(0)
        out.writeInt(0)
        out.writeInt(65535)
        out.writeInt(1)
    }

    @Synchronized
    fun write(frame: ByteArray, length: Int) {
        if (closed) return
        if (written >= fullFrames && isStreamData(frame, length)) return
        val micros = System.currentTimeMillis() * 1000 + (System.nanoTime() / 1000) % 1000
        runCatching {
            out.writeInt((micros / 1_000_000).toInt())
            out.writeInt((micros % 1_000_000).toInt())
            out.writeInt(length)
            out.writeInt(length)
            out.write(frame, 0, length)
            written++
        }
    }

    private fun isStreamData(f: ByteArray, n: Int): Boolean {
        if (n < 14 + 20 + 20) return false
        if (f[12].toInt() != 0x08 || f[13].toInt() != 0x00 || f[23].toInt() != 6) return false
        val t = 14 + (f[14].toInt() and 0x0F) * 4
        val src = ((f[t].toInt() and 0xFF) shl 8) or (f[t + 1].toInt() and 0xFF)
        val dst = ((f[t + 2].toInt() and 0xFF) shl 8) or (f[t + 3].toInt() and 0xFF)
        return src == skipPort || dst == skipPort
    }

    @Synchronized
    override fun close() {
        closed = true
        runCatching { out.close() }
    }
}
