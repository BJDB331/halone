package dev.halone.glasses.transport.usbnet

import dev.halone.glasses.transport.HidTransport
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.random.Random

/**
 * A TCP client over [NetStack], for one cable to one device: in-order receive
 * with an ACK per segment, retransmission on timeout, and nothing else. Reads
 * behave like a socket's: whatever has arrived, 0 on timeout, -1 once closed.
 */
class TcpConnection internal constructor(
    private val stack: NetStack,
    val localPort: Int,
    val remoteIp: Int,
    val remotePort: Int,
    private val remoteMac: ByteArray,
) : HidTransport {

    enum class State { SYN_SENT, ESTABLISHED, PEER_CLOSED, CLOSED }

    override val name = "usbnet:${Packets.ipToString(remoteIp)}:$remotePort"

    private val lock = ReentrantLock()
    private val changed = lock.newCondition()

    @Volatile
    var state = State.SYN_SENT
        private set

    private val iss = Random.nextLong(0, 0xFFFFFFFFL)
    private var sndUna = iss
    private var sndNxt = iss
    private var rcvNxt = 0L
    private var peerWindow = 0
    private var mss = DEFAULT_MSS
    private var lastAdvertised = 0

    private val buffer = ByteArray(RECEIVE_BUFFER)
    private var head = 0
    private var size = 0

    private class Segment(val seq: Long, val data: ByteArray, val flags: Int, var sentAtMs: Long, var retries: Int = 0)

    private val unacked = ArrayDeque<Segment>()

    var retransmissions = 0L
        private set

    var outOfOrderDrops = 0L
        private set

    internal fun open(timeoutMs: Long): Boolean = lock.withLock {
        transmit(Segment(sndNxt, Packets.EMPTY, Packets.TCP_SYN, now()))
        sndNxt = seqAdd(sndNxt, 1)
        val deadline = now() + timeoutMs
        while (state == State.SYN_SENT) {
            val left = deadline - now()
            if (left <= 0) break
            changed.await(left, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
        state == State.ESTABLISHED
    }

    override fun read(into: ByteArray, timeoutMs: Int): Int = lock.withLock {
        val deadline = now() + timeoutMs
        while (size == 0) {
            if (state == State.CLOSED || state == State.PEER_CLOSED) return -1
            val left = deadline - now()
            if (left <= 0) return 0
            changed.await(left, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
        val n = minOf(size, into.size)
        val first = minOf(n, buffer.size - head)
        System.arraycopy(buffer, head, into, 0, first)
        if (n > first) System.arraycopy(buffer, 0, into, first, n - first)
        head = (head + n) % buffer.size
        size -= n
        if (lastAdvertised < mss * 2 && window() >= mss * 4) sendAck()
        n
    }

    override fun write(packet: ByteArray, timeoutMs: Int): Int = lock.withLock {
        if (state != State.ESTABLISHED) return -1
        var at = 0
        val deadline = now() + timeoutMs
        while (at < packet.size) {
            val inFlight = seqDiff(sndNxt, sndUna)
            val room = minOf(mss, peerWindow - inFlight)
            if (room <= 0) {
                val left = deadline - now()
                if (left <= 0 || state != State.ESTABLISHED) return if (at > 0) at else -1
                changed.await(left, java.util.concurrent.TimeUnit.MILLISECONDS)
                continue
            }
            val n = minOf(room, packet.size - at)
            val data = packet.copyOfRange(at, at + n)
            transmit(Segment(sndNxt, data, Packets.TCP_ACK or Packets.TCP_PSH, now()))
            sndNxt = seqAdd(sndNxt, n)
            at += n
        }
        packet.size
    }

    override fun close() {
        lock.withLock {
            if (state == State.ESTABLISHED || state == State.PEER_CLOSED) {
                transmit(Segment(sndNxt, Packets.EMPTY, Packets.TCP_FIN or Packets.TCP_ACK, now()), remember = false)
                sndNxt = seqAdd(sndNxt, 1)
            }
            state = State.CLOSED
            unacked.clear()
            changed.signalAll()
        }
        stack.forget(this)
    }

    /** Called on the stack's reader thread for every segment addressed to this connection. */
    internal fun onSegment(frame: ByteArray, tcp: Packets.Tcp) = lock.withLock {
        if (tcp.flags and Packets.TCP_RST != 0) {
            state = State.CLOSED
            changed.signalAll()
            return
        }
        when (state) {
            State.SYN_SENT -> {
                val synAck = Packets.TCP_SYN or Packets.TCP_ACK
                if (tcp.flags and synAck == synAck && tcp.ack == sndNxt) {
                    rcvNxt = seqAdd(tcp.seq, 1)
                    sndUna = tcp.ack
                    unacked.clear()
                    peerWindow = tcp.window
                    if (tcp.mss > 0) mss = minOf(tcp.mss, DEFAULT_MSS)
                    state = State.ESTABLISHED
                    sendAck()
                    changed.signalAll()
                }
            }
            State.ESTABLISHED, State.PEER_CLOSED -> established(frame, tcp)
            State.CLOSED -> Unit
        }
    }

    private fun established(frame: ByteArray, tcp: Packets.Tcp) {
        if (tcp.flags and Packets.TCP_ACK != 0) {
            if (seqLt(sndUna, tcp.ack) && seqLe(tcp.ack, sndNxt)) {
                sndUna = tcp.ack
                while (unacked.isNotEmpty()) {
                    val s = unacked.first()
                    val end = seqAdd(s.seq, s.data.size + if (s.flags and (Packets.TCP_SYN or Packets.TCP_FIN) != 0) 1 else 0)
                    if (seqLe(end, sndUna)) unacked.removeFirst() else break
                }
            }
            peerWindow = tcp.window
            changed.signalAll()
        }

        var seq = tcp.seq
        var at = tcp.dataAt
        var length = tcp.dataLength
        if (length > 0 && seqLt(seq, rcvNxt)) {
            val overlap = seqDiff(rcvNxt, seq)
            if (overlap >= length) {
                length = 0
            } else {
                at += overlap
                length -= overlap
                seq = rcvNxt
            }
        }
        if (length > 0) {
            if (seq == rcvNxt && state == State.ESTABLISHED) {
                val n = minOf(length, buffer.size - size)
                val tail = (head + size) % buffer.size
                val first = minOf(n, buffer.size - tail)
                System.arraycopy(frame, at, buffer, tail, first)
                if (n > first) System.arraycopy(frame, at + first, buffer, 0, n - first)
                size += n
                rcvNxt = seqAdd(rcvNxt, n)
                changed.signalAll()
            } else {
                outOfOrderDrops++
            }
        }
        if (tcp.flags and Packets.TCP_FIN != 0 && seqAdd(tcp.seq, tcp.dataLength) == rcvNxt && state == State.ESTABLISHED) {
            rcvNxt = seqAdd(rcvNxt, 1)
            state = State.PEER_CLOSED
            changed.signalAll()
        }
        if (tcp.dataLength > 0 || tcp.flags and Packets.TCP_FIN != 0) sendAck()
    }

    /** Resends the oldest unacknowledged segment once its timer runs out. */
    internal fun tick(nowMs: Long) = lock.withLock {
        val oldest = unacked.firstOrNull() ?: return
        val timeout = minOf(MAX_RTO_MS, INITIAL_RTO_MS shl oldest.retries)
        if (nowMs - oldest.sentAtMs < timeout) return
        if (oldest.retries >= MAX_RETRIES) {
            state = State.CLOSED
            changed.signalAll()
            return
        }
        oldest.retries++
        oldest.sentAtMs = nowMs
        retransmissions++
        send(oldest)
    }

    private fun window() = minOf(MAX_WINDOW, buffer.size - size)

    private fun sendAck() {
        lastAdvertised = window()
        stack.send(
            Packets.tcp(
                stack.localMac, remoteMac, stack.localIp, remoteIp, stack.nextIpId(),
                localPort, remotePort, sndNxt, rcvNxt, Packets.TCP_ACK, lastAdvertised,
            )
        )
    }

    private fun transmit(segment: Segment, remember: Boolean = true) {
        if (remember) unacked.addLast(segment)
        send(segment)
    }

    private fun send(segment: Segment) {
        val syn = segment.flags and Packets.TCP_SYN != 0
        lastAdvertised = window()
        stack.send(
            Packets.tcp(
                stack.localMac, remoteMac, stack.localIp, remoteIp, stack.nextIpId(),
                localPort, remotePort, segment.seq, if (syn) 0 else rcvNxt, segment.flags, lastAdvertised,
                segment.data, 0, segment.data.size, mss = if (syn) DEFAULT_MSS else 0,
            )
        )
    }

    private fun now() = System.nanoTime() / 1_000_000

    private companion object {
        const val DEFAULT_MSS = 1460
        const val MAX_WINDOW = 65535
        const val RECEIVE_BUFFER = 512 * 1024
        const val INITIAL_RTO_MS = 200L
        const val MAX_RTO_MS = 3_000L
        const val MAX_RETRIES = 8
    }
}
