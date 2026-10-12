package dev.halone.engine.plugin

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import dev.halone.compositor.MotionSynthesis
import dev.halone.compositor.PanelEngine
import dev.halone.compositor.TouchPhase
import dev.halone.plugin.IPanel
import dev.halone.plugin.IPanelHost
import dev.halone.plugin.KeyboardRequest
import dev.halone.plugin.KeyboardState
import dev.halone.plugin.PluginContract
import dev.halone.plugin.TextInputEvent

/** What a plugin's panel may do to its own window. Called on the main thread. */
interface PluginWindow {
    fun setTitle(title: String)
    fun setBackButton(visible: Boolean)
    fun close()
    fun focus()
    fun setPinned(pinned: Boolean)
    fun resize(widthPx: Int, heightPx: Int)
    fun setSizeLimits(minWidthPx: Int, minHeightPx: Int, maxWidthPx: Int, maxHeightPx: Int)
}

/**
 * A window drawn by a plugin, in the plugin's process, straight into the
 * `Surface` of this panel's texture; input goes back over binder as
 * `MotionEvent`s and `KeyEvent`s.
 *
 * When there is nothing of the plugin's to show (opening, refused, or its
 * process gone) the shell draws a card into the surface itself, with a fresh
 * `Surface` released straight after, so the plugin can connect to it again; a
 * tap on a card that offers it opens the app again. Never a frozen last frame.
 */
class PluginPanelEngine(
    /** Null when the plugin is not there to open the app: removed, disabled, or untrusted. */
    private val connection: PluginConnection?,
    private val appId: String,
    private val args: Bundle,
    private val appName: String,
    initialWidthPx: Int,
    initialHeightPx: Int,
    private val window: PluginWindow,
    private val log: (String) -> Unit,
    /** The phone's keyboard, for the panel's text fields; null where there is none. */
    private val keyboard: PluginKeyboard? = null,
) : PanelEngine {
    private enum class State { IDLE, OPENING, OPEN, STOPPED, FAILED }

    private val main = Handler(Looper.getMainLooper())

    @Volatile
    override var widthPx: Int = initialWidthPx
        private set

    @Volatile
    override var heightPx: Int = initialHeightPx
        private set

    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null
    private var panel: IPanel? = null
    private var state = State.IDLE
    private var closed = false
    private var card: Pair<String, String?>? = null

    private val onStop: () -> Unit = {
        if (state == State.OPEN || state == State.OPENING) {
            log("  plugin panel: $appName stopped")
            panel = null
            keyboard?.hide(keyboardTarget)
            window.setBackButton(false)
            show(State.STOPPED, "$appName stopped", "Tap to open it again")
        }
    }

    private val host = object : IPanelHost.Stub() {
        override fun setTitle(title: String?) = fromPanel { window.setTitle(title.orEmpty()) }
        override fun close() = fromPanel { window.close() }
        override fun focus() = fromPanel { window.focus() }
        override fun setBackButton(visible: Boolean) = fromPanel { window.setBackButton(visible) }
        override fun setPinned(pinned: Boolean) = fromPanel { window.setPinned(pinned) }
        override fun requestResize(widthPx: Int, heightPx: Int) = fromPanel { window.resize(widthPx, heightPx) }
        override fun setSizeLimits(minWidthPx: Int, minHeightPx: Int, maxWidthPx: Int, maxHeightPx: Int) =
            fromPanel { window.setSizeLimits(minWidthPx, minHeightPx, maxWidthPx, maxHeightPx) }

        override fun showKeyboard(request: Bundle?) {
            val parsed = KeyboardRequest.fromBundle(request ?: Bundle())
            fromPanel { keyboard?.show(keyboardTarget, parsed) }
        }

        override fun hideKeyboard() = fromPanel { keyboard?.hide(keyboardTarget) }

        override fun updateKeyboard(state: Bundle?) {
            val parsed = KeyboardState.fromBundle(state ?: Bundle())
            fromPanel { keyboard?.update(keyboardTarget, parsed) }
        }
    }

    private val keyboardTarget = object : KeyboardTarget {
        override fun onTextInput(event: TextInputEvent) {
            val remote = panel ?: return
            call { remote.dispatchTextInput(event.toBundle()) }
        }

        override fun onKey(event: KeyEvent) {
            dispatchKey(event)
        }
    }

    private fun fromPanel(block: () -> Unit) {
        main.post { if (!closed && state == State.OPEN) block() }
    }

    private val motion = MotionSynthesis(onTouch = ::send, onGeneric = ::send)

    override fun createTexture(textureId: Int): SurfaceTexture {
        val texture = SurfaceTexture(textureId).apply { setDefaultBufferSize(widthPx, heightPx) }
        surfaceTexture = texture
        surface = Surface(texture)
        return texture
    }

    override fun attach() {
        connection?.addStopListener(onStop)
        open()
    }

    private fun open() {
        val plugin = connection ?: return show(State.FAILED, "$appName is not available", "Its plugin is not installed or not enabled")
        show(State.OPENING, "Opening $appName…", null)
        plugin.whenConnected { provider ->
            val opened = runCatching { provider.open(appId, args, host) }
            main.post { onOpened(opened) }
        }
    }

    private fun onOpened(opened: Result<IPanel?>) {
        val remote = opened.getOrNull()
        if (closed) {
            remote?.let { runCatching { it.close() } }
            return
        }
        if (remote == null) {
            val reason = opened.exceptionOrNull()?.let { it.message ?: it.toString() } ?: "The plugin refused it"
            log("  plugin panel: $appName could not open: $reason")
            return show(State.FAILED, "$appName could not open", reason)
        }
        val target = surface ?: return
        panel = remote
        state = State.OPEN
        card = null
        call { remote.attach(target, widthPx, heightPx, PluginContract.DENSITY_DPI) }
    }

    private fun show(newState: State, title: String, detail: String?) {
        state = newState
        card = title to detail
        paint(ATTEMPTS)
    }

    /** Draws the card, trying again shortly while the plugin's dead producer still holds the surface. */
    private fun paint(attemptsLeft: Int) {
        if (closed) return
        val (title, detail) = card ?: return
        val texture = surfaceTexture ?: return
        val drawing = Surface(texture)
        val drawn = try {
            val canvas = drawing.lockCanvas(null)
            drawCard(canvas, title, detail)
            drawing.unlockCanvasAndPost(canvas)
            true
        } catch (e: Exception) {
            if (attemptsLeft <= 1) log("  plugin panel: could not draw the card: $e")
            false
        } finally {
            drawing.release()
        }
        if (!drawn && attemptsLeft > 1) main.postDelayed({ paint(attemptsLeft - 1) }, RETRY_MS)
    }

    private fun drawCard(canvas: Canvas, title: String, detail: String?) {
        canvas.drawColor(BACKGROUND)
        val unit = minOf(widthPx, heightPx) / 20f
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = unit * 1.4f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DETAIL
            textSize = unit
            textAlign = Paint.Align.CENTER
        }
        val x = widthPx / 2f
        val y = heightPx / 2f
        canvas.drawText(title, x, if (detail == null) y else y - unit * 0.4f, titlePaint)
        detail?.let { canvas.drawText(it.take(MAX_DETAIL), x, y + unit * 1.4f, detailPaint) }
    }

    override fun resize(widthPx: Int, heightPx: Int) {
        if (widthPx == this.widthPx && heightPx == this.heightPx) return
        this.widthPx = widthPx
        this.heightPx = heightPx
        surfaceTexture?.setDefaultBufferSize(widthPx, heightPx)
        val remote = panel
        if (remote != null) call { remote.resize(widthPx, heightPx, PluginContract.DENSITY_DPI) } else paint(ATTEMPTS)
    }

    private val reopens: Boolean get() = state == State.STOPPED || state == State.FAILED && connection != null

    /** Opens a new session if the panel shows a stopped or refused card, as launching an open app should. Main thread. */
    fun reopenIfStopped() {
        if (!closed && reopens) open()
    }

    override fun dispatchTap(x: Float, y: Float) {
        if (reopens) open() else motion.tap(x, y)
    }

    override fun dispatchScroll(fromX: Float, fromY: Float, toX: Float, toY: Float, steps: Int) =
        motion.scroll(fromX, fromY, toX, toY, steps)

    override fun dispatchTouch(phase: TouchPhase, x: Float, y: Float) {
        if (reopens) {
            if (phase == TouchPhase.UP) open()
            return
        }
        motion.touch(phase, x, y)
    }

    override fun dispatchWheel(x: Float, y: Float, horizontal: Float, vertical: Float) = motion.wheel(x, y, horizontal, vertical)

    override fun dispatchHover(x: Float, y: Float) = motion.hover(x, y)

    override fun dispatchHoverExit() = motion.hoverExit()

    override fun dispatchMiddleClick(x: Float, y: Float) = motion.middleClick(x, y)

    override fun dispatchBack() {
        val remote = panel ?: return
        call { remote.back() }
    }

    override fun dispatchContextMenu(x: Float, y: Float) {
        val remote = panel ?: return
        call { remote.contextMenu(x, y) }
    }

    override fun dispatchKey(event: KeyEvent): Boolean {
        val remote = panel ?: return false
        call { remote.dispatchKey(event) }
        return true
    }

    private fun send(event: MotionEvent) {
        val remote = panel ?: return
        call { remote.dispatchMotion(event) }
    }

    private inline fun call(block: () -> Unit) {
        try {
            block()
        } catch (_: RemoteException) {
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        keyboard?.hide(keyboardTarget)
        connection?.removeStopListener(onStop)
        panel?.let { remote -> call { remote.close() } }
        panel = null
        main.removeCallbacksAndMessages(null)
        surface?.release()
        surface = null
    }

    private companion object {
        const val ATTEMPTS = 10
        const val RETRY_MS = 150L
        const val MAX_DETAIL = 90
        val BACKGROUND = Color.rgb(0x16, 0x18, 0x1d)
        val DETAIL = Color.rgb(0xa0, 0xa6, 0xb0)
    }
}
