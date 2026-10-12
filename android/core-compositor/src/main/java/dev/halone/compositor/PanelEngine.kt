package dev.halone.compositor

import android.graphics.SurfaceTexture

/**
 * A source of pixels for one panel: a WebView, a browser session, a video
 * decoder.
 *
 * Panel creation is a **two-phase handshake across threads**, and every engine
 * so far has forced it for its own reasons. A `SurfaceTexture` needs a texture
 * name from a live GL context, so [createTexture] can only run on the render
 * thread; GeckoView asserts the UI thread in `acquireDisplay` and a
 * `VirtualDisplay` plus `Presentation` needs it too, so [attach] can only run
 * there. The compositor drives both halves in order.
 *
 * Coordinates in [dispatchTap] and [dispatchScroll] are panel pixels. Turning a
 * pointer ray into those is [Scene]'s job, not the engine's.
 */
enum class TouchPhase { DOWN, MOVE, UP, CANCEL }

/**
 * A second producer shown in a band across the top of a panel, above the
 * engine's own content: a browser's toolbar over its page. Its texture is
 * [engine]'s full size, and only the top [visibleHeightPx] of it is shown, so
 * the band can grow and shrink without anything being resized.
 */
interface TopLayer {
    val engine: PanelEngine
    val visibleHeightPx: Int
}

interface PanelEngine {

    /** Drawn above the content, or null; the panel's [heightPx] includes its visible part. */
    val topLayer: TopLayer? get() = null

    /** Lays the content out at a new size, from the end of a resize. UI thread. */
    fun resize(widthPx: Int, heightPx: Int) = Unit

    /** Panel resolution. One CSS pixel is one panel pixel, by construction. */
    val widthPx: Int
    val heightPx: Int

    /** Phase one, on the **render thread**. */
    fun createTexture(textureId: Int): SurfaceTexture

    /** Phase two, on the **UI thread**. */
    fun attach()

    fun dispatchTap(x: Float, y: Float)

    fun dispatchScroll(fromX: Float, fromY: Float, toX: Float, toY: Float, steps: Int = 12)

    /**
     * One step of a touch the wearer is performing live, for gestures whose
     * shape is not known in advance: a finger dragging a page, a press held
     * while the pointer moves. Timed by the caller, so a fling keeps its speed.
     */
    fun dispatchTouch(phase: TouchPhase, x: Float, y: Float) = Unit

    /** A mouse wheel over panel pixel ([x], [y]), in wheel notches; positive [vertical] is up. */
    fun dispatchWheel(x: Float, y: Float, horizontal: Float, vertical: Float) = Unit

    /** A pointer resting over panel pixel ([x], [y]), so pages can show hover state. */
    fun dispatchHover(x: Float, y: Float) = Unit

    /** The pointer left the panel, or something else took it: hover state ends. */
    fun dispatchHoverExit() = Unit

    /** The arrow's shape where the pointer last hovered, as the content asked; read on the GL thread. */
    val cursorShape: CursorShape get() = CursorShape.DEFAULT

    /** A middle click at panel pixel ([x], [y]); on a link in a browser, it opens in a new tab. */
    fun dispatchMiddleClick(x: Float, y: Float) = Unit

    /**
     * The wearer asked for a context menu at panel pixel ([x], [y]): a right
     * click, a two-finger tap, a still hold. The page decides what to offer.
     */
    fun dispatchContextMenu(x: Float, y: Float) = Unit

    /** The title bar's Back button, shown while the panel's [ScenePanel.backButton] is set. */
    fun dispatchBack() = Unit

    /** A key from a physical keyboard, for whatever the page has focused. True if it was taken. */
    fun dispatchKey(event: android.view.KeyEvent): Boolean = false

    fun close()
}
