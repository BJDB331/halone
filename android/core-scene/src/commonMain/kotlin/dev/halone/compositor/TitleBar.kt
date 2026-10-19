package dev.halone.compositor

import kotlin.math.min

/** What part of a window a hit landed on. */
enum class HitPart(val corner: Corner? = null) {
    CONTENT,
    TITLE_BAR,
    BACK,
    PIN,
    /** Cycles the window through preset shapes: landscape, portrait, square. */
    SIZE,
    CLOSE,
    RESIZE_TOP_LEFT(Corner.TOP_LEFT),
    RESIZE_TOP_RIGHT(Corner.TOP_RIGHT),
    RESIZE_BOTTOM_LEFT(Corner.BOTTOM_LEFT),
    RESIZE_BOTTOM_RIGHT(Corner.BOTTOM_RIGHT);

    companion object {
        fun resizing(corner: Corner) = entries.first { it.corner == corner }
    }
}

/**
 * A region in angles: a heading with a half-width either side, and the
 * elevations of its bottom and top edges. Radians, native signs.
 */
data class AngularRect(val yaw: Float, val halfWidth: Float, val bottom: Float, val top: Float)

/** A rectangle in a panel's pixels, y down from the panel's top edge. */
data class PanelRect(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height

    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
}

/**
 * The pill under every window: a grip, Back when the window asks for it, the
 * title, the shape button when it is turned on, pin and close.
 *
 * Its geometry lives here because three things have to agree on it: the
 * compositor that draws it, the hit test that decides what a click on it does,
 * and the simulator that renders it in HTML.
 */
object TitleBar {
    const val GAP_PX = 14f
    const val HEIGHT_PX = 56f
    const val BUTTON_PX = 40f
    const val BUTTON_GAP_PX = 6f
    const val PADDING_START_PX = 20f
    const val PADDING_END_PX = 10f
    const val BACK_LEFT_PX = 38f
    private const val WIDTH_FRACTION = 0.45f
    private const val MIN_WIDTH_PX = 360f
    private const val MAX_WIDTH_PX = 640f

    fun bar(panelWidthPx: Int, panelHeightPx: Int): PanelRect {
        val width = (panelWidthPx * WIDTH_FRACTION).coerceIn(MIN_WIDTH_PX, min(MAX_WIDTH_PX, panelWidthPx.toFloat()))
        return PanelRect((panelWidthPx - width) / 2f, panelHeightPx + GAP_PX, width, HEIGHT_PX)
    }

    fun close(panelWidthPx: Int, panelHeightPx: Int): PanelRect {
        val bar = bar(panelWidthPx, panelHeightPx)
        return button(bar, bar.right - PADDING_END_PX - BUTTON_PX)
    }

    fun pin(panelWidthPx: Int, panelHeightPx: Int): PanelRect {
        val close = close(panelWidthPx, panelHeightPx)
        return button(bar(panelWidthPx, panelHeightPx), close.left - BUTTON_GAP_PX - BUTTON_PX)
    }

    /** Left of pin: cycles the window through preset shapes. */
    fun size(panelWidthPx: Int, panelHeightPx: Int): PanelRect {
        val pin = pin(panelWidthPx, panelHeightPx)
        return button(bar(panelWidthPx, panelHeightPx), pin.left - BUTTON_GAP_PX - BUTTON_PX)
    }

    /**
     * The shapes the size button cycles through, in pixels: landscape 16:9,
     * portrait 9:16 and 4:3. Android apps that start landscape open portrait
     * by default, which is what this is for.
     */
    val SIZE_PRESETS: List<Pair<Int, Int>> = listOf(1280 to 720, 720 to 1280, 1024 to 768)

    /** The preset after the one [widthPx] x [heightPx] is, or the first. */
    fun nextSize(widthPx: Int, heightPx: Int): Pair<Int, Int> {
        val at = SIZE_PRESETS.indexOf(widthPx to heightPx)
        return SIZE_PRESETS[(at + 1) % SIZE_PRESETS.size]
    }

    /** Right after the grip, where a window that asks for one has its Back button. */
    fun back(panelWidthPx: Int, panelHeightPx: Int): PanelRect {
        val bar = bar(panelWidthPx, panelHeightPx)
        return button(bar, bar.left + BACK_LEFT_PX)
    }

    /** Which part of the bar is at ([x], [y]) in panel pixels, or null if none. */
    fun partAt(panelWidthPx: Int, panelHeightPx: Int, x: Float, y: Float, back: Boolean = false, shape: Boolean = false): HitPart? = when {
        !bar(panelWidthPx, panelHeightPx).contains(x, y) -> null
        back && back(panelWidthPx, panelHeightPx).contains(x, y) -> HitPart.BACK
        close(panelWidthPx, panelHeightPx).contains(x, y) -> HitPart.CLOSE
        pin(panelWidthPx, panelHeightPx).contains(x, y) -> HitPart.PIN
        shape && size(panelWidthPx, panelHeightPx).contains(x, y) -> HitPart.SIZE
        else -> HitPart.TITLE_BAR
    }

    private fun button(bar: PanelRect, left: Float) =
        PanelRect(left, bar.top + (bar.height - BUTTON_PX) / 2f, BUTTON_PX, BUTTON_PX)
}
