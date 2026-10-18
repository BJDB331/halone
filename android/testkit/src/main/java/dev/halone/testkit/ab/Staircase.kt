package dev.halone.testkit.ab

/**
 * Bisection driven by blind pairwise answers. Each round shows the points a
 * quarter of the way in from either end of the current range; the preferred one
 * keeps its half. "Same" means the range is already finer than the tester can
 * tell apart, so it ends there.
 */
class Staircase(val name: String, low: Double, high: Double, val rounds: Int) {

    data class Round(val lower: Double, val higher: Double, val preferred: Double?)

    var low = low
        private set
    var high = high
        private set

    private val log = mutableListOf<Round>()
    val history: List<Round> get() = log.toList()

    var settled = false
        private set

    val done: Boolean get() = settled || log.size >= rounds

    val estimate: Double get() = (low + high) / 2

    fun candidates(): Pair<Double, Double> {
        val quarter = (high - low) / 4
        return (low + quarter) to (high - quarter)
    }

    /** [preferred] is one of [candidates], or null for "no difference". */
    fun answer(preferred: Double?) {
        val (lower, higher) = candidates()
        log += Round(lower, higher, preferred)
        val middle = (low + high) / 2
        when (preferred) {
            null -> {
                low = lower
                high = higher
                settled = true
            }
            lower -> high = middle
            higher -> low = middle
            else -> throw IllegalArgumentException("$preferred was not a candidate")
        }
    }
}
