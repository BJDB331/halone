package dev.halone.glasses.transport.usbnet

/**
 * Just enough Ethernet, ARP, IPv4, UDP and TCP to reach a device on a
 * point-to-point USB link. No options beyond TCP MSS, no fragmentation, no IPv6.
 */
internal object Packets {
    const val ETH_HEADER = 14
    const val IP_HEADER = 20
    const val TCP_HEADER = 20
    const val UDP_HEADER = 8

    const val ETHERTYPE_IPV4 = 0x0800
    const val ETHERTYPE_ARP = 0x0806
    const val PROTO_TCP = 6
    const val PROTO_UDP = 17

    const val TCP_FIN = 0x01
    const val TCP_SYN = 0x02
    const val TCP_RST = 0x04
    const val TCP_PSH = 0x08
    const val TCP_ACK = 0x10

    val BROADCAST_MAC = ByteArray(6) { 0xFF.toByte() }

    fun u16(b: ByteArray, at: Int) = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

    fun u32(b: ByteArray, at: Int): Long =
        ((b[at].toLong() and 0xFF) shl 24) or ((b[at + 1].toLong() and 0xFF) shl 16) or
            ((b[at + 2].toLong() and 0xFF) shl 8) or (b[at + 3].toLong() and 0xFF)

    fun put16(b: ByteArray, at: Int, v: Int) {
        b[at] = (v ushr 8).toByte()
        b[at + 1] = v.toByte()
    }

    fun put32(b: ByteArray, at: Int, v: Long) {
        b[at] = (v ushr 24).toByte()
        b[at + 1] = (v ushr 16).toByte()
        b[at + 2] = (v ushr 8).toByte()
        b[at + 3] = v.toByte()
    }

    /** Ones' complement sum, folded but not inverted, so pseudo-headers can be chained in. */
    fun sum(b: ByteArray, at: Int, length: Int, initial: Long = 0): Long {
        var s = initial
        var i = at
        val end = at + length
        while (i + 1 < end) {
            s += u16(b, i)
            i += 2
        }
        if (i < end) s += (b[i].toInt() and 0xFF) shl 8
        while (s ushr 16 != 0L) s = (s and 0xFFFF) + (s ushr 16)
        return s
    }

    fun checksum(b: ByteArray, at: Int, length: Int, initial: Long = 0): Int = (sum(b, at, length, initial).inv() and 0xFFFF).toInt()

    fun ethernet(dst: ByteArray, src: ByteArray, type: Int, payloadLength: Int): ByteArray {
        val frame = ByteArray(ETH_HEADER + payloadLength)
        dst.copyInto(frame, 0)
        src.copyInto(frame, 6)
        put16(frame, 12, type)
        return frame
    }

    fun arp(op: Int, srcMac: ByteArray, srcIp: Int, dstMac: ByteArray, dstIp: Int, frameDst: ByteArray): ByteArray {
        val f = ethernet(frameDst, srcMac, ETHERTYPE_ARP, 28)
        val a = ETH_HEADER
        put16(f, a, 1)
        put16(f, a + 2, ETHERTYPE_IPV4)
        f[a + 4] = 6
        f[a + 5] = 4
        put16(f, a + 6, op)
        srcMac.copyInto(f, a + 8)
        put32(f, a + 14, srcIp.toLong() and 0xFFFFFFFFL)
        dstMac.copyInto(f, a + 18)
        put32(f, a + 24, dstIp.toLong() and 0xFFFFFFFFL)
        return f
    }

    /** Fills in an IPv4 header at [ETH_HEADER] for a frame already sized for its payload. */
    fun ipv4(frame: ByteArray, protocol: Int, src: Int, dst: Int, id: Int) {
        val at = ETH_HEADER
        val total = frame.size - ETH_HEADER
        frame[at] = 0x45
        frame[at + 1] = 0
        put16(frame, at + 2, total)
        put16(frame, at + 4, id and 0xFFFF)
        put16(frame, at + 6, 0x4000)
        frame[at + 8] = 64
        frame[at + 9] = protocol.toByte()
        put16(frame, at + 10, 0)
        put32(frame, at + 12, src.toLong() and 0xFFFFFFFFL)
        put32(frame, at + 16, dst.toLong() and 0xFFFFFFFFL)
        put16(frame, at + 10, checksum(frame, at, IP_HEADER))
    }

    fun pseudoHeaderSum(src: Int, dst: Int, protocol: Int, length: Int): Long {
        var s = 0L
        s += (src ushr 16) and 0xFFFF
        s += src and 0xFFFF
        s += (dst ushr 16) and 0xFFFF
        s += dst and 0xFFFF
        s += protocol
        s += length
        return s
    }

    fun tcp(
        srcMac: ByteArray, dstMac: ByteArray, src: Int, dst: Int, ipId: Int,
        srcPort: Int, dstPort: Int, seq: Long, ack: Long, flags: Int, window: Int,
        payload: ByteArray = EMPTY, payloadAt: Int = 0, payloadLength: Int = 0, mss: Int = 0,
    ): ByteArray {
        val options = if (mss > 0) 4 else 0
        val tcpLength = TCP_HEADER + options + payloadLength
        val f = ethernet(dstMac, srcMac, ETHERTYPE_IPV4, IP_HEADER + tcpLength)
        val t = ETH_HEADER + IP_HEADER
        put16(f, t, srcPort)
        put16(f, t + 2, dstPort)
        put32(f, t + 4, seq and 0xFFFFFFFFL)
        put32(f, t + 8, ack and 0xFFFFFFFFL)
        f[t + 12] = (((TCP_HEADER + options) / 4) shl 4).toByte()
        f[t + 13] = flags.toByte()
        put16(f, t + 14, window)
        if (mss > 0) {
            f[t + 20] = 2
            f[t + 21] = 4
            put16(f, t + 22, mss)
        }
        if (payloadLength > 0) System.arraycopy(payload, payloadAt, f, t + TCP_HEADER + options, payloadLength)
        put16(f, t + 16, checksum(f, t, tcpLength, pseudoHeaderSum(src, dst, PROTO_TCP, tcpLength)))
        ipv4(f, PROTO_TCP, src, dst, ipId)
        return f
    }

    fun udp(srcMac: ByteArray, dstMac: ByteArray, src: Int, dst: Int, ipId: Int, srcPort: Int, dstPort: Int, payload: ByteArray): ByteArray {
        val udpLength = UDP_HEADER + payload.size
        val f = ethernet(dstMac, srcMac, ETHERTYPE_IPV4, IP_HEADER + udpLength)
        val u = ETH_HEADER + IP_HEADER
        put16(f, u, srcPort)
        put16(f, u + 2, dstPort)
        put16(f, u + 4, udpLength)
        payload.copyInto(f, u + UDP_HEADER)
        put16(f, u + 6, checksum(f, u, udpLength, pseudoHeaderSum(src, dst, PROTO_UDP, udpLength)).let { if (it == 0) 0xFFFF else it })
        ipv4(f, PROTO_UDP, src, dst, ipId)
        return f
    }

    /** A received IPv4 packet's essentials, or null if it is not one we can use. */
    class Ip(val src: Int, val dst: Int, val protocol: Int, val payloadAt: Int, val payloadLength: Int)

    fun parseIp(f: ByteArray, length: Int): Ip? {
        if (length < ETH_HEADER + IP_HEADER || u16(f, 12) != ETHERTYPE_IPV4) return null
        val at = ETH_HEADER
        if ((f[at].toInt() ushr 4) != 4) return null
        val headerLength = (f[at].toInt() and 0x0F) * 4
        val total = u16(f, at + 2)
        if (headerLength < IP_HEADER || total < headerLength || at + total > length) return null
        val fragment = u16(f, at + 6)
        if (fragment and 0x3FFF != 0) return null
        return Ip(
            src = u32(f, at + 12).toInt(),
            dst = u32(f, at + 16).toInt(),
            protocol = f[at + 9].toInt() and 0xFF,
            payloadAt = at + headerLength,
            payloadLength = total - headerLength,
        )
    }

    /** USB already checks each transfer, so a mismatch is counted for diagnosis rather than dropped. */
    fun checksumsOk(f: ByteArray, ip: Ip): Boolean =
        checksum(f, ETH_HEADER, ip.payloadAt - ETH_HEADER) == 0 &&
            (ip.protocol != PROTO_TCP ||
                checksum(f, ip.payloadAt, ip.payloadLength, pseudoHeaderSum(ip.src, ip.dst, PROTO_TCP, ip.payloadLength)) == 0)

    class Tcp(
        val srcPort: Int, val dstPort: Int, val seq: Long, val ack: Long, val flags: Int, val window: Int,
        val dataAt: Int, val dataLength: Int, val mss: Int,
    )

    fun parseTcp(f: ByteArray, ip: Ip): Tcp? {
        val t = ip.payloadAt
        if (ip.payloadLength < TCP_HEADER) return null
        val offset = ((f[t + 12].toInt() ushr 4) and 0x0F) * 4
        if (offset < TCP_HEADER || offset > ip.payloadLength) return null
        var mss = 0
        var o = t + TCP_HEADER
        while (o < t + offset) {
            when (f[o].toInt() and 0xFF) {
                0 -> break
                1 -> o++
                else -> {
                    val kind = f[o].toInt() and 0xFF
                    val len = f[o + 1].toInt() and 0xFF
                    if (len < 2) break
                    if (kind == 2 && len == 4) mss = u16(f, o + 2)
                    o += len
                }
            }
        }
        return Tcp(
            srcPort = u16(f, t), dstPort = u16(f, t + 2),
            seq = u32(f, t + 4), ack = u32(f, t + 8),
            flags = f[t + 13].toInt() and 0x3F, window = u16(f, t + 14),
            dataAt = t + offset, dataLength = ip.payloadLength - offset, mss = mss,
        )
    }

    fun ipToString(ip: Int) = "${(ip ushr 24) and 0xFF}.${(ip ushr 16) and 0xFF}.${(ip ushr 8) and 0xFF}.${ip and 0xFF}"

    fun parseIpv4(text: String): Int {
        val parts = text.split('.').map { it.toInt() }
        require(parts.size == 4 && parts.all { it in 0..255 }) { "not an IPv4 address: $text" }
        return (parts[0] shl 24) or (parts[1] shl 16) or (parts[2] shl 8) or parts[3]
    }

    fun macToString(mac: ByteArray) = mac.joinToString(":") { "%02x".format(it) }

    val EMPTY = ByteArray(0)
}

/** Sequence-space comparisons, modulo 2^32. */
internal fun seqLt(a: Long, b: Long) = ((a - b) and 0xFFFFFFFFL) > 0x7FFFFFFFL
internal fun seqLe(a: Long, b: Long) = a == b || seqLt(a, b)
internal fun seqAdd(a: Long, n: Int) = (a + n) and 0xFFFFFFFFL
internal fun seqDiff(a: Long, b: Long) = ((a - b) and 0xFFFFFFFFL).toInt()
