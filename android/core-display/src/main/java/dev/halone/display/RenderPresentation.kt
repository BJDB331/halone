package dev.halone.display

import android.app.Presentation
import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Build
import android.view.Display
import android.view.Surface
import android.view.SurfaceHolder
import android.view.WindowManager

/**
 * Puts a GL renderer on the glasses.
 *
 * A `Presentation` holding one `GLSurfaceView` is the whole of it, but two
 * details are not optional: the wide mode has to be pinned explicitly, because
 * the display advertises it only while the glasses are in SBS and will otherwise
 * hand back a mirror-mode surface; and `FLAG_KEEP_SCREEN_ON` here covers the
 * external display only, so the phone still needs its own hold (and, in the
 * real shell, a foreground service — without one the render thread stops within
 * seconds of being backgrounded and the glasses freeze on a stale frame).
 */
class RenderPresentation(
    private val context: Context,
    private val log: (String) -> Unit,
) {
    private var presentation: Presentation? = null

    var surfaceView: GLSurfaceView? = null
        private set

    /** Refresh rate of the mode asked for in the last [show], or the display's own. */
    var refreshHz: Float = 60f
        private set

    fun show(display: Display, renderer: GLSurfaceView.Renderer) {
        dismiss()

        val wide = widestStereoMode(display) ?: fastestMode(display)
        refreshHz = wide?.refreshRate ?: display.refreshRate

        val rate = refreshHz
        val view = object : GLSurfaceView(context) {}.apply {
            setEGLContextClientVersion(3)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            // The mode id alone is a request the system may weigh against
            // others; saying the surface itself is a fixed-rate source at that
            // rate is what tells it a 60 Hz mode would drop frames.
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        runCatching { holder.surface.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE) }
                    }
                }
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
            })
        }
        surfaceView = view

        presentation = Presentation(context, display).apply {
            window?.let { w ->
                wide?.let { mode ->
                    w.attributes = w.attributes.apply {
                        preferredDisplayModeId = mode.modeId
                        preferredRefreshRate = mode.refreshRate
                    }
                }
                w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            setContentView(view)
            show()
        }

        log("  presentation shown on display ${display.displayId}, requested mode ${wide?.modeId} at ${"%.0f".format(refreshHz)} Hz")
    }

    fun dismiss() {
        presentation?.dismiss()
        presentation = null
        surfaceView = null
    }

    private companion object {
        /**
         * No side-by-side mode means the glasses are in 2D: take the fastest
         * refresh at the current resolution, which on RayNeo GT glasses is
         * 120 Hz against the 60 the default mode may be.
         */
        fun fastestMode(display: Display): Display.Mode? {
            val current = display.mode
            return display.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .maxByOrNull { it.refreshRate }
        }

        /** Widest mode shaped like two 16:9 halves glued together. */
        fun widestStereoMode(display: Display): Display.Mode? =
            display.supportedModes
                .filter { it.physicalWidth >= 2 * it.physicalHeight }
                .maxByOrNull { it.physicalWidth.toLong() * it.physicalHeight }
    }
}
