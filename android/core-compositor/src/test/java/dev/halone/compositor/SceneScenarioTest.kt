package dev.halone.compositor

import dev.halone.glasses.math.Vec3
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Plays the scenarios in `fixtures/scene` against the scene and checks the
 * trace against the golden one. The simulator's `scene.ts` is checked against
 * the same goldens, which is what keeps the two implementations the same.
 *
 * `./gradlew :core-compositor:testDebugUnitTest -PupdateSceneGoldens` rewrites
 * the goldens after a deliberate change.
 */
class SceneScenarioTest {

    private val fixtures = File(System.getProperty("sceneFixtures") ?: "../../fixtures/scene")
    private val update = System.getProperty("updateSceneGoldens") == "true"

    @Test
    fun scenariosMatchTheirGoldens() {
        val scenarios = JSONObject(File(fixtures, "scenarios.json").readText())
        val failures = mutableListOf<String>()
        for (name in scenarios.keys().asSequence().sorted()) {
            val trace = ScenarioRunner(scenarios.getJSONObject(name)).run()
            val golden = File(fixtures, "golden/$name.json")
            if (update) {
                golden.parentFile.mkdirs()
                golden.writeText(trace.toString(1) + "\n")
                continue
            }
            if (!golden.exists()) {
                failures += "$name: no golden; run with -PupdateSceneGoldens"
                continue
            }
            compare(name, JSONArray(golden.readText()), trace)?.let { failures += it }
        }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
    }

    private fun compare(name: String, expected: JSONArray, actual: JSONArray): String? {
        if (expected.length() != actual.length()) {
            return "$name: ${actual.length()} samples, golden has ${expected.length()}"
        }
        for (i in 0 until expected.length()) {
            val want = expected.getJSONObject(i)
            val got = actual.getJSONObject(i)
            val wantPanels = want.getJSONObject("p")
            val gotPanels = got.getJSONObject("p")
            if (wantPanels.keys().asSequence().toSet() != gotPanels.keys().asSequence().toSet()) return "$name frame ${want.getInt("f")}: panels differ"
            for (id in wantPanels.keys()) {
                val w = wantPanels.getJSONArray(id)
                val g = gotPanels.getJSONArray(id)
                for (k in 0 until w.length()) {
                    if (kotlin.math.abs(w.getDouble(k) - g.getDouble(k)) > TOLERANCE_DEGREES) {
                        return "$name frame ${want.getInt("f")} panel $id: expected $w, got $g"
                    }
                }
            }
        }
        return null
    }

    @Test
    fun panelSizesMatchTheSimulatorGeometry() {
        val runner = ScenarioRunner(JSONObject("""{"steps":[{"open":"a","heightPx":800}]}"""))
        runner.run()
        assertEquals(14.25, Math.toDegrees(2.0 * runner.scene.panels[0].angularHalfHeightRadians), 1e-3)
    }

    private class ScenarioRunner(private val scenario: JSONObject) {
        val scene = Scene()
        private var head = 0f
        private var frame = 0
        private val trace = JSONArray()

        fun run(): JSONArray {
            scenario.optJSONObject("settings")?.let(::apply)
            val steps = scenario.getJSONArray("steps")
            for (i in 0 until steps.length()) step(steps.getJSONObject(i))
            return trace
        }

        private fun step(step: JSONObject) {
            when {
                step.has("open") -> {
                    val width = step.optInt("widthPx", 1280)
                    val height = step.optInt("heightPx", 800)
                    scene.open(
                        Panel(
                            step.getString("open"),
                            FakePanelEngine(width, height),
                            PanelPose(
                                distanceMetres = DISTANCE,
                                widthMetres = metres(width),
                                heightMetres = metres(height),
                            ),
                        ),
                    )
                }
                step.has("close") -> {
                    scene.remove(panel(step.getString("close")))
                    scene.layOutCylindrical(immediate = false)
                }
                step.has("pin") -> panel(step.getString("pin")).let {
                    scene.setFollow(it, it.follow.copy(enabled = false))
                }
                step.has("unpin") -> panel(step.getString("unpin")).let {
                    scene.setFollow(it, it.follow.copy(enabled = true))
                }
                step.has("head") -> head = radians(step.getDouble("head"))
                step.has("run") -> frames(step.getDouble("run")) {}
                step.has("turn") -> {
                    val seconds = step.getDouble("seconds")
                    val perFrame = radians(step.getDouble("turn")) / (seconds * FPS).toInt()
                    frames(seconds) { head = Panel.wrapPi(head + perFrame) }
                }
                step.has("point") -> scene.hitTest(direction(panel(step.getString("point")).pose))
                step.has("grab") -> {
                    val pose = panel(step.getString("grab")).pose
                    aimYaw = pose.yawRadians
                    aimPitch = pose.pitchRadians
                    scene.beginGrab(panel(step.getString("grab")), direction(aimYaw, aimPitch))
                }
                step.has("drag") -> {
                    val delta = step.getJSONArray("drag")
                    aimYaw = Panel.wrapPi(aimYaw + radians(delta.getDouble(0)))
                    aimPitch += radians(delta.getDouble(1))
                    scene.updateGrab(direction(aimYaw, aimPitch))
                }
                step.has("drop") -> scene.endGrab(head)
                step.has("bringIntoView") -> scene.bringIntoView(panel(step.getString("bringIntoView")), head)
                step.has("set") -> apply(step.getJSONObject("set"))
                else -> error("unknown step $step")
            }
            sample()
        }

        private var aimYaw = 0f
        private var aimPitch = 0f

        private fun apply(settings: JSONObject) {
            if (settings.has("policy")) {
                scene.collisionPolicy = when (settings.getString("policy")) {
                    "overlap" -> CollisionPolicy.OVERLAP
                    "avoid-pinned" -> CollisionPolicy.AVOID_PINNED
                    else -> CollisionPolicy.AVOID_ALL
                }
            }
            if (settings.has("margin")) scene.followMarginDegrees = settings.getDouble("margin").toFloat()
            if (settings.has("catchUp")) scene.catchUpSeconds = settings.getDouble("catchUp").toFloat()
            if (settings.has("follow")) scene.followsHead = settings.getBoolean("follow")
            if (settings.has("spacing")) {
                scene.layOutCylindrical(settings.getDouble("spacing").toFloat(), immediate = false)
            }
        }

        private inline fun frames(seconds: Double, beforeEach: () -> Unit) {
            repeat((seconds * FPS).toInt()) {
                beforeEach()
                scene.advanceFollow(head, DT)
                frame++
                if (frame % SAMPLE_EVERY == 0) sample()
            }
        }

        private fun sample() {
            val panels = JSONObject()
            for (panel in scene.panels.sortedBy { it.id }) {
                panels.put(
                    panel.id,
                    JSONArray()
                        .put(round(panel.pose.yawRadians))
                        .put(round(panel.pose.pitchRadians))
                        .put(round(panel.slotYawRadians))
                        .put(if (panel.slotPlacedByHand) 1 else 0)
                        .put(if (panel.follow.enabled) 0 else 1),
                )
            }
            trace.put(JSONObject().put("f", frame).put("p", panels))
        }

        private fun panel(id: String) = scene.find(id) ?: error("no panel $id")
    }

    companion object {
        private const val FPS = 60
        private const val DT = 1f / FPS
        private const val SAMPLE_EVERY = 3
        private const val DISTANCE = 4f
        private const val TOLERANCE_DEGREES = 1e-3

        /** The simulator's geometry: an 800px panel subtends 75% of 19°. */
        private val PANEL_DISTANCE_PX = 400.0 / kotlin.math.tan(Math.toRadians(19.0 * 0.75 / 2))

        private fun metres(px: Int) = (DISTANCE * px / PANEL_DISTANCE_PX).toFloat()

        private fun radians(degrees: Double) = Math.toRadians(degrees).toFloat()

        private fun round(radians: Float) = Math.round(Math.toDegrees(radians.toDouble()) * 1e4) / 1e4

        private fun direction(pose: PanelPose) = direction(pose.yawRadians, pose.pitchRadians)

        private fun direction(yaw: Float, pitch: Float) = Vec3(
            -kotlin.math.cos(pitch) * kotlin.math.sin(yaw),
            kotlin.math.sin(pitch),
            -kotlin.math.cos(pitch) * kotlin.math.cos(yaw),
        )
    }
}
