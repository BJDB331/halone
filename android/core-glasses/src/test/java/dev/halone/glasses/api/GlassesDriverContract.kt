package dev.halone.glasses.api

import dev.halone.glasses.transport.HidTransportProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contract every glasses driver must satisfy.
 *
 * A new driver subclasses this and supplies a known device identity plus a
 * transport provider — usually `ReplayHidTransport` over a packet capture, so
 * the suite runs on a laptop with no hardware attached. This exists so that
 * "does my driver behave like a driver?" has a mechanical answer, and so that
 * a contributor with hardware we do not own can still prove their work.
 *
 * See `docs/adding-a-device.md`.
 */
abstract class GlassesDriverContract {

    protected abstract fun driver(): GlassesDriver

    /** A USB identity this driver must recognise. */
    protected abstract fun knownIdentity(): UsbIdentity

    /** Transports satisfying whatever the probe asked for. */
    protected abstract fun transportsFor(match: DriverMatch): HidTransportProvider

    private fun requireMatch(): DriverMatch =
        driver().probe(knownIdentity()) ?: error("driver did not recognise its own known identity")

    @Test
    fun `driver id is a stable lowercase slug`() {
        val id = driver().id
        assertTrue("driver id must not be blank", id.isNotBlank())
        assertEquals("driver id should be lowercase", id.lowercase(), id)
        assertFalse("driver id should not contain spaces", id.contains(' '))
    }

    @Test
    fun `driver documents what it supports`() {
        assertTrue(driver().supportedDevices.isNotBlank())
    }

    @Test
    fun `probe ignores an unrelated device`() {
        assertNull(
            "driver claimed a device it should not recognise",
            driver().probe(UsbIdentity(vendorId = 0x0000, productId = 0x0000)),
        )
    }

    @Test
    fun `probe recognises its known identity`() {
        assertNotNull(driver().probe(knownIdentity()))
    }

    @Test
    fun `probe is pure and repeatable`() {
        val a = requireMatch()
        val b = requireMatch()
        assertEquals(a.info, b.info)
        assertEquals(a.optics, b.optics)
        assertEquals(a.transports, b.transports)
    }

    @Test
    fun `probe declares at least one transport`() {
        val transports = requireMatch().transports
        assertTrue(
            "a driver must say which interfaces or ports it needs",
            transports.interfaceIds.isNotEmpty() || !transports.network?.ports.isNullOrEmpty(),
        )
    }

    @Test
    fun `device info is populated`() {
        val info = requireMatch().info
        assertTrue(info.vendor.isNotBlank())
        assertTrue(info.model.isNotBlank())
        assertTrue(info.driverId.startsWith(driver().id))
    }

    /**
     * The vertical field of view must follow from the diagonal and the aspect.
     * A driver that reports a diagonal the projection cannot reproduce produces
     * a world-locked plane that appears to rotate off-axis — subtle enough to
     * ship, so it is worth asserting.
     */
    @Test
    fun `vertical field of view follows from the diagonal`() {
        val optics = requireMatch().optics
        val halfV = Math.toRadians(optics.verticalFovDegrees / 2.0)
        val halfH = kotlin.math.atan(kotlin.math.tan(halfV) * optics.eyeAspect)
        val diagonal = 2.0 * Math.toDegrees(
            kotlin.math.atan(kotlin.math.hypot(kotlin.math.tan(halfV), kotlin.math.tan(halfH)))
        )
        assertEquals(
            "verticalFovDegrees must reproduce the declared diagonal",
            optics.diagonalFovDegrees.toDouble(), diagonal, 0.1,
        )
        assertTrue(
            "vertical FoV should be the smallest component, was ${optics.verticalFovDegrees}",
            optics.verticalFovDegrees < optics.diagonalFovDegrees,
        )
    }

    @Test
    fun `optics are physically plausible`() {
        val optics = requireMatch().optics
        assertTrue("fov ${optics.diagonalFovDegrees}", optics.diagonalFovDegrees in 10f..180f)
        assertTrue("width ${optics.perEyeWidth}", optics.perEyeWidth > 0)
        assertTrue("height ${optics.perEyeHeight}", optics.perEyeHeight > 0)
        assertTrue("ipd ${optics.defaultIpdMm}", optics.defaultIpdMm in 40f..80f)
        assertTrue("imu rate ${optics.imuRateHz}", optics.imuRateHz > 0)
    }

    @Test
    fun `connect produces a usable device`() {
        val match = requireMatch()
        val device = driver().connect(match, transportsFor(match)).getOrThrow()
        device.use {
            assertEquals(match.info, it.info)
            assertNotNull(it.tracking)
            assertTrue(
                "displayNameHints are needed to pair the data link with an Android Display",
                it.displayNameHints.isNotEmpty(),
            )
        }
    }

    @Test
    fun `display modes are internally consistent`() {
        val match = requireMatch()
        driver().connect(match, transportsFor(match)).getOrThrow().use { device ->
            val display = device.display ?: return
            assertTrue("a display control with no modes is useless", display.supportedModes.isNotEmpty())

            val keys = display.supportedModes.map { it.key }
            assertEquals("mode keys must be unique", keys.distinct().size, keys.size)

            for (mode in display.supportedModes) {
                assertTrue("${mode.key} frame width", mode.frameWidth > 0)
                assertTrue("${mode.key} frame height", mode.frameHeight > 0)
                assertTrue("${mode.key} refresh", mode.refreshHz > 0f)
                when (mode.layout) {
                    StereoLayout.FULL_SBS ->
                        assertEquals("${mode.key} per-eye width", mode.frameWidth / 2, mode.perEyeWidth)
                    StereoLayout.OVER_UNDER ->
                        assertEquals("${mode.key} per-eye height", mode.frameHeight / 2, mode.perEyeHeight)
                    else -> Unit
                }
                assertEquals("${mode.key} isStereo", mode.layout != StereoLayout.MONO, mode.isStereo)
            }
        }
    }

    @Test
    fun `a stereo mode is reachable when the device claims stereo support`() {
        val match = requireMatch()
        driver().connect(match, transportsFor(match)).getOrThrow().use { device ->
            val display = device.display ?: return
            if (display.supportedModes.none { it.isStereo }) return
            assertNotNull("preferredStereoMode should find one", display.preferredStereoMode())
            assertNotNull("preferredMonoMode should find one", display.preferredMonoMode())
        }
    }

    @Test
    fun `closing twice is harmless`() {
        val match = requireMatch()
        val device = driver().connect(match, transportsFor(match)).getOrThrow()
        device.close()
        device.close()
    }
}
