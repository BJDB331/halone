package dev.halone.testkit.engine

import dev.halone.testkit.ab.HeadView
import dev.halone.testkit.ab.SceneParams

data class Choice(val id: String, val label: String)

/**
 * Everything a script does with the person holding the phone. The tester is
 * usually wearing the glasses, so every call is spoken as well as shown.
 */
interface Io {
    suspend fun say(text: String)

    suspend fun ask(question: String, choices: List<Choice>): Choice

    /** Null when skipped. */
    suspend fun askText(question: String, skip: Choice): String?

    /**
     * Shows [prompt] until [condition] holds, [timeoutMs] passes, or the tester
     * taps [skip]. True only when the condition was met.
     */
    suspend fun waitUntil(
        prompt: String,
        timeoutMs: Long,
        skip: Choice?,
        condition: suspend () -> Boolean,
    ): Boolean

    /** A short beep: the moment to start a movement. */
    suspend fun cue()

    /** Replaces the instruction card on the glasses with a head-tracked scene; nulls bring the card back. */
    fun showScene(view: HeadView?, params: SceneParams?)

    fun progress(done: Int, total: Int)
}
