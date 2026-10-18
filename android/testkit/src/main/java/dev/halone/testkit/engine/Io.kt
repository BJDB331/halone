package dev.halone.testkit.engine

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

    fun progress(done: Int, total: Int)
}
