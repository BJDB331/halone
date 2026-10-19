package dev.halone.glasses.transport.usbnet

import dev.halone.glasses.drivers.xrealone.XrealOneCaptures
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class NetStackTest {

    private val pipe = FramePipe()
    private val opened = mutableListOf<AutoCloseable>()

    @After
    fun tearDown() = opened.reversed().forEach { runCatching { it.close() } }

    private fun device(serveDhcp: Boolean = true, dropEvery: Int = 0) =
        FakeDevice(pipe, serveDhcp = serveDhcp, dropEvery = dropEvery).also { opened += it }

    private fun stack(): NetStack = NetStack(pipe).also {
        opened += it
        it.start()
        it.configure(fallbackIp = Packets.parseIpv4("169.254.1.20"), timeoutMs = 1_500)
    }

    private fun TcpConnection.readAll(expected: Int, timeoutMs: Long = 10_000): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (out.size() < expected && System.currentTimeMillis() < deadline) {
            val n = read(buffer, 100)
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    @Test
    fun `the header checksum matches a known example`() {
        val header = "4500 0073 0000 4000 4011 0000 c0a8 0001 c0a8 00c7"
            .replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertEquals(0xB861, Packets.checksum(header, 0, header.size))
    }

    @Test
    fun `an address is leased from the device`() {
        val device = device()
        val net = stack()
        assertEquals(device.leased, net.localIp)
        assertEquals(device.ip, net.deviceIp)
    }

    @Test
    fun `without dhcp the fallback address still reaches the device`() {
        val device = device(serveDhcp = false)
        device.greetings[52998] = "hello".toByteArray()
        val net = stack()

        assertNull(net.deviceIp)
        assertEquals(Packets.parseIpv4("169.254.1.20"), net.localIp)
        val connection = net.connect(device.ip, 52998).getOrThrow()
        assertEquals("hello", String(connection.readAll(5)))
    }

    @Test
    fun `the real report stream arrives intact through dropped segments`() {
        val stream = XrealOneCaptures.REPORTS
        val device = device(dropEvery = 7)
        device.greetings[52998] = stream
        val net = stack()

        val connection = net.connect(device.ip, 52998).getOrThrow()
        val received = connection.readAll(stream.size)

        assertArrayEquals(stream, received)
        assertEquals(600, XrealOneCaptures.messages(received).size)
        assertTrue("the drops never reached the host's loss handling", connection.outOfOrderDrops > 0)
    }

    @Test
    fun `a request gets a reply larger than any window`() {
        val config = ByteArray(220_000) { ('a' + it % 26).code.toByte() }
        val device = device(dropEvery = 11)
        device.handlers[52999] = { request -> if (request.contentEquals(byteArrayOf(1, 2, 3))) config else ByteArray(0) }
        val net = stack()

        val connection = net.connect(device.ip, 52999).getOrThrow()
        assertEquals(3, connection.write(byteArrayOf(1, 2, 3), 1_000))
        assertArrayEquals(config, connection.readAll(config.size))
    }

    @Test
    fun `the device closing is seen as end of stream after the data`() {
        val device = device()
        device.greetings[52999] = "bye".toByteArray()
        device.finishAfterGreeting += 52999
        val net = stack()

        val connection = net.connect(device.ip, 52999).getOrThrow()
        assertEquals("bye", String(connection.readAll(3)))
        val buffer = ByteArray(16)
        var n = 0
        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline) {
            n = connection.read(buffer, 100)
            if (n != 0) break
        }
        assertEquals(-1, n)
    }

    @Test
    fun `a refused port fails at once rather than timing out`() {
        val device = device()
        device.refused += 52990
        val net = stack()

        val started = System.currentTimeMillis()
        assertTrue(net.connect(device.ip, 52990, timeoutMs = 3_000).isFailure)
        assertTrue(System.currentTimeMillis() - started < 1_000)
    }

    @Test
    fun `nothing at the address fails cleanly`() {
        device()
        val net = stack()
        assertTrue(net.connect(Packets.parseIpv4("169.254.1.99"), 52998).isFailure)
    }

    @Test
    fun `frames are offered for capture in both directions`() {
        val device = device()
        val seen = mutableListOf<Boolean>()
        val net = NetStack(pipe, onFrame = { outbound, _, _ -> synchronized(seen) { seen += outbound } }).also {
            opened += it
            it.start()
            it.configure(Packets.parseIpv4("169.254.1.20"), 1_500)
        }
        device.greetings[52998] = "x".toByteArray()
        net.connect(device.ip, 52998).getOrThrow().readAll(1)
        synchronized(seen) { assertTrue(seen.contains(true) && seen.contains(false)) }
    }
}
