package dev.halone.compositor

import android.content.Context
import android.graphics.Bitmap
import android.view.PointerIcon

/** How the pointer is drawn, by what moves it. */
enum class CursorStyle {
    /** The phone aimed like a remote: a beam from below the view to a ring on the target. */
    BEAM,

    /** Head gaze: the ring alone. */
    RETICLE,

    /** A mouse or touchpad: an arrow, or whatever shape the content under it asks for. */
    ARROW,
}

/** The shapes content can ask the arrow to take, named as CSS names them. */
enum class CursorShape(val css: String, val pointerIconType: Int) {
    DEFAULT("default", PointerIcon.TYPE_ARROW),
    POINTER("pointer", PointerIcon.TYPE_HAND),
    TEXT("text", PointerIcon.TYPE_TEXT),
    VERTICAL_TEXT("vertical-text", PointerIcon.TYPE_VERTICAL_TEXT),
    CROSSHAIR("crosshair", PointerIcon.TYPE_CROSSHAIR),
    GRAB("grab", PointerIcon.TYPE_GRAB),
    GRABBING("grabbing", PointerIcon.TYPE_GRABBING),
    NOT_ALLOWED("not-allowed", PointerIcon.TYPE_NO_DROP),
    ALL_SCROLL("all-scroll", PointerIcon.TYPE_ALL_SCROLL),
    EW_RESIZE("ew-resize", PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW),
    NS_RESIZE("ns-resize", PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW),
    NWSE_RESIZE("nwse-resize", PointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW),
    NESW_RESIZE("nesw-resize", PointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW),
    HELP("help", PointerIcon.TYPE_HELP),
    WAIT("wait", PointerIcon.TYPE_WAIT),
    ZOOM_IN("zoom-in", PointerIcon.TYPE_ZOOM_IN),
    ZOOM_OUT("zoom-out", PointerIcon.TYPE_ZOOM_OUT),
    CONTEXT_MENU("context-menu", PointerIcon.TYPE_CONTEXT_MENU),
    COPY("copy", PointerIcon.TYPE_COPY),
    ALIAS("alias", PointerIcon.TYPE_ALIAS),
    CELL("cell", PointerIcon.TYPE_CELL),

    /** Content that hides the pointer, as CSS `cursor: none` does. */
    NONE("none", PointerIcon.TYPE_NULL),
    ;

    companion object {
        private var systemIcons: Map<CursorShape, PointerIcon>? = null

        /**
         * The shape a view asked for with `setPointerIcon`, as WebView and
         * GeckoView do for the CSS cursor. [PointerIcon] keeps its type to
         * itself, but a system icon equals another of the same type.
         */
        fun of(context: Context, icon: PointerIcon?): CursorShape {
            icon ?: return DEFAULT
            val icons = systemIcons ?: entries.associateWith { PointerIcon.getSystemIcon(context, it.pointerIconType) }.also { systemIcons = it }
            return icons.entries.firstOrNull { it.value == icon }?.key ?: DEFAULT
        }
    }
}

/** A cursor picture with its hotspot, as fractions of its width and height from the top left. */
class CursorImage(val bitmap: Bitmap, val hotspotX: Float, val hotspotY: Float)
