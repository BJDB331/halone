package dev.halone.glasses.drivers.rayneo

import dev.halone.glasses.api.DeviceEvent
import dev.halone.glasses.api.GlassesDevice
import dev.halone.glasses.api.TrackingSample
import dev.halone.glasses.api.UsbIdentity
import dev.halone.glasses.registry.GlassesRegistry
import dev.halone.glasses.transport.HidTransport
import dev.halone.glasses.transport.HidTransportProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.PI

class RayneoSessionTest {

    private val identity = UsbIdentity(vendorId = 0x3941, productId = 0xAF50)
    private val driver = RayneoDriver()

    private fun connect(
        vararg byInterface: Pair<Int, FakeRayneoGlasses>,
        hidInterfaces: List<Int> = emptyList(),
    ): Result<GlassesDevice> {
        val glasses = byInterface.toMap()
        val provider = object : HidTransportProvider {
            override fun open(interfaceId: Int): Result<HidTransport> =
                glasses[interfaceId]?.let { Result.success(it) }
                    ?: Result.failure(IllegalArgumentException("no interface with id $interfaceId"))

            override fun hidInterfaceIds() = hidInterfaces
        }
        return driver.connect(driver.probe(identity)!!, provider)
    }

    @Test
    fun `the registry routes the GT series to this driver, and not its DFU mode`() {
        val registry = GlassesRegistry.default()
        assertEquals("rayneo", registry.probe(identity)?.driver?.id)
        assertNull(registry.probe(identity.copy(productId = RayneoProtocol.PRODUCT_ID_DFU)))
    }

    @Test
    fun `connect asks who the glasses are and quiets a leftover stream`() {
        val glasses = FakeRayneoGlasses()
        connect(0 to glasses).getOrThrow().use { device ->
            val session = device as RayneoSession
            assertEquals(RayneoProtocol.BOARD_GT_MAX, session.board)
            assertEquals("Sep 22 2026", session.firmwareBuild)
            assertEquals(RayneoDriver.GT_MAX_OPTICS, device.optics)
            assertEquals(
                listOf(RayneoProtocol.CMD_STREAM_OFF, RayneoProtocol.CMD_DEVICE_INFO),
                glasses.commands().map { it.first }.take(2),
            )
        }
    }

    @Test
    fun `a plain GT gets the GT's optics`() {
        connect(0 to FakeRayneoGlasses(board = RayneoProtocol.BOARD_GT)).getOrThrow().use { device ->
            assertEquals(RayneoDriver.GT_OPTICS, device.optics)
        }
    }

    @Test
    fun `an interface that never answers is passed over for the next`() {
        val silent = FakeRayneoGlasses(answers = false)
        val real = FakeRayneoGlasses()
        connect(0 to silent, 5 to real).getOrThrow().use {
            assertTrue("the silent interface should be released", silent.closed)
            assertFalse(real.closed)
        }
    }

    @Test
    fun `any other HID interface is tried when the known ones are absent`() {
        val real = FakeRayneoGlasses()
        connect(3 to real, hidInterfaces = listOf(3)).getOrThrow().use {
            assertFalse(real.closed)
            assertEquals(RayneoProtocol.CMD_DEVICE_INFO, real.commands()[1].first)
        }
    }

    @Test
    fun `firmware that wants a report id is answered in its own framing`() {
        val glasses = FakeRayneoGlasses(wantsReportId = true)
        connect(0 to glasses).getOrThrow().use { device ->
            device.brightness!!.set(2).getOrThrow()
            val last = glasses.written.last()
            assertEquals(65, last.size)
            assertEquals(0, last[0].toInt())
        }
    }

    @Test
    fun `connect fails clearly when nothing answers`() {
        val result = connect(0 to FakeRayneoGlasses(answers = false))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("interface 5"))
    }

    @Test
    fun `tracking switches the glasses to follow, streams canonical samples, and hands back pinned`() {
        val glasses = FakeRayneoGlasses(
            imuFrames = listOf(
                RayneoCaptures.imu(tick = 0xFFFF_FFE0L, gyroDeg = floatArrayOf(0f, 0f, 90f)),
                RayneoCaptures.imu(tick = 0xFFFF_FFF6L, gyroDeg = floatArrayOf(0f, 0f, 90f)),
                RayneoCaptures.imu(tick = 0x0000_000CL, gyroDeg = floatArrayOf(0f, 0f, 90f)),
            ),
        )
        val device = connect(0 to glasses).getOrThrow()
        val samples = CopyOnWriteArrayList<TrackingSample.Inertial>()
        val enough = CountDownLatch(3)

        device.use {
            device.tracking.start { sample ->
                samples += sample as TrackingSample.Inertial
                enough.countDown()
            }
            assertTrue("no samples arrived", enough.await(2, TimeUnit.SECONDS))
            device.tracking.stop()
        }

        val sent = glasses.commands()
        val follow = sent.indexOf(RayneoProtocol.CMD_ANCHOR_MODE to RayneoProtocol.AnchorMode.FOLLOW.value)
        val streamOn = sent.indexOf(RayneoProtocol.CMD_STREAM_ON to 0)
        assertTrue("follow must be set before streaming: $sent", follow in 0 until streamOn)
        assertEquals(
            "the stop should end the stream and return the glasses to pinned",
            listOf(
                RayneoProtocol.CMD_STREAM_OFF to 0,
                RayneoProtocol.CMD_ANCHOR_MODE to RayneoProtocol.AnchorMode.PINNED.value,
            ),
            sent.takeLast(2),
        )

        // A yaw about sensor +Z is a yaw about canonical +Y, in rad/s.
        val first = samples.first()
        assertEquals(0f, first.gyro.x, 1e-5f)
        assertEquals((PI / 2).toFloat(), first.gyro.y, 1e-4f)
        assertEquals(0f, first.gyro.z, 1e-5f)
        assertEquals(9.81f, first.accel.y, 1e-4f)
        assertNull("µT must not be fed to the counts-tuned heading correction", first.magnetRaw)

        // 22 ticks of 100 µs, straight across the 32-bit wrap.
        val steps = samples.take(3).zipWithNext { a, b -> b.timestampNs - a.timestampNs }
        assertEquals(listOf(2_200_000L, 2_200_000L), steps)
    }

    @Test
    fun `the mode button is reported, and follow is reasserted while tracking`() {
        val glasses = FakeRayneoGlasses()
        val device = connect(0 to glasses).getOrThrow()
        val pressed = CountDownLatch(1)
        device.addEventListener { if (it is DeviceEvent.ButtonPressed) pressed.countDown() }

        device.use {
            device.tracking.start { }
            val before = glasses.commands().size
            glasses.pressModeButton()
            assertTrue(pressed.await(2, TimeUnit.SECONDS))
            Thread.sleep(50)
            assertEquals(
                RayneoProtocol.CMD_ANCHOR_MODE to RayneoProtocol.AnchorMode.FOLLOW.value,
                glasses.commands().drop(before).first(),
            )
        }
    }

    @Test
    fun `onboard pinning hands world-locking to the glasses and survives the mode button`() {
        val glasses = FakeRayneoGlasses()
        val device = connect(0 to glasses).getOrThrow()
        val pressed = CountDownLatch(1)
        device.addEventListener { if (it is DeviceEvent.ButtonPressed) pressed.countDown() }

        device.use {
            device.tracking.start { }
            val pinning = device.onboardPinning!!
            assertFalse(pinning.enabled)

            pinning.setEnabled(true).getOrThrow()
            assertTrue(pinning.enabled)
            val pinned = RayneoProtocol.CMD_ANCHOR_MODE to RayneoProtocol.AnchorMode.PINNED.value
            assertEquals(pinned, glasses.commands().last())

            // The button must now restore pinned, not follow.
            val before = glasses.commands().size
            glasses.pressModeButton()
            assertTrue(pressed.await(2, TimeUnit.SECONDS))
            Thread.sleep(200)
            // Once: the ack to our own reassertion is not another press.
            assertEquals(listOf(pinned), glasses.commands().drop(before))

            // Recentring leaves pinned and comes back.
            val mark = glasses.commands().size
            pinning.recentre().getOrThrow()
            assertEquals(
                listOf(RayneoProtocol.CMD_ANCHOR_MODE to RayneoProtocol.AnchorMode.FOLLOW.value, pinned),
                glasses.commands().drop(mark),
            )

            pinning.setEnabled(false).getOrThrow()
            assertFalse(pinning.enabled)
        }
    }

    @Test
    fun `brightness reads from device info and refuses levels outside its range`() {
        val glasses = FakeRayneoGlasses()
        connect(0 to glasses).getOrThrow().use { device ->
            val brightness = device.brightness!!
            assertEquals(8, brightness.current().getOrThrow())
            assertTrue(brightness.set(3).isSuccess)
            assertEquals(RayneoProtocol.CMD_BRIGHTNESS to 3, glasses.commands().last())
            assertTrue(brightness.set(brightness.range.last + 1).isFailure)
            assertTrue(brightness.set(-1).isFailure)
        }
    }

    @Test
    fun `there is no display control, so the wearer chooses 3D`() {
        connect(0 to FakeRayneoGlasses()).getOrThrow().use { device ->
            assertNull(device.display)
            assertTrue(device.displayNameHints.isNotEmpty())
        }
    }

    @Test
    fun `closing releases the endpoint`() {
        val glasses = FakeRayneoGlasses()
        val device = connect(0 to glasses).getOrThrow()
        device.tracking.start { }
        device.close()
        assertTrue(glasses.closed)
        assertFalse(glasses.streaming)
    }
}
