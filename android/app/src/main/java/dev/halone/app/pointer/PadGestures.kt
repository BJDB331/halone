package dev.halone.app.pointer

import kotlin.math.hypot

/**
 * What the wearer is doing on a phone screen they cannot see.
 *
 * Gestures are told apart by finger count and by time and travel, never by
 * where they land. Positions are in dp and times in milliseconds, supplied by
 * the caller, so every rule here is testable without a touchscreen. What each
 * gesture *does* is a setting; this only says which one happened.
 *
 * - tap with 1, 2 or 3 fingers: lifted before the hold time
 * - hold with 1, 2 or 3 fingers: kept down past [holdMs], until a finger lifts
 * - one finger swiped past [DEADZONE_DP]: scroll, the finger's travel dragging the page
 */
class PadGestures(private val listener: Listener) {

    interface Listener {
        fun onTap(fingers: Int) {}
        fun onHoldStart(fingers: Int) {}
        fun onHoldEnd(fingers: Int) {}
        fun onScrollStart() {}
        fun onScrollMove(dxDp: Float, dyDp: Float) {}
        fun onScrollEnd() {}
    }

    private sealed interface State
    private data object Idle : State

    /**
     * Fingers are down and nothing is decided. The clock restarts when a finger
     * joins: a three-finger gesture arrives one finger at a time, and must not
     * become a two-finger hold on the way.
     */
    private data class Pending(val since: Long, val fingers: Int, val x: Float, val y: Float) : State
    private data class Swiping(var lastX: Float, var lastY: Float) : State
    private data class Holding(val fingers: Int) : State

    /** The gesture has been decided and acted on; ignore everything until all fingers lift. */
    private data object Spent : State

    private var state: State = Idle

    fun down(at: Long, x: Float, y: Float) {
        state = Pending(at, 1, x, y)
    }

    fun move(x: Float, y: Float) {
        when (val current = state) {
            is Pending -> if (current.fingers == 1 && hypot(x - current.x, y - current.y) > DEADZONE_DP) {
                state = Swiping(x, y)
                listener.onScrollStart()
                listener.onScrollMove(x - current.x, y - current.y)
            }
            is Swiping -> {
                listener.onScrollMove(x - current.lastX, y - current.lastY)
                current.lastX = x
                current.lastY = y
            }
            else -> Unit
        }
    }

    /** [fingers] is how many are down now, the new one included. */
    fun fingerAdded(at: Long, fingers: Int) {
        val current = state
        if (current is Pending) state = current.copy(since = at, fingers = fingers.coerceAtMost(MAX_FINGERS))
    }

    /** A finger lifted while at least one other stays down. */
    fun fingerLifted() {
        when (val current = state) {
            is Pending -> {
                listener.onTap(current.fingers)
                state = Spent
            }
            is Holding -> {
                listener.onHoldEnd(current.fingers)
                state = Spent
            }
            else -> Unit
        }
    }

    fun up(at: Long) {
        when (val current = state) {
            is Pending -> if (at - current.since < holdMs(current.fingers)) listener.onTap(current.fingers)
            is Swiping -> listener.onScrollEnd()
            is Holding -> listener.onHoldEnd(current.fingers)
            else -> Unit
        }
        state = Idle
    }

    fun cancel() = up(Long.MAX_VALUE)

    /** Promotes a gesture that has waited long enough. Call at the times [nextDeadline] asks for. */
    fun tick(at: Long) {
        val current = state
        if (current is Pending && at - current.since >= holdMs(current.fingers)) {
            state = Holding(current.fingers)
            listener.onHoldStart(current.fingers)
        }
    }

    /** When [tick] next has something to decide, or null. */
    fun nextDeadline(): Long? = (state as? Pending)?.let { it.since + holdMs(it.fingers) }

    /** What a held press turns out to be: the pointer moving makes it a drag, staying put a context menu. */
    enum class HoldIntent { UNDECIDED, DRAG, MENU }

    companion object {
        const val DEADZONE_DP = 16f
        const val MAX_FINGERS = 3

        /** How long the pointer has to stay put once a hold starts, and how far it may wander, for a menu. */
        const val STILL_MS = 350L
        const val STILL_SLOP_DEGREES = 1f

        /**
         * Decides a one-finger hold by what the pointer does after it starts,
         * as a long press on a touchscreen: moving past [STILL_SLOP_DEGREES]
         * drags, staying inside it for [STILL_MS] or letting go first opens
         * the context menu.
         */
        fun holdIntent(elapsedMs: Long, movedDegrees: Float, released: Boolean = false): HoldIntent = when {
            movedDegrees > STILL_SLOP_DEGREES -> HoldIntent.DRAG
            released || elapsedMs >= STILL_MS -> HoldIntent.MENU
            else -> HoldIntent.UNDECIDED
        }

        /**
         * One finger has to be held long enough not to be a slow tap. Two is
         * the grab, which worn wanted to be quick, but not so quick that a
         * two-finger tap cannot be made.
         */
        fun holdMs(fingers: Int): Long = when (fingers) {
            2 -> 300L
            else -> 600L
        }
    }
}
