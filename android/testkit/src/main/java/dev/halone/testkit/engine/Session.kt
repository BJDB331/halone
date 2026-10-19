package dev.halone.testkit.engine

import java.io.File

/**
 * What one run of a script produced: timestamped markers, answers, notes and
 * attached files. Markers use the same clock as capture chunks, so every
 * recorded byte can be placed inside the step that produced it.
 */
class Session(
    @Volatile var profileId: String = "unknown",
    private val clock: () -> Long = System::nanoTime,
) {
    data class Marker(val label: String, val hostNs: Long)
    data class Answer(val id: String, val question: String, val choice: String, val attempt: Int)

    val startedAtNs: Long = clock()
    val startedAtWallMs: Long = System.currentTimeMillis()

    @Volatile
    var recording: Boolean = false
        private set

    private val lock = Any()
    private val markerList = mutableListOf<Marker>()
    private val answerList = mutableListOf<Answer>()
    private val noteList = mutableListOf<String>()
    private val attachments = linkedMapOf<String, Any>()
    private val secretSet = linkedSetOf<String>()
    private val outcomeMap = linkedMapOf<String, String>()

    val markers: List<Marker> get() = synchronized(lock) { markerList.toList() }
    val answers: List<Answer> get() = synchronized(lock) { answerList.toList() }
    val notes: List<String> get() = synchronized(lock) { noteList.toList() }
    val secrets: Set<String> get() = synchronized(lock) { secretSet.toSet() }
    val outcomes: Map<String, String> get() = synchronized(lock) { outcomeMap.toMap() }

    /** In attachment order; values are `ByteArray` or `File`. */
    val files: Map<String, Any> get() = synchronized(lock) { LinkedHashMap(attachments) }

    fun now(): Long = clock()

    fun mark(label: String) = synchronized(lock) { markerList += Marker(label, clock()) }

    /** Captures write only inside a segment, which keeps a ten-minute run to megabytes. */
    suspend fun <T> segment(label: String, block: suspend () -> T): T {
        mark("$label:start")
        recording = true
        try {
            return block()
        } finally {
            recording = false
            mark("$label:end")
        }
    }

    fun answer(id: String, question: String, choice: Choice, attempt: Int = 1) = synchronized(lock) {
        answerList += Answer(id, question, choice.id, attempt)
    }

    fun note(text: String) = synchronized(lock) { noteList += text }

    /** A short machine-readable result per step, e.g. `sensors -> connected`. */
    fun outcome(step: String, result: String) = synchronized(lock) { outcomeMap[step] = result }

    fun attach(name: String, bytes: ByteArray) = synchronized(lock) { attachments[name] = bytes }

    fun attach(name: String, text: String) = attach(name, text.toByteArray(Charsets.UTF_8))

    fun attach(name: String, file: File) = synchronized(lock) { attachments[name] = file }

    /** Removed from every file in the bundle, wherever it appears. */
    fun secret(value: String) {
        if (value.length >= MIN_SECRET_LENGTH) synchronized(lock) { secretSet += value }
    }

    private companion object {
        const val MIN_SECRET_LENGTH = 4
    }
}

/**
 * Runs [block] until it returns true, at most [times] times. The attempt number
 * is passed in so the script can say something different on a retry.
 */
inline fun retrying(times: Int, block: (attempt: Int) -> Boolean): Boolean {
    for (attempt in 1..times) if (block(attempt)) return true
    return false
}
