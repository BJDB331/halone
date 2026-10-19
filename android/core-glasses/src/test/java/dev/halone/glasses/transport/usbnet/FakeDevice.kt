package dev.halone.glasses.transport.usbnet

import java.io.ByteArrayOutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Two queues of frames: what the host sends and what the device sends back. */
class FramePipe : EthernetLink {
    val toDevice = LinkedBlockingQueue<ByteArray>()
    val toHost = LinkedBlockingQueue<ByteArray>()

    @Volatile
    var closed = false

    override fun send(frame: ByteArray): Boolean {
        if (closed) return false
        toDevice.put(frame.copyOf())
        return true
    }

    override fun receive(into: ByteArray, timeoutMs: Int): Int {
        if (closed) return -1
        val frame = toHost.poll(timeoutMs.toLong(), TimeUnit.MILLISECONDS) ?: return 0
        frame.copyInto(into)
        return frame.size
    }

    override fun close() {
        closed = true
    }
}

/**
 * Plays the glasses on the far end of a [FramePipe]: ARP, an optional DHCP
 * server, and a TCP server per port whose behaviour a test supplies. Drops
 * every [dropEvery]th outgoing data segment the first time it is sent, so the
 * host has to cope with loss the way a real link occasionally demands.
 */
class FakeDevice(
    private val pipe: FramePipe,
    val ip: Int = Packets.parseIpv4("169.254.1.1"),
    private val serveDhcp: Boolean = true,
    private val dropEvery: Int = 0,
) : AutoCloseable {

    val mac = byteArrayOf(0x02, 0x11, 0x22, 0x33, 0x44, 0x55)
    val leased = Packets.parseIpv4("169.254.1.10")

    /** Bytes to send once a connection on a port is established. */
    val greetings = mutableMapOf<Int, ByteArray>()

    /** Produces a reply to bytes the host sent on a port. */
    val handlers = mutableMapOf<Int, (ByteArray) -> ByteArray>()

    /** Ports that answer a SYN with RST. */
    val refused = mutableSetOf<Int>()

    /** Closes from the device side once everything queued is acknowledged. */
    val finishAfterGreeting = mutableSetOf<Int>()

    var dhcpRequests = 0
        private set

    private class Peer(val hostMac: ByteArray, val hostIp: Int, val hostPort: Int, val port: Int) {
        val iss = Random.nextLong(0, 0xFFFFFFFFL)
        val dataStart = seqAdd(iss, 1)
        var sndUna = iss
        var sndNxt = dataStart
        var rcvNxt = 0L
        var hostWindow = 0
        var established = false
        val outgoing = ByteArrayOutputStream()
        var highestSent = 0
        var lastSendMs = 0L
        var finQueued = false
        var finSent = false
        var segmentsSent = 0
    }

    private val peers = mutableMapOf<Int, Peer>()
    private var ipId = 1

    @Volatile
    private var running = true
    private val thread = Thread(::loop, "fake-device").apply { isDaemon = true; start() }

    private fun loop() {
        while (running) {
            val frame = pipe.toDevice.poll(5, TimeUnit.MILLISECONDS)
            if (frame != null) handle(frame)
            peers.values.toList().forEach { pump(it) }
        }
    }

    private fun handle(f: ByteArray) {
        when (Packets.u16(f, 12)) {
            Packets.ETHERTYPE_ARP -> {
                val a = Packets.ETH_HEADER
                val target = Packets.u32(f, a + 24).toInt()
                if (Packets.u16(f, a + 6) == 1 && target == ip) {
                    val senderMac = f.copyOfRange(a + 8, a + 14)
                    val senderIp = Packets.u32(f, a + 14).toInt()
                    emit(Packets.arp(2, mac, ip, senderMac, senderIp, senderMac))
                }
            }
            Packets.ETHERTYPE_IPV4 -> {
                val packet = Packets.parseIp(f, f.size) ?: return
                check(Packets.checksumsOk(f, packet)) { "host sent a bad checksum" }
                when (packet.protocol) {
                    Packets.PROTO_UDP -> if (serveDhcp) dhcp(f, packet)
                    Packets.PROTO_TCP -> tcp(f, packet, Packets.parseTcp(f, packet)!!)
                }
            }
        }
    }

    private fun dhcp(f: ByteArray, packet: Packets.Ip) {
        val b = packet.payloadAt + Packets.UDP_HEADER
        if (Packets.u16(f, packet.payloadAt + 2) != 67) return
        dhcpRequests++
        val xid = Packets.u32(f, b + 4)
        val chaddr = f.copyOfRange(b + 28, b + 34)
        var type = 0
        var o = b + 240
        while (o < f.size && (f[o].toInt() and 0xFF) != 0xFF) {
            if (f[o].toInt() == 53) type = f[o + 2].toInt()
            o += 2 + (f[o + 1].toInt() and 0xFF)
        }
        val replyType = when (type) {
            1 -> 2
            3 -> 5
            else -> return
        }
        val body = ByteArray(240)
        body[0] = 2
        body[1] = 1
        body[2] = 6
        Packets.put32(body, 4, xid)
        Packets.put32(body, 16, leased.toLong() and 0xFFFFFFFFL)
        chaddr.copyInto(body, 28)
        byteArrayOf(99, 130.toByte(), 83, 99).copyInto(body, 236)
        val options = ByteArrayOutputStream().apply {
            write(byteArrayOf(53, 1, replyType.toByte()))
            write(byteArrayOf(54, 4))
            write(ByteArray(4).also { Packets.put32(it, 0, ip.toLong() and 0xFFFFFFFFL) })
            write(0xFF)
        }.toByteArray()
        emit(Packets.udp(mac, chaddr, ip, -1, ipId++, 67, 68, body + options))
    }

    private fun tcp(f: ByteArray, packet: Packets.Ip, t: Packets.Tcp) {
        val hostMac = f.copyOfRange(6, 12)
        if (t.flags and Packets.TCP_SYN != 0) {
            if (t.dstPort in refused) {
                emit(Packets.tcp(mac, hostMac, ip, packet.src, ipId++, t.dstPort, t.srcPort, 0, seqAdd(t.seq, 1), Packets.TCP_RST or Packets.TCP_ACK, 0))
                return
            }
            val peer = peers.getOrPut(t.srcPort) { Peer(hostMac, packet.src, t.srcPort, t.dstPort) }
            peer.rcvNxt = seqAdd(t.seq, 1)
            peer.hostWindow = t.window
            emit(Packets.tcp(mac, hostMac, ip, packet.src, ipId++, t.dstPort, t.srcPort, peer.iss, peer.rcvNxt, Packets.TCP_SYN or Packets.TCP_ACK, 65535, mss = 1460))
            return
        }
        val peer = peers[t.srcPort] ?: return
        if (t.flags and Packets.TCP_ACK != 0) {
            if (seqLt(peer.sndUna, t.ack) && seqLe(t.ack, peer.sndNxt)) peer.sndUna = t.ack
            peer.hostWindow = t.window
            if (!peer.established && peer.sndUna == peer.dataStart) {
                peer.established = true
                greetings.remove(peer.port)?.let { peer.outgoing.write(it) }
                if (peer.port in finishAfterGreeting) peer.finQueued = true
            }
        }
        if (t.dataLength > 0) {
            if (t.seq == peer.rcvNxt) {
                val bytes = f.copyOfRange(t.dataAt, t.dataAt + t.dataLength)
                peer.rcvNxt = seqAdd(peer.rcvNxt, t.dataLength)
                handlers[peer.port]?.let { peer.outgoing.write(it(bytes)) }
            }
            emit(Packets.tcp(mac, peer.hostMac, ip, peer.hostIp, ipId++, peer.port, peer.hostPort, peer.sndNxt, peer.rcvNxt, Packets.TCP_ACK, 65535))
        }
    }

    /** Go-back-N from the unacknowledged edge: send while the host's window allows, rewind after a quiet 150 ms. */
    private fun pump(peer: Peer) {
        if (!peer.established) return
        val data = peer.outgoing.toByteArray()
        val acked = seqDiff(peer.sndUna, peer.dataStart)
        val now = System.nanoTime() / 1_000_000
        if (seqDiff(peer.sndNxt, peer.sndUna) > 0 && now - peer.lastSendMs > REWIND_MS) peer.sndNxt = peer.sndUna
        while (true) {
            val inFlight = seqDiff(peer.sndNxt, peer.sndUna)
            val offset = acked + inFlight
            if (offset >= data.size || inFlight >= peer.hostWindow) break
            val n = minOf(1460, data.size - offset, peer.hostWindow - inFlight)
            val firstTime = offset >= peer.highestSent
            if (firstTime) peer.segmentsSent++
            val drop = firstTime && dropEvery > 0 && peer.segmentsSent % dropEvery == 0
            if (!drop) {
                emit(Packets.tcp(mac, peer.hostMac, ip, peer.hostIp, ipId++, peer.port, peer.hostPort, peer.sndNxt, peer.rcvNxt, Packets.TCP_ACK or Packets.TCP_PSH, 65535, data, offset, n))
            }
            peer.highestSent = maxOf(peer.highestSent, offset + n)
            peer.sndNxt = seqAdd(peer.sndNxt, n)
            peer.lastSendMs = now
        }
        if (peer.finQueued && !peer.finSent && acked >= data.size) {
            peer.finSent = true
            emit(Packets.tcp(mac, peer.hostMac, ip, peer.hostIp, ipId++, peer.port, peer.hostPort, peer.sndNxt, peer.rcvNxt, Packets.TCP_FIN or Packets.TCP_ACK, 65535))
        }
    }

    private fun emit(frame: ByteArray) = pipe.toHost.put(frame)

    override fun close() {
        running = false
        thread.join(500)
    }

    private companion object {
        const val REWIND_MS = 150L
    }
}
