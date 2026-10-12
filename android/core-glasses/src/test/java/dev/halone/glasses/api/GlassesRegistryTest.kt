package dev.halone.glasses.api

import dev.halone.glasses.registry.GlassesRegistry
import dev.halone.glasses.transport.HidTransportProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassesRegistryTest {

    private class FakeDriver(
        override val id: String,
        private val vendorId: Int,
    ) : GlassesDriver {
        override val supportedDevices = "fake"
        var probeCount = 0

        override fun probe(identity: UsbIdentity): DriverMatch? {
            probeCount++
            if (identity.vendorId != vendorId) return null
            return DriverMatch(
                info = DeviceInfo("Fake", "Model", "$id:model"),
                optics = Optics(46f, 1920, 1080, imuRateHz = 100),
                transports = TransportRequest(listOf(0)),
            )
        }

        override fun connect(match: DriverMatch, transports: HidTransportProvider) =
            Result.failure<GlassesDevice>(UnsupportedOperationException("not needed"))
    }

    @Test
    fun `returns the first driver that recognises the device`() {
        val a = FakeDriver("a", 0x1111)
        val b = FakeDriver("b", 0x2222)
        val registry = GlassesRegistry(listOf(a, b))

        assertEquals("b", registry.probe(UsbIdentity(0x2222, 0x0001))!!.driver.id)
    }

    @Test
    fun `returns null when nothing recognises the device`() {
        val registry = GlassesRegistry(listOf(FakeDriver("a", 0x1111)))
        assertNull(registry.probe(UsbIdentity(0x9999, 0x0001)))
    }

    @Test
    fun `stops probing once a driver claims the device`() {
        val a = FakeDriver("a", 0x1111)
        val b = FakeDriver("b", 0x2222)
        GlassesRegistry(listOf(a, b)).probe(UsbIdentity(0x1111, 0x0001))

        assertEquals(1, a.probeCount)
        assertEquals("later drivers should not be consulted after a match", 0, b.probeCount)
    }

    @Test
    fun `probes many devices and keeps only the matches`() {
        val registry = GlassesRegistry(listOf(FakeDriver("a", 0x1111)))
        val results = registry.probeAll(
            listOf(
                UsbIdentity(0x9999, 1),
                UsbIdentity(0x1111, 2),
                UsbIdentity(0x1111, 3),
            )
        )
        assertEquals(2, results.size)
    }

    @Test
    fun `the default registry includes the xreal driver and finds real glasses`() {
        val registry = GlassesRegistry.default()

        assertTrue("xreal" in registry.driverIds())
        assertNotNull(
            "the default registry must recognise an Air 2 Pro",
            registry.probe(UsbIdentity(vendorId = 0x3318, productId = 0x0432)),
        )
        assertTrue(registry.describeSupported().isNotBlank())
    }

    @Test
    fun `driver ids are unique`() {
        val ids = GlassesRegistry.default().driverIds()
        assertEquals(ids.distinct().size, ids.size)
    }
}
