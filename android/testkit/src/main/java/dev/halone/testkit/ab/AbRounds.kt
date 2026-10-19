package dev.halone.testkit.ab

import dev.halone.testkit.R
import dev.halone.testkit.engine.Kit
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * Blind pairwise rounds over the parameters only a wearer can judge. Each round
 * shows two candidates as "first" and "second" in random order; which was which
 * goes into the report and is never said aloud.
 */
class AbRounds(
    private val kit: Kit,
    private val view: HeadView,
    private val baseFovDegrees: Double,
    private val random: Random = Random.Default,
) {
    private class Parameter(
        val name: String,
        val low: Double,
        val high: Double,
        val intro: Int,
        val question: Int,
        val apply: (SceneParams, Double) -> SceneParams,
    )

    /**
     * Ranges centred on what the first two wearers chose (test kit 0.6.0). The
     * mount round is gone: neither could tell ±5° apart, so the factory mount
     * stands and the round only cost them time.
     */
    private val parameters = listOf(
        Parameter("fov_scale", 0.7, 1.1, R.string.ab_fov_intro, R.string.ab_fov_question) { p, v ->
            p.copy(verticalFovDegrees = baseFovDegrees * v)
        },
        Parameter("prediction_ms", 0.0, 80.0, R.string.ab_prediction_intro, R.string.ab_prediction_question) { p, v ->
            p.copy(predictionMs = v)
        },
    )

    suspend fun run(): JSONObject {
        val report = JSONObject().put("base_fov_deg", baseFovDegrees)
        var current = SceneParams(baseFovDegrees, START_PREDICTION_MS, 0.0)
        kit.io.say(kit.text(R.string.ab_intro))

        for (parameter in parameters) {
            val staircase = Staircase(parameter.name, parameter.low, parameter.high, ROUNDS)
            val rounds = JSONArray()
            kit.io.say(kit.text(parameter.intro))
            while (!staircase.done) {
                val (lower, higher) = staircase.candidates()
                val lowerFirst = random.nextBoolean()
                val shown = if (lowerFirst) listOf(lower, higher) else listOf(higher, lower)
                for ((slot, value) in shown.withIndex()) {
                    val params = parameter.apply(current, value)
                    view.recenter(params)
                    kit.io.showScene(view, params)
                    kit.io.say(kit.text(if (slot == 0) R.string.ab_first else R.string.ab_second))
                    kit.io.cue()
                    delay(SHOW_MS)
                }
                kit.io.showScene(null, null)

                val question = kit.text(parameter.question)
                val choice = kit.io.ask(
                    question,
                    listOf(kit.choice("first", R.string.ab_first), kit.choice("second", R.string.ab_second), kit.choice("same", R.string.ab_same)),
                )
                kit.session.answer("ab_${parameter.name}", question, choice, rounds.length() + 1)
                val preferred = when (choice.id) {
                    "first" -> shown[0]
                    "second" -> shown[1]
                    else -> null
                }
                staircase.answer(preferred)
                rounds.put(
                    JSONObject()
                        .put("first", shown[0]).put("second", shown[1])
                        .put("choice", choice.id).put("preferred", preferred ?: JSONObject.NULL)
                )
            }
            current = parameter.apply(current, staircase.estimate)
            kit.session.outcome("ab_${parameter.name}", "%.3f".format(staircase.estimate))
            report.put(
                parameter.name,
                JSONObject()
                    .put("estimate", staircase.estimate)
                    .put("range", JSONArray(listOf(staircase.low, staircase.high)))
                    .put("settled_early", staircase.settled)
                    .put("rounds", rounds),
            )
        }
        report.put("final", JSONObject()
            .put("vertical_fov_deg", current.verticalFovDegrees)
            .put("prediction_ms", current.predictionMs)
            .put("mount_offset_deg", current.mountOffsetDegrees))
        kit.io.say(kit.text(R.string.ab_done))
        return report
    }

    companion object {
        const val ROUNDS = 3
        const val SHOW_MS = 7_000L
        const val START_PREDICTION_MS = 30.0
    }
}
