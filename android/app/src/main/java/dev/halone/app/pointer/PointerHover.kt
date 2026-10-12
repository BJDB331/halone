package dev.halone.app.pointer

import android.os.Handler
import android.os.Looper
import dev.halone.compositor.HitPart
import dev.halone.compositor.Panel
import dev.halone.compositor.Scene
import dev.halone.compositor.StereoCompositor
import kotlin.math.abs

/**
 * Hover for every pointer, not only a mouse: the panel content under the
 * pointer hears where it rests, and a panel the pointer leaves hears that it
 * left. It follows the compositor's hit rather than input events, because the
 * gyro pointer and gaze move with the head, with no event at all.
 */
class PointerHover(
    private val compositor: () -> StereoCompositor?,
    private val scene: () -> Scene?,
    /** True while a finger is down on a panel, when the page has a touch, not a hover. */
    private val pressing: () -> Boolean,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var hovered: Panel? = null
    private var lastX = Float.NaN
    private var lastY = Float.NaN

    private val frame = object : Runnable {
        override fun run() {
            handler.postDelayed(this, FRAME_MS)
            if (!pressing()) update()
        }
    }

    fun start() {
        if (running) return
        running = true
        handler.post(frame)
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(frame)
        leave()
    }

    private fun update() {
        val hit = compositor()?.lastHit?.takeIf { it.part == HitPart.CONTENT }
        if (hit?.panel !== hovered) {
            leave()
            hovered = hit?.panel
        }
        hit ?: return
        if (abs(hit.hit.x - lastX) < MIN_MOVE_PX && abs(hit.hit.y - lastY) < MIN_MOVE_PX) return
        lastX = hit.hit.x
        lastY = hit.hit.y
        hit.panel.engine.dispatchHover(lastX, lastY)
    }

    private fun leave() {
        val panel = hovered ?: return
        hovered = null
        lastX = Float.NaN
        lastY = Float.NaN
        val scene = scene() ?: return
        val open = panel in scene.panels || panel === scene.dock || panel === scene.launcher || panel === scene.popup
        if (open) panel.engine.dispatchHoverExit()
    }

    private companion object {
        const val FRAME_MS = 16L

        /** Less than this is the head's tremor under the gyro pointer or gaze, not a move. */
        const val MIN_MOVE_PX = 1f
    }
}
