package dev.halone.glasses.input

/**
 * Single and double taps, per side.
 *
 * A side with no double-tap action reports every tap as a single at once. A
 * side with one has to wait out [DOUBLE_WINDOW_MS] after a tap before it can
 * call it a single, so enabling double taps on a side delays its single tap by
 * that much. Times are in milliseconds, supplied by the caller.
 */
class TapGestures(
    private val wantsDouble: (TapSide) -> Boolean,
    private val listener: Listener,
) {
    /** [at] is when the gesture's first tap landed, which is where it was aimed. */
    interface Listener {
        fun onSingle(side: TapSide, at: Long)
        fun onDouble(side: TapSide, at: Long)
    }

    private var pendingSide: TapSide? = null
    private var pendingAt = 0L

    fun tap(side: TapSide, at: Long) {
        val waiting = pendingSide
        if (waiting == side && at - pendingAt <= DOUBLE_WINDOW_MS) {
            pendingSide = null
            listener.onDouble(side, pendingAt)
            return
        }
        if (waiting != null) {
            pendingSide = null
            listener.onSingle(waiting, pendingAt)
        }
        if (wantsDouble(side)) {
            pendingSide = side
            pendingAt = at
        } else {
            listener.onSingle(side, at)
        }
    }

    fun tick(at: Long) {
        val waiting = pendingSide ?: return
        if (at - pendingAt > DOUBLE_WINDOW_MS) {
            pendingSide = null
            listener.onSingle(waiting, pendingAt)
        }
    }

    fun nextDeadline(): Long? = if (pendingSide != null) pendingAt + DOUBLE_WINDOW_MS + 1 else null

    companion object {
        /** Measured doubles were 203–239 ms apart; this leaves room for a slower hand. */
        const val DOUBLE_WINDOW_MS = 350L
    }
}
