package dev.halone.testkit

import dev.halone.glasses.math.Quaternion
import dev.halone.glasses.math.Vec3
import dev.halone.testkit.ab.HeadView
import dev.halone.testkit.ab.SceneParams
import dev.halone.testkit.ab.Staircase
import dev.halone.testkit.analysis.AxisSolver
import dev.halone.testkit.analysis.D
import dev.halone.testkit.engine.GenericProfile
import dev.halone.testkit.engine.Runner
import dev.halone.testkit.profiles.XrealOneProfile
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.acos

class AbTest {

    @Test
    fun `the staircase closes in on what an honest observer prefers`() {
        for (truth in listOf(-9.0, -3.3, 0.0, 4.0, 8.7)) {
            val stairs = Staircase("x", -10.0, 10.0, rounds = 3)
            while (!stairs.done) {
                val (a, b) = stairs.candidates()
                stairs.answer(if (abs(a - truth) <= abs(b - truth)) a else b)
            }
            assertTrue("truth $truth outside ${stairs.low}..${stairs.high}", truth in stairs.low..stairs.high)
            assertEquals(2.5, stairs.high - stairs.low, 1e-9)
        }
    }

    @Test
    fun `no difference settles between the two candidates`() {
        val stairs = Staircase("x", 0.0, 100.0, rounds = 3)
        stairs.answer(null)
        assertTrue(stairs.done)
        assertEquals(50.0, stairs.estimate, 1e-9)
        assertEquals(25.0, stairs.low, 1e-9)
    }

    private val mount = Quaternion.fromPitchDegrees(-35f)

    /** Glasses using [FAKE_AXES], IMU pitched by [mount]; the head starts level then turns left. */
    private fun HeadView.feed(seconds: Double, headRate: Vec3, fromNs: Long): Long {
        val gravity = mount.rotate(Vec3(0f, 9.81f, 0f))
        val gyro = mount.rotate(headRate)
        val rawGyro = toHardware(FAKE_AXES, D(gyro.x.toDouble(), gyro.y.toDouble(), gyro.z.toDouble()))
        val rawAccel = toHardware(FAKE_AXES, D(gravity.x.toDouble(), gravity.y.toDouble(), gravity.z.toDouble()))
        var t = fromNs
        repeat((seconds * 1000).toInt()) {
            onImu(t, rawGyro.x.toFloat(), rawGyro.y.toFloat(), rawGyro.z.toFloat(), rawAccel.x.toFloat(), rawAccel.y.toFloat(), rawAccel.z.toFloat())
            t += 1_000_000L
        }
        return t
    }

    private fun degreesBetween(a: Vec3, b: Vec3) =
        Math.toDegrees(acos(((a.x * b.x + a.y * b.y + a.z * b.z) / (a.length * b.length)).coerceIn(-1f, 1f).toDouble()))

    @Test
    fun `the view is level at rest and stays level through a turn with the right mount`() {
        val view = HeadView(FAKE_AXES, mount)
        val params = SceneParams(30.0, 0.0, 0.0)
        var t = view.feed(2.0, Vec3.ZERO, 1_000_000L)
        view.recenter(params)
        assertTrue(degreesBetween(view.head(params).rotate(Vec3(0f, 1f, 0f)), Vec3(0f, 1f, 0f)) < 2.0)

        t = view.feed(1.0, Vec3(0f, (Math.PI / 2).toFloat(), 0f), t)
        view.feed(0.2, Vec3.ZERO, t)
        val head = view.head(params)
        assertTrue("tilted after a turn", degreesBetween(head.rotate(Vec3(0f, 1f, 0f)), Vec3(0f, 1f, 0f)) < 3.0)
        assertTrue("turn not followed", degreesBetween(head.rotate(Vec3(0f, 0f, -1f)), Vec3(-1f, 0f, 0f)) < 5.0)
    }

    @Test
    fun `a mount offset shows up as pitch, and prediction leads a turn`() {
        val view = HeadView(FAKE_AXES, mount)
        var t = view.feed(2.0, Vec3.ZERO, 1_000_000L)
        val level = SceneParams(30.0, 0.0, 0.0)
        view.recenter(level)
        val tipped = view.head(level.copy(mountOffsetDegrees = 6.0)).rotate(Vec3(0f, 0f, -1f))
        assertEquals(6.0, degreesBetween(tipped, Vec3(0f, 0f, -1f)), 0.5)

        t = view.feed(0.5, Vec3(0f, 1f, 0f), t)
        val now = view.head(level).rotate(Vec3(0f, 0f, -1f))
        val ahead = view.head(level.copy(predictionMs = 100.0)).rotate(Vec3(0f, 0f, -1f))
        assertEquals(Math.toDegrees(0.1), degreesBetween(now, ahead), 1.0)
    }

    private fun TestScope.kit() = FakeKit { testScheduler.currentTime * 1_000_000 }

    private val config = """{"FSN": "K531X00001", "display": {"resolution": [1920, 1080],
        "k_left_display": [2200, 0, 960, 0, 2200, 540, 0, 0, 1],
        "target_q_left_display": [-0.300706, 0, 0, 0.953717]}}"""

    /** Physically consistent motions after each beep, the config on request, and a wearer who prefers [truth]. */
    private fun FakeKit.setUp(truth: Map<String, Double>) {
        var clock = 1_000_000_000L
        var pending: XrealOneProfile.Motion? = null
        val gravity = toHardware(FAKE_AXES, D(0.0, 9.8, 0.0))
        onSay = { text -> pending = XrealOneProfile.MOTIONS.firstOrNull { said(it.instruction) == text } ?: pending }
        onCue = {
            val reports = taps.entries.firstOrNull { it.key.startsWith("reports") }?.value
            val motion = pending
            if (reports != null) {
                val axis = motion?.let { AxisSolver.EXPECTED[it.label] }
                val rate = if (motion == null || motion.check is XrealOneProfile.Check.Still) 0.0 else 0.6
                reports.push(turning(clock, 2.0, toHardware(FAKE_AXES, (axis ?: D(0.0, 1.0, 0.0)) * rate), gravity))
                clock += 3_000_000_000L
                pending = null
            }
        }
        tcp = { name, _, listener ->
            val tap = FakeTap(listener)
            taps[name] = tap
            if (name.startsWith("reports")) tap.push(imuReport(1, 0f))
            if (name == "control") tap.onSend = { if (it[1] == 0x1F.toByte()) tap.push(config.toByteArray()) }
            Result.success(tap)
        }
        val questions = mapOf(
            said(R.string.ab_mount_question) to "mount_offset_deg",
            said(R.string.ab_fov_question) to "fov_scale",
            said(R.string.ab_prediction_question) to "prediction_ms",
        )
        answer = { question, choices ->
            val parameter = questions[question]
            if (parameter == null) {
                choices.first()
            } else {
                val (first, second) = scenes.filterNotNull().takeLast(2)
                fun value(p: SceneParams) = when (parameter) {
                    "mount_offset_deg" -> p.mountOffsetDegrees
                    "fov_scale" -> p.verticalFovDegrees / Math.toDegrees(2 * kotlin.math.atan(540.0 / 2200.0))
                    else -> p.predictionMs
                }
                val target = truth.getValue(parameter)
                choices.first { it.id == if (abs(value(first) - target) <= abs(value(second) - target)) "first" else "second" }
            }
        }
    }

    @Test
    fun `a session runs the rounds blind and lands near the wearer's preferences`() = runTest {
        val truth = mapOf("mount_offset_deg" to 4.0, "fov_scale" to 0.86, "prediction_ms" to 90.0)
        val kit = kit().apply { setUp(truth) }

        Runner(kit, listOf(XrealOneProfile, GenericProfile)).run()

        assertEquals("done", kit.session.outcomes["ab"])
        val report = JSONObject(String(kit.session.files.getValue("analysis/ab.json") as ByteArray))
        assertEquals(4.0, report.getJSONObject("mount_offset_deg").getDouble("estimate"), 1.25)
        assertEquals(0.86, report.getJSONObject("fov_scale").getDouble("estimate"), 0.07)
        assertEquals(90.0, report.getJSONObject("prediction_ms").getDouble("estimate"), 9.0)
        assertTrue("every round shows two candidates", kit.scenes.filterNotNull().size == 18)
        assertTrue("the scene is taken down at the end", kit.scenes.last() == null)
        assertFalse("the letters are never explained aloud", kit.spoken.any { it.contains("higher") || it.contains("lower") })
    }

    @Test
    fun `rounds are skipped when the axes could not be trusted`() = runTest {
        val kit = kit().apply {
            setUp(emptyMap())
            onCue = {}
        }

        Runner(kit, listOf(XrealOneProfile, GenericProfile)).run()

        assertTrue(kit.session.outcomes["ab"]?.startsWith("skipped") == true)
        assertTrue(kit.scenes.isEmpty())
    }
}
