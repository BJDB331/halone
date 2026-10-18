package dev.halone.testkit

import dev.halone.testkit.analysis.AxisSolver
import dev.halone.testkit.analysis.D
import dev.halone.testkit.engine.GenericProfile
import dev.halone.testkit.engine.ReportBundle
import dev.halone.testkit.engine.Runner
import dev.halone.testkit.profiles.XrealOneProfile
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class XrealOneProfileTest {

    private fun TestScope.kit() = FakeKit { testScheduler.currentTime * 1_000_000 }

    private val profiles = listOf(XrealOneProfile, GenericProfile)

    /**
     * Answers each motion with a physically consistent movement of glasses using
     * [FAKE_AXES], made after the beep. While the instruction plays it adds what
     * real testers do then: the tail of the previous movement, coming back.
     * [weak] barely moves for that many attempts; [backwards] moves the wrong
     * way for that many.
     */
    private fun FakeKit.performMotions(weak: Map<Int, Int> = emptyMap(), backwards: Map<Int, Int> = emptyMap()) {
        val attempts = mutableMapOf<Int, Int>()
        var clock = 1_000_000_000L
        var pending: XrealOneProfile.Motion? = null
        val gravity = toHardware(FAKE_AXES, D(0.0, 9.8, 0.0))
        onSay = { text ->
            val motion = XrealOneProfile.MOTIONS.firstOrNull { said(it.instruction) == text }
            val reports = taps.entries.firstOrNull { it.key.startsWith("reports") }?.value
            if (motion != null && reports != null) {
                pending = motion
                val leftover = AxisSolver.EXPECTED[motion.label]
                if (leftover != null) {
                    reports.push(turning(clock, 0.8, toHardware(FAKE_AXES, leftover * -0.7), gravity))
                    clock += 1_000_000_000L
                }
            }
        }
        onCue = {
            val motion = pending
            val reports = taps.entries.firstOrNull { it.key.startsWith("reports") }?.value
            if (motion != null && reports != null) {
                val attempt = (attempts[motion.instruction] ?: 0) + 1
                attempts[motion.instruction] = attempt
                val rate = when {
                    attempt <= (weak[motion.instruction] ?: 0) -> 0.05
                    motion.check is XrealOneProfile.Check.Still -> 0.0
                    attempt <= (backwards[motion.instruction] ?: 0) -> -0.6
                    else -> 0.6
                }
                val axis = AxisSolver.EXPECTED[motion.label] ?: D(0.0, 1.0, 0.0)
                reports.push(turning(clock, 2.0, toHardware(FAKE_AXES, axis * rate), gravity))
                clock += 3_000_000_000L
            }
        }
        tcp = { name, _, listener ->
            val tap = FakeTap(listener)
            taps[name] = tap
            if (name.startsWith("reports")) tap.push(imuReport(1, 0f))
            Result.success(tap)
        }
    }

    @Test
    fun `a full run records every motion and the display tour`() = runTest {
        val kit = kit().apply { performMotions() }

        assertTrue(Runner(kit, profiles).run())

        val outcomes = kit.session.outcomes
        assertEquals("xreal-one", kit.session.profileId)
        assertEquals("streaming", outcomes["sensors"])
        XrealOneProfile.MOTIONS.forEach { assertEquals("ok", outcomes["motion_${it.label}"]) }
        assertTrue(kit.session.markers.any { it.label == "motion:turn-left:1:start" })
        assertTrue(kit.session.notes.contains("tester: felt fine"))
        assertEquals("confident", outcomes["axes"])
        assertEquals("x=X_POS y=Y_NEG z=Z_NEG", outcomes["axes_convention"])
        assertTrue(kit.session.files.containsKey("analysis/axes.json"))
        assertFalse(kit.session.markers.any { it.label.contains("redo") })
    }

    @Test
    fun `contradictory tilts are repeated once, and only they are`() = runTest {
        val kit = kit().apply { performMotions(backwards = mapOf(R.string.motion_tilt_left to 1)) }

        Runner(kit, profiles).run()

        assertTrue(kit.spoken.contains(said(R.string.axes_repeat)))
        val redone = kit.session.markers.filter { it.label.endsWith(":start") && it.label.contains(":redo") }
            .map { it.label.split(":")[1] }
        assertEquals(listOf("tilt-left", "tilt-right"), redone)
        assertEquals("confident", kit.session.outcomes["axes"])
    }

    @Test
    fun `a motion that barely moved is asked for again`() = runTest {
        val kit = kit().apply { performMotions(weak = mapOf(R.string.motion_turn_left to 1)) }

        Runner(kit, profiles).run()

        assertTrue(kit.spoken.contains(said(R.string.motion_retry_turn)))
        assertTrue(kit.session.markers.any { it.label == "motion:turn-left:2:start" })
        assertEquals("ok", kit.session.outcomes["motion_turn-left"])
    }

    @Test
    fun `a motion that never works out is recorded as unclear and the run carries on`() = runTest {
        val kit = kit().apply { performMotions(weak = mapOf(R.string.motion_look_up to 99)) }

        Runner(kit, profiles).run()

        assertEquals("unclear", kit.session.outcomes["motion_look-up"])
        assertEquals("ok", kit.session.outcomes["motion_free"])
    }

    @Test
    fun `unreachable sensors ask about stabilizer, then skip the motions`() = runTest {
        val kit = kit().apply {
            tcp = { _, _, _ -> Result.failure(java.io.IOException("no route")) }
        }

        assertTrue(Runner(kit, profiles).run())

        assertEquals("unreachable", kit.session.outcomes["sensors"])
        assertTrue(kit.session.answers.any { it.id == "stabilizer_off" && it.attempt == 3 })
        assertFalse(kit.session.markers.any { it.label.startsWith("motion:") })
        assertTrue("sensors-unreachable" in kit.snapshots)
        assertEquals("skipped", kit.session.outcomes["display_mode_3d"])
    }

    @Test
    fun `the config reply's serial is redacted from the bundle`() = runTest {
        val kit = kit()
        kit.tcp = { name, _, listener ->
            val tap = FakeTap(listener)
            kit.taps[name] = tap
            if (name == "control") {
                tap.onSend = { request ->
                    if (request[0] == 0x27.toByte() && request[1] == 0x1F.toByte()) {
                        tap.push("{\"FSN\": \"K531X99999\", \"display\": {}}".toByteArray())
                    }
                }
            }
            Result.success(tap)
        }

        Runner(kit, profiles).run()
        kit.session.attach("captures/control.cap", "...\"FSN\": \"K531X99999\"...")

        val zip = ByteArrayOutputStream().also { ReportBundle.write(kit.session, "test", it) }.toByteArray()
        val contents = unzip(zip)
        assertTrue(contents.values.none { String(it).contains("K531X99999") })
        assertTrue(String(contents.getValue("captures/control.cap")).contains("XXXXXXXXXX"))
        assertEquals("config-read", kit.session.outcomes["control"])
    }

    @Test
    fun `the display tour records every state and waits for a real return to 2d`() = runTest {
        val twoD = "1920x1080@120,1920x1080@60,1920x1080@90"
        val half = "1920x1080@60"
        val full = "3840x1080@60"
        val kit = kit()
        var current = twoD
        var polls = 0
        kit.onSay = { if (it == said(R.string.modes_intro)) polls = 0 }
        kit.displays = {
            polls++
            current = when {
                current == twoD && polls in 5..30 -> half
                current == half && polls > 30 -> full
                current == full && polls > 60 -> twoD
                else -> current
            }
            dev.halone.testkit.engine.DisplayState("XREAL One Pro", current)
        }

        Runner(kit, profiles).run()

        val states = kit.session.outcomes.filterKeys { it.startsWith("display_state_") }.values.toList()
        assertEquals(listOf(twoD, half, full), states)
        assertEquals("reached", kit.session.outcomes["display_mode_3d"])
        assertEquals("reached", kit.session.outcomes["display_mode_2d-again"])
    }

    @Test
    fun `without a phone network it asks for usb access once and carries on`() = runTest {
        val kit = kit().apply {
            performMotions()
            systemLink = false
        }

        Runner(kit, profiles).run()

        assertEquals(1, kit.usbRequests)
        assertEquals("granted", kit.session.outcomes["usb_access"])
        assertEquals("streaming", kit.session.outcomes["sensors"])
    }

    @Test
    fun `refused usb access skips the sensors but not the rest`() = runTest {
        val kit = kit().apply {
            systemLink = false
            usbGranted = false
        }

        assertTrue(Runner(kit, profiles).run())

        assertEquals("no-usb-access", kit.session.outcomes["sensors"])
        assertTrue(kit.taps.keys.none { it.startsWith("reports") })
        assertTrue(kit.spoken.contains(said(R.string.usb_access_denied)))
    }

    @Test
    fun `with a phone network no usb access is asked for`() = runTest {
        val kit = kit().apply { performMotions() }

        Runner(kit, profiles).run()

        assertEquals(0, kit.usbRequests)
    }

    @Test
    fun `unknown glasses get the generic run`() = runTest {
        val kit = kit().apply { devices = listOf(dev.halone.glasses.api.UsbIdentity(vendorId = 0x1234, productId = 0x0001)) }

        Runner(kit, profiles).run()

        assertEquals("generic", kit.session.profileId)
        assertTrue(kit.spoken.contains(said(R.string.found_unknown)))
    }

    @Test
    fun `declining consent gathers nothing`() = runTest {
        val kit = kit().apply { answer = { _, choices -> choices.first { it.id == "stop" } } }

        assertFalse(Runner(kit, profiles).run())
        assertTrue(kit.session.files.isEmpty())
    }

    @Test
    fun `glasses plugged in late are found after a nudge`() = runTest {
        val kit = kit().apply {
            devices = emptyList()
            answer = { question, choices ->
                if (question == said(R.string.plug_in_question)) {
                    devices = listOf(dev.halone.glasses.api.UsbIdentity(vendorId = 0x3318, productId = 0x0436))
                }
                choices.first()
            }
        }

        assertTrue(Runner(kit, profiles).run())
        assertEquals("xreal-one", kit.session.profileId)
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                out[entry.name] = zip.readBytes()
            }
        }
        return out
    }
}
