package dev.halone.glasses.drivers.xreal

import dev.halone.glasses.transport.ReplayHidTransport.Companion.parseHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The timing beacon, against bytes captured from an Air 2 Pro.
 *
 * The vsync interface does not use the MCU's framing and matches neither
 * message table in the vendor's libraries, so the layout was read off the wire
 * — which makes a captured packet the only thing that can hold it in place.
 */
class XrealScanoutTest {

    /** Consecutive beacons, 20:09 on the day the interface was identified. */
    private val first =
        "fd 14 00 00 00 00 00 00 70 ad 1f 43 16 00 00 00 90 4d fe 00 00 00 00 00".parseHex()
    private val second =
        "fd 15 00 00 00 00 00 00 50 dd 6f 41 17 00 00 00 78 51 fe 00 00 00 00 00".parseHex()

    @Test
    fun `a captured beacon reads as an uptime and a refresh period`() {
        val beacon = XrealProtocol.vsyncBeacon(first)
        assertNotNull(beacon)
        assertEquals(0x14, beacon!!.counter)
        assertEquals(95_615_430_000L, beacon.deviceTimeNs)
        assertEquals(16_666_000L, beacon.periodNs)
    }

    @Test
    fun `the counter steps by one per beacon`() {
        assertEquals(
            1,
            XrealProtocol.vsyncBeacon(second)!!.counter - XrealProtocol.vsyncBeacon(first)!!.counter,
        )
    }

    /**
     * The reading that validates itself: consecutive beacons are a whole number
     * of refreshes apart. 256 of them, which is also why the period comes out
     * more precisely from the gap than from the field that states it.
     */
    @Test
    fun `consecutive beacons are a whole number of refreshes apart`() {
        val a = XrealProtocol.vsyncBeacon(first)!!
        val b = XrealProtocol.vsyncBeacon(second)!!
        val refreshes = (b.deviceTimeNs - a.deviceTimeNs).toDouble() / a.periodNs
        assertEquals(256.0, refreshes, 0.02)
    }

    @Test
    fun `a short packet is not a beacon`() {
        assertNull(XrealProtocol.vsyncBeacon(first, size = 16))
    }

    @Test
    fun `a packet with the wrong head is not a beacon`() {
        val wrong = first.copyOf().also { it[0] = 0xAA.toByte() }
        assertNull(XrealProtocol.vsyncBeacon(wrong))
    }

    /** A zeroed frame is what a quiet endpoint returns, not a refresh at time zero. */
    @Test
    fun `an empty frame is not a beacon`() {
        assertNull(XrealProtocol.vsyncBeacon(ByteArray(24).also { it[0] = 0xFD.toByte() }))
    }

    @Test
    fun `a heartbeat carries the host timestamp where the device expects it`() {
        val packet = XrealProtocol.heartbeat(123_456_789_000L)
        assertEquals(
            XrealProtocol.MCU_MSG_W_HEARTBEAT.toLong(),
            XrealProtocol.mcuMsgId(packet).toLong(),
        )
        assertEquals(
            123_456_789_000L,
            XrealProtocol.u64At(packet, XrealProtocol.MCU_DATA_OFFSET),
        )
    }
}
