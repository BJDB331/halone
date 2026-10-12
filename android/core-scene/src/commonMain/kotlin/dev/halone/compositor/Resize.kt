package dev.halone.compositor

import kotlin.math.abs
import kotlin.math.hypot

/** A window corner. [sx] is -1 left, +1 right; [sy] is +1 top, -1 bottom, as the panel's own axes run. */
enum class Corner(val sx: Int, val sy: Int) {
    TOP_LEFT(-1, 1),
    TOP_RIGHT(1, 1),
    BOTTOM_LEFT(-1, -1),
    BOTTOM_RIGHT(1, -1);

    val opposite: Corner get() = entries.first { it.sx == -sx && it.sy == -sy }

    /** Where this corner is in a window's pixels, y down from its top edge. */
    fun pointPx(widthPx: Float, heightPx: Float) = PanelHit(if (sx < 0) 0f else widthPx, if (sy > 0) 0f else heightPx)
}

/**
 * The handles that resize a window, one past each corner, the same gap out as
 * the title bar. They take only what lies outside the window, so a click near
 * a corner of the page still reaches the page, and they show when the pointer
 * comes near the corner.
 */
object ResizeHandles {
    /** The handle's arc runs this far outside the window's edge, concentric with its corner. */
    const val GAP_PX = 14f
    const val THICKNESS_PX = 5f

    /** How far the arc carries on along each edge past the rounding. */
    const val EXTEND_PX = 28f

    /** How far outside the window, and inside the corner, a press still takes the handle. */
    const val REACH_OUTSIDE_PX = 64f
    const val REACH_INSIDE_PX = 40f

    /** Shown when the pointer is this close to the corner. */
    const val SHOW_WITHIN_PX = 150f

    /** The corner whose handle is at ([x], [y]) in a window's pixels, which must be outside the window. */
    fun cornerAt(widthPx: Int, heightPx: Int, x: Float, y: Float): Corner? {
        val w = widthPx.toFloat()
        val h = heightPx.toFloat()
        if (x in 0f..w && y in 0f..h) return null
        return Corner.entries.firstOrNull { corner ->
            val c = corner.pointPx(w, h)
            val outwardX = (x - c.x) * corner.sx
            val outwardY = (c.y - y) * corner.sy
            outwardX in -REACH_INSIDE_PX..REACH_OUTSIDE_PX && outwardY in -REACH_INSIDE_PX..REACH_OUTSIDE_PX
        }
    }

    /** The corner the pointer is near enough to show, if any. */
    fun cornerNear(widthPx: Int, heightPx: Int, x: Float, y: Float): Corner? = Corner.entries
        .map { it to it.pointPx(widthPx.toFloat(), heightPx.toFloat()) }
        .filter { (_, c) -> hypot(x - c.x, y - c.y) < SHOW_WITHIN_PX }
        .minByOrNull { (_, c) -> abs(x - c.x) + abs(y - c.y) }
        ?.first
}

/** How small and large a window may be made, in its pixels. */
data class SizeLimits(val minWidthPx: Int, val minHeightPx: Int, val maxWidthPx: Int, val maxHeightPx: Int) {
    fun clampWidth(px: Float) = px.coerceIn(minWidthPx.toFloat(), maxOf(minWidthPx, maxWidthPx).toFloat())
    fun clampHeight(px: Float) = px.coerceIn(minHeightPx.toFloat(), maxOf(minHeightPx, maxHeightPx).toFloat())

    companion object {
        /** Without anything declared: a sensible floor, and two and a half times the size the window opened at. */
        fun defaultFor(widthPx: Int, heightPx: Int) = SizeLimits(
            minWidthPx = minOf(DEFAULT_MIN_WIDTH_PX, widthPx),
            minHeightPx = minOf(DEFAULT_MIN_HEIGHT_PX, heightPx),
            maxWidthPx = (widthPx * MAX_GROWTH).toInt(),
            maxHeightPx = (heightPx * MAX_GROWTH).toInt(),
        )

        const val DEFAULT_MIN_WIDTH_PX = 480
        const val DEFAULT_MIN_HEIGHT_PX = 360
        const val MAX_GROWTH = 2.5f
    }
}

/** A resize in progress: the corner being dragged, and the size it would make. */
class ResizeDrag<P : ScenePanel>(val panel: P, val corner: Corner) {
    var widthPx: Int = panel.widthPx
        internal set
    var heightPx: Int = panel.heightPx
        internal set
}
