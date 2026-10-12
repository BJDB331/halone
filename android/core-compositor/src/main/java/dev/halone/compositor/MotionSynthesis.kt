package dev.halone.compositor

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent

/**
 * The pointer calls of [PanelEngine] as the `MotionEvent`s an Android view
 * tree expects: touchscreen fingers for taps, drags and scrolls, mouse events
 * for the wheel, hover and the middle button. [onTouch] and [onGeneric] are handed each event and must
 * not keep it.
 */
class MotionSynthesis(
    private val onTouch: (MotionEvent) -> Unit,
    private val onGeneric: (MotionEvent) -> Unit,
) {
    private var touchDownTime = 0L
    private var hoverX = 0f
    private var hoverY = 0f

    fun tap(x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, down, down, x, y)
        touch(MotionEvent.ACTION_UP, down, down + TAP_MS, x, y)
    }

    fun scroll(fromX: Float, fromY: Float, toX: Float, toY: Float, steps: Int) {
        val down = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, down, down, fromX, fromY)
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            touch(MotionEvent.ACTION_MOVE, down, down + i * STEP_MS, fromX + (toX - fromX) * t, fromY + (toY - fromY) * t)
        }
        touch(MotionEvent.ACTION_UP, down, down + steps * STEP_MS, toX, toY)
    }

    fun touch(phase: TouchPhase, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        if (phase == TouchPhase.DOWN) touchDownTime = now
        val action = when (phase) {
            TouchPhase.DOWN -> MotionEvent.ACTION_DOWN
            TouchPhase.MOVE -> MotionEvent.ACTION_MOVE
            TouchPhase.UP -> MotionEvent.ACTION_UP
            TouchPhase.CANCEL -> MotionEvent.ACTION_CANCEL
        }
        touch(action, touchDownTime, now, x, y)
    }

    fun wheel(x: Float, y: Float, horizontal: Float, vertical: Float) = mouse(MotionEvent.ACTION_SCROLL, x, y, horizontal, vertical)

    fun hover(x: Float, y: Float) {
        hoverX = x
        hoverY = y
        mouse(MotionEvent.ACTION_HOVER_MOVE, x, y, 0f, 0f)
    }

    fun hoverExit() = mouse(MotionEvent.ACTION_HOVER_EXIT, hoverX, hoverY, 0f, 0f)

    /** The middle button pressed and let go, as button events, which views that ignore the button let pass. */
    fun middleClick(x: Float, y: Float) {
        mouse(MotionEvent.ACTION_BUTTON_PRESS, x, y, 0f, 0f, MotionEvent.BUTTON_TERTIARY)
        mouse(MotionEvent.ACTION_BUTTON_RELEASE, x, y, 0f, 0f, 0)
    }

    /**
     * A finger on a touchscreen, said in full: the short `obtain` leaves the
     * source a bare pointer class and the tool unknown, and content that
     * filters by source (Unity, for one) ignores such events.
     */
    private fun touch(action: Int, downTime: Long, eventTime: Long, x: Float, y: Float) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1f
            size = 1f
        }
        val event = MotionEvent.obtain(downTime, eventTime, action, 1, arrayOf(properties), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try {
            onTouch(event)
        } finally {
            event.recycle()
        }
    }

    private fun mouse(action: Int, x: Float, y: Float, horizontal: Float, vertical: Float, buttons: Int = 0) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            setAxisValue(MotionEvent.AXIS_HSCROLL, horizontal)
            setAxisValue(MotionEvent.AXIS_VSCROLL, vertical)
        }
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, 1, arrayOf(properties), arrayOf(coords), 0, buttons, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
        try {
            onGeneric(event)
        } finally {
            event.recycle()
        }
    }

    private companion object {
        const val TAP_MS = 60L
        const val STEP_MS = 12L
    }
}
