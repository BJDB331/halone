package dev.halone.glasses.drivers.xrealone

import dev.halone.glasses.transport.ReplayHidTransport
import dev.halone.glasses.transport.ReplayHidTransport.Companion.toHexKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.CRC32

/** Layout per xr-tools' `proto/usb/endpoint.rs`; the CRC is standard CRC-32, as its `crc-adler` dependency implements. */
class XrealOneHidTest {

    @Test
    fun `the checksum is standard crc-32`() {
        val crc = CRC32().apply { update("123456789".toByteArray()) }.value
        assertEquals(0xCBF43926L, crc)
    }

    @Test
    fun `a request frame has the documented layout`() {
        val packet = XrealOneHid.frame(XrealOneHid.CMD_GET_USB_CONFIG, 1, ByteArray(0))

        assertEquals(1024, packet.size)
        assertEquals(0xFD.toByte(), packet[0])
        assertArrayEquals(byteArrayOf(0x11, 0x00), packet.copyOfRange(5, 7))
        assertArrayEquals(byteArrayOf(1, 0, 0, 0), packet.copyOfRange(7, 11))
        assertArrayEquals(byteArrayOf(0xD2.toByte(), 0x00), packet.copyOfRange(15, 17))
        assertTrue(packet.copyOfRange(17, 1024).all { it == 0.toByte() })

        val expected = CRC32().apply { update(packet, 5, 17) }.value
        val stored = (0 until 4).fold(0L) { v, i -> v or ((packet[1 + i].toLong() and 0xFF) shl (8 * i)) }
        assertEquals(expected, stored)
    }

    private fun reply(command: Int, requestId: Long, status: Int, payload: ByteArray): ByteArray {
        val frame = XrealOneHid.frame(command, requestId, byteArrayOf(status.toByte()) + payload)
        return frame
    }

    @Test
    fun `a reply round-trips through parse`() {
        val parsed = XrealOneHid.parse(reply(0x00D2, 7, 0, byteArrayOf(1, 2, 3, 4)), 1024)!!
        assertEquals(0x00D2, parsed.command)
        assertEquals(7L, parsed.requestId)
        assertEquals(0, parsed.status)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), parsed.payload)
    }

    @Test
    fun `a corrupted reply is ignored`() {
        val frame = reply(0x00D2, 7, 0, byteArrayOf(1, 2, 3, 4))
        frame[25] = 9
        assertEquals(null, XrealOneHid.parse(frame, 1024))
    }

    @Test
    fun `reading the usb functions decodes the network fields`() {
        val request = XrealOneHid.frame(XrealOneHid.CMD_GET_USB_CONFIG, 1, ByteArray(0))
        val transport = ReplayHidTransport(
            replies = mapOf(request.toHexKey() to reply(0x00D2, 1, 0, byteArrayOf(0x0A, 0x00, 0x00, 0x00))),
            repeatLast = false,
        )

        val functions = XrealOneHid(transport).usbFunctions().getOrThrow()

        assertEquals(2, functions.ncm)
        assertEquals(2, functions.ecm)
        assertEquals(false, functions.networkOn)
    }

    @Test
    fun `enabling the network writes only ncm, ecm and enable`() {
        val expectedPayload = byteArrayOf(0x05, 0x00, 0x01, 0x00)
        val request = XrealOneHid.frame(XrealOneHid.CMD_SET_USB_CONFIG, 1, expectedPayload)
        val transport = ReplayHidTransport(
            replies = mapOf(request.toHexKey() to reply(0x00D3, 1, 0, ByteArray(0))),
            repeatLast = false,
        )

        XrealOneHid(transport).enableNetwork().getOrThrow()

        assertArrayEquals(request, transport.written.single())
    }

    private fun setupTransport(functions: ByteArray): ReplayHidTransport {
        val read = XrealOneHid.frame(XrealOneHid.CMD_GET_USB_CONFIG, 1, ByteArray(0))
        val write = XrealOneHid.frame(XrealOneHid.CMD_SET_USB_CONFIG, 2, byteArrayOf(0x05, 0x00, 0x01, 0x00))
        return ReplayHidTransport(
            replies = mapOf(
                read.toHexKey() to reply(0x00D2, 1, 0, functions),
                write.toHexKey() to reply(0x00D3, 2, 0, ByteArray(0)),
            ),
            repeatLast = false,
        )
    }

    @Test
    fun `the setup turns the link on only when the glasses report it off`() {
        val off = setupTransport(byteArrayOf(0x0A, 0x00, 0x00, 0x00))
        assertTrue(XrealOneSetup.enableLink(off).isSuccess)
        assertEquals(2, off.written.size)

        val on = setupTransport(byteArrayOf(0x05, 0x00, 0x01, 0x00))
        assertTrue(XrealOneSetup.enableLink(on).isFailure)
        assertEquals("only the read was sent", 1, on.written.size)
    }

    @Test
    fun `the driver offers the setup with every match`() {
        val match = XrealOneDriver().probe(dev.halone.glasses.api.UsbIdentity(0x3318, 0x0436))!!
        assertTrue(match.setup === XrealOneSetup)
        assertTrue(match.setup!!.beforeStarting!!.contains("Stabilizer"))
    }

    @Test
    fun `nothing off the allowlist is ever written`() {
        val transport = ReplayHidTransport(repeatLast = false)
        val hid = XrealOneHid(transport)

        assertTrue(hid.request(0x00C1).isFailure)
        assertTrue(hid.request(XrealOneHid.CMD_SET_USB_CONFIG, byteArrayOf(0x00, 0x10, 0x00, 0x00)).isFailure)
        assertTrue(transport.written.isEmpty())
    }
}
