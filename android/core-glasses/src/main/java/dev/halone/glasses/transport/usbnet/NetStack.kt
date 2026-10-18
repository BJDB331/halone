package dev.halone.glasses.transport.usbnet

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/** One Ethernet frame at a time over some link: a USB ECM interface, or a fake in tests. */
interface EthernetLink : Closeable {
    fun send(frame: ByteArray): Boolean

    /** Bytes of one frame, 0 on timeout, -1 once the link is gone. */
    fun receive(into: ByteArray, timeoutMs: Int): Int
}

/**
 * The host side of a point-to-point link to one device, run in-process for
 * phones that never bring the device's USB network up themselves.
 *
 * Gets an address by DHCP from the device, falling back to [fallbackIp];
 * answers ARP for it; and carries [TcpConnection]s. [onFrame] sees every frame
 * in both directions, for capture.
 */
class NetStack(
    private val link: EthernetLink,
    private val log: (String) -> Unit = {},
    private val onFrame: ((outbound: Boolean, frame: ByteArray, length: Int) -> Unit)? = null,
    val localMac: ByteArray = randomMac(),
) : Closeable {

    @Volatile
    var localIp: Int = 0
        private set

    /** The device's address as DHCP named it, or null when DHCP got no answer. */
    @Volatile
    var deviceIp: Int? = null
        private set

    var badChecksums = 0L
        private set

    private val sendLock = Any()
    private val ipId = AtomicInteger(Random.nextInt(0, 0xFFFF))
    private val arpCache = ConcurrentHashMap<Int, ByteArray>()
    private val arpWaiters = ConcurrentHashMap<Int, CountDownLatch>()
    private val connections = ConcurrentHashMap<Long, TcpConnection>()

    @Volatile
    private var running = false
    private var thread: Thread? = null

    @Volatile
    private var dhcp: DhcpClient? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread(::readLoop, "usbnet").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    /** Leases an address from the device, or takes [fallbackIp] if nobody answers. */
    fun configure(fallbackIp: Int, timeoutMs: Long = DHCP_TIMEOUT_MS) {
        val client = DhcpClient(this)
        dhcp = client
        val lease = client.run(timeoutMs)
        dhcp = null
        if (lease != null) {
            localIp = lease.address
            deviceIp = lease.server
            log("dhcp: ${Packets.ipToString(lease.address)} from ${Packets.ipToString(lease.server)}")
        } else {
            localIp = fallbackIp
            log("dhcp: no answer, using ${Packets.ipToString(fallbackIp)}")
        }
    }

    fun resolve(ip: Int, timeoutMs: Long = ARP_TIMEOUT_MS): ByteArray? {
        arpCache[ip]?.let { return it }
        val latch = arpWaiters.getOrPut(ip) { CountDownLatch(1) }
        repeat(ARP_ATTEMPTS) {
            send(Packets.arp(OP_REQUEST, localMac, localIp, ByteArray(6), ip, Packets.BROADCAST_MAC))
            if (latch.await(timeoutMs / ARP_ATTEMPTS, TimeUnit.MILLISECONDS)) return arpCache[ip]
        }
        arpWaiters.remove(ip)
        return arpCache[ip]
    }

    fun connect(ip: Int, port: Int, timeoutMs: Long = CONNECT_TIMEOUT_MS): Result<TcpConnection> {
        val mac = resolve(ip) ?: return Result.failure(IOException("no ARP reply from ${Packets.ipToString(ip)}"))
        var localPort: Int
        do {
            localPort = Random.nextInt(EPHEMERAL_FIRST, EPHEMERAL_LAST)
        } while (connections.containsKey(key(port, localPort)))
        val connection = TcpConnection(this, localPort, ip, port, mac)
        connections[key(port, localPort)] = connection
        if (!connection.open(timeoutMs)) {
            connection.close()
            return Result.failure(IOException("no answer from ${Packets.ipToString(ip)}:$port"))
        }
        return Result.success(connection)
    }

    override fun close() {
        connections.values.toList().forEach { it.close() }
        running = false
        runCatching { link.close() }
        thread?.join(500)
        thread = null
    }

    internal fun forget(connection: TcpConnection) {
        connections.remove(key(connection.remotePort, connection.localPort))
    }

    internal fun nextIpId() = ipId.incrementAndGet() and 0xFFFF

    internal fun send(frame: ByteArray): Boolean {
        onFrame?.invoke(true, frame, frame.size)
        return synchronized(sendLock) { link.send(frame) }
    }

    private fun readLoop() {
        val frame = ByteArray(MAX_FRAME)
        var lastTick = 0L
        while (running) {
            val n = link.receive(frame, RECEIVE_TIMEOUT_MS)
            if (n < 0) {
                log("link closed")
                running = false
                connections.values.toList().forEach { it.close() }
                break
            }
            if (n > 0) {
                onFrame?.invoke(false, frame, n)
                runCatching { dispatch(frame, n) }.onFailure { log("dropped a frame: ${it.message}") }
            }
            val now = System.nanoTime() / 1_000_000
            if (now - lastTick >= TICK_MS) {
                lastTick = now
                connections.values.forEach { it.tick(now) }
            }
        }
    }

    private fun dispatch(f: ByteArray, n: Int) {
        if (n < Packets.ETH_HEADER) return
        when (Packets.u16(f, 12)) {
            Packets.ETHERTYPE_ARP -> onArp(f, n)
            Packets.ETHERTYPE_IPV4 -> {
                val ip = Packets.parseIp(f, n) ?: return
                if (!Packets.checksumsOk(f, ip)) badChecksums++
                when (ip.protocol) {
                    Packets.PROTO_TCP -> {
                        if (ip.dst != localIp) return
                        val tcp = Packets.parseTcp(f, ip) ?: return
                        arpCache.putIfAbsent(ip.src, f.copyOfRange(6, 12))
                        connections[key(tcp.srcPort, tcp.dstPort)]?.onSegment(f, tcp)
                    }
                    Packets.PROTO_UDP -> dhcp?.onUdp(f, ip)
                }
            }
        }
    }

    private fun onArp(f: ByteArray, n: Int) {
        if (n < Packets.ETH_HEADER + 28) return
        val a = Packets.ETH_HEADER
        val op = Packets.u16(f, a + 6)
        val senderMac = f.copyOfRange(a + 8, a + 14)
        val senderIp = Packets.u32(f, a + 14).toInt()
        val targetIp = Packets.u32(f, a + 24).toInt()
        if (senderIp != 0) {
            arpCache[senderIp] = senderMac
            arpWaiters.remove(senderIp)?.countDown()
        }
        if (op == OP_REQUEST && localIp != 0 && targetIp == localIp) {
            send(Packets.arp(OP_REPLY, localMac, localIp, senderMac, senderIp, senderMac))
        }
    }

    private fun key(remotePort: Int, localPort: Int) = (remotePort.toLong() shl 16) or localPort.toLong()

    companion object {
        const val MAX_FRAME = 2048
        private const val OP_REQUEST = 1
        private const val OP_REPLY = 2
        private const val RECEIVE_TIMEOUT_MS = 20
        private const val TICK_MS = 50L
        private const val ARP_ATTEMPTS = 4
        private const val ARP_TIMEOUT_MS = 2_000L
        private const val CONNECT_TIMEOUT_MS = 3_000L
        private const val DHCP_TIMEOUT_MS = 3_000L
        private const val EPHEMERAL_FIRST = 40_000
        private const val EPHEMERAL_LAST = 60_000

        /** Locally administered and unicast, so it cannot collide with a real vendor's. */
        fun randomMac(): ByteArray = Random.nextBytes(6).also {
            it[0] = ((it[0].toInt() and 0xFC) or 0x02).toByte()
        }
    }
}

/** DISCOVER, OFFER, REQUEST, ACK, and nothing else: enough to take a lease from a device that serves one. */
internal class DhcpClient(private val stack: NetStack) {

    class Lease(val address: Int, val server: Int)

    private val xid = Random.nextInt()
    private val offers = java.util.concurrent.LinkedBlockingQueue<Pair<Int, Lease>>()

    fun run(timeoutMs: Long): Lease? {
        val deadline = System.nanoTime() / 1_000_000 + timeoutMs
        val half = timeoutMs / 2
        val offer = exchange(TYPE_DISCOVER, null, TYPE_OFFER, minOf(half, remaining(deadline))) ?: return null
        return exchange(TYPE_REQUEST, offer, TYPE_ACK, remaining(deadline)) ?: offer
    }

    private fun remaining(deadline: Long) = (deadline - System.nanoTime() / 1_000_000).coerceAtLeast(0)

    private fun exchange(type: Int, offer: Lease?, expect: Int, timeoutMs: Long): Lease? {
        offers.clear()
        val deadline = System.nanoTime() / 1_000_000 + timeoutMs
        while (true) {
            stack.send(Packets.udp(stack.localMac, Packets.BROADCAST_MAC, 0, -1, stack.nextIpId(), CLIENT_PORT, SERVER_PORT, message(type, offer)))
            val wait = minOf(RESEND_MS, remaining(deadline))
            if (wait <= 0) return null
            val reply = offers.poll(wait, TimeUnit.MILLISECONDS) ?: continue
            if (reply.first == expect) return reply.second
        }
    }

    fun onUdp(f: ByteArray, ip: Packets.Ip) {
        val u = ip.payloadAt
        if (ip.payloadLength < Packets.UDP_HEADER + BOOTP_FIXED + 4) return
        if (Packets.u16(f, u + 2) != CLIENT_PORT) return
        val b = u + Packets.UDP_HEADER
        if (f[b].toInt() != 2 || Packets.u32(f, b + 4).toInt() != xid) return
        val yiaddr = Packets.u32(f, b + 16).toInt()
        var type = 0
        var server = ip.src
        var o = b + BOOTP_FIXED + 4
        val end = u + ip.payloadLength
        while (o < end) {
            val code = f[o].toInt() and 0xFF
            if (code == 0xFF) break
            if (code == 0) {
                o++
                continue
            }
            val len = f[o + 1].toInt() and 0xFF
            when (code) {
                53 -> type = f[o + 2].toInt() and 0xFF
                54 -> if (len == 4) server = Packets.u32(f, o + 2).toInt()
            }
            o += 2 + len
        }
        if (yiaddr != 0) offers.offer(type to Lease(yiaddr, server))
    }

    private fun message(type: Int, offer: Lease?): ByteArray {
        val out = ByteArrayOutputStream()
        val fixed = ByteArray(BOOTP_FIXED)
        fixed[0] = 1
        fixed[1] = 1
        fixed[2] = 6
        Packets.put32(fixed, 4, xid.toLong() and 0xFFFFFFFFL)
        Packets.put16(fixed, 10, 0x8000)
        stack.localMac.copyInto(fixed, 28)
        out.write(fixed)
        out.write(byteArrayOf(99, 130.toByte(), 83, 99))
        out.write(byteArrayOf(53, 1, type.toByte()))
        if (offer != null) {
            out.write(50)
            out.write(4)
            out.write(ByteArray(4).also { Packets.put32(it, 0, offer.address.toLong() and 0xFFFFFFFFL) })
            out.write(54)
            out.write(4)
            out.write(ByteArray(4).also { Packets.put32(it, 0, offer.server.toLong() and 0xFFFFFFFFL) })
        }
        out.write(byteArrayOf(55, 2, 1, 3))
        out.write(0xFF)
        return out.toByteArray()
    }

    private companion object {
        const val CLIENT_PORT = 68
        const val SERVER_PORT = 67
        const val BOOTP_FIXED = 236
        const val TYPE_DISCOVER = 1
        const val TYPE_OFFER = 2
        const val TYPE_REQUEST = 3
        const val TYPE_ACK = 5
        const val RESEND_MS = 700L
    }
}
