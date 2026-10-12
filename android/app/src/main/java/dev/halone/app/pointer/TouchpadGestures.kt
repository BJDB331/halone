package dev.halone.app.pointer

import kotlin.math.hypot

/**
 * A touchpad: the phone's screen, or a trackpad whose fingers the app has
 * captured. Positions are the fingers' centroid in dp, times in milliseconds.
 *
 * - one finger moving moves the cursor, from the first sample: a touchpad has
 *   no deadzone for pointing, only for telling a tap from a move
 * - two fingers moving scroll, once they have moved too far for a tap: a
 *   scroll presses the page, and a press that lifts in place is a click
 * - a tap is fingers lifted within [TAP_MS] having moved less than [TAP_SLOP_DP]
 * - a double tap held down drags: the second touch presses where the cursor is
 *   and carries the press with it until it lifts, so a quick second tap is a
 *   click too
 * - fingers kept still for [HOLD_MS] hold; while a hold lasts, moving any
 *   number of fingers moves the cursor, so a held window can be carried
 */
class TouchpadGestures(private val listener: Listener) {

    interface Listener {
        fun onMove(dxDp: Float, dyDp: Float) {}
        fun onScroll(dxDp: Float, dyDp: Float) {}
        fun onScrollEnd() {}
        fun onTap(fingers: Int) {}
        fun onHoldStart(fingers: Int) {}
        fun onHoldEnd(fingers: Int) {}
        fun onDragStart() {}
        fun onDragEnd() {}
    }

    private var active = false
    private var downAt = 0L
    private var since = 0L
    private var fingers = 0
    private var lastX = 0f
    private var lastY = 0f
    private var travel = 0f
    private var holding = false
    private var dragging = false
    private var scrolling = false
    private var pendingScrollX = 0f
    private var pendingScrollY = 0f
    private var lastTapAt = Long.MIN_VALUE / 2

    fun down(at: Long, x: Float, y: Float) {
        active = true
        downAt = at
        since = at
        fingers = 1
        lastX = x
        lastY = y
        travel = 0f
        holding = false
        scrolling = false
        pendingScrollX = 0f
        pendingScrollY = 0f
        dragging = at - lastTapAt <= DOUBLE_TAP_MS
        if (dragging) listener.onDragStart()
    }

    /** [count] fingers are down now, with their centroid at [x], [y]. */
    fun fingersChanged(at: Long, count: Int, x: Float, y: Float) {
        if (!active) return
        if (count > fingers) {
            fingers = count.coerceAtMost(MAX_FINGERS)
            since = at
        }
        if (count != 2) endScroll()
        // The centroid jumps when a finger joins or leaves; that is not movement.
        lastX = x
        lastY = y
    }

    fun move(count: Int, x: Float, y: Float) {
        if (!active) return
        val dx = x - lastX
        val dy = y - lastY
        lastX = x
        lastY = y
        travel += hypot(dx, dy)
        when {
            dragging || holding || count == 1 -> listener.onMove(dx, dy)
            count == 2 -> {
                pendingScrollX += dx
                pendingScrollY += dy
                if (!scrolling && travel < TAP_SLOP_DP) return
                scrolling = true
                listener.onScroll(pendingScrollX, pendingScrollY)
                pendingScrollX = 0f
                pendingScrollY = 0f
            }
        }
    }

    fun up(at: Long) {
        if (!active) return
        active = false
        endScroll()
        when {
            dragging -> listener.onDragEnd()
            holding -> listener.onHoldEnd(fingers)
            travel < TAP_SLOP_DP && at - downAt < TAP_MS -> {
                listener.onTap(fingers)
                lastTapAt = if (fingers == 1) at else Long.MIN_VALUE / 2
            }
        }
        dragging = false
        holding = false
    }

    fun cancel() = up(Long.MAX_VALUE)

    /**
     * The touch did something else — a trackpad's physical click — so it must
     * not also be a tap or a hold. Moving still moves the cursor.
     */
    fun consume() {
        travel = Float.MAX_VALUE
    }

    private fun endScroll() {
        pendingScrollX = 0f
        pendingScrollY = 0f
        if (!scrolling) return
        scrolling = false
        listener.onScrollEnd()
    }

    fun tick(at: Long) {
        if (!active || holding || dragging || travel >= TAP_SLOP_DP) return
        if (at - since >= HOLD_MS) {
            holding = true
            listener.onHoldStart(fingers)
        }
    }

    fun nextDeadline(): Long? = if (active && !holding && !dragging && travel < TAP_SLOP_DP) since + HOLD_MS else null

    companion object {
        const val TAP_MS = 250L
        const val TAP_SLOP_DP = 10f
        const val DOUBLE_TAP_MS = 300L
        const val HOLD_MS = 600L
        const val MAX_FINGERS = 3
    }
}
