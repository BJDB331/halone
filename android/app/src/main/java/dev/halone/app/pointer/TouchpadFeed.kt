package dev.halone.app.pointer

import android.view.MotionEvent

/**
 * Feeds [gestures] from touch events, the phone screen's or a captured
 * trackpad's, with [toDp] turning the source's units into dp.
 */
fun TouchpadGestures.feed(event: MotionEvent, toDp: Float) {
    val at = event.eventTime
    val lifting = if (event.actionMasked == MotionEvent.ACTION_POINTER_UP) event.actionIndex else -1
    var sumX = 0f
    var sumY = 0f
    var count = 0
    for (i in 0 until event.pointerCount) {
        if (i == lifting) continue
        sumX += event.getX(i)
        sumY += event.getY(i)
        count++
    }
    val x = if (count > 0) sumX / count * toDp else 0f
    val y = if (count > 0) sumY / count * toDp else 0f
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> down(at, x, y)
        MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> fingersChanged(at, count, x, y)
        MotionEvent.ACTION_MOVE -> move(count, x, y)
        MotionEvent.ACTION_UP -> up(at)
        MotionEvent.ACTION_CANCEL -> cancel()
    }
}
