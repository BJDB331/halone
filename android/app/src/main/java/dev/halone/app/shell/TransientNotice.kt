package dev.halone.app.shell

import android.os.Handler
import android.os.Looper
import dev.halone.compositor.Notice
import dev.halone.compositor.StereoCompositor

/**
 * A notice shown for a moment, such as the volume or "Screenshot saved". It
 * clears only what it put up, so it never takes down the bed mode countdown,
 * which shares the one notice the compositor draws. UI thread.
 */
class TransientNotice(private val compositor: () -> StereoCompositor?) {
    private val handler = Handler(Looper.getMainLooper())
    private var shown: Notice? = null
    private val hide = Runnable { clear() }

    fun show(text: String, progress: Float = 0f, forMs: Long = SHOWN_MS) {
        val notice = Notice(text, progress)
        compositor()?.notice = notice
        shown = notice
        handler.removeCallbacks(hide)
        handler.postDelayed(hide, forMs)
    }

    fun clear() {
        handler.removeCallbacks(hide)
        val target = compositor()
        if (target != null && shown != null && target.notice == shown) target.notice = null
        shown = null
    }

    private companion object {
        const val SHOWN_MS = 1_500L
    }
}
