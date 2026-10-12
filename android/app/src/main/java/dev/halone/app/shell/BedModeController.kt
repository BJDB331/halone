package dev.halone.app.shell

import android.view.Choreographer
import dev.halone.compositor.Notice
import dev.halone.compositor.StereoCompositor
import dev.halone.glasses.fusion.HeadTracker
import dev.halone.shell.BedModeStep
import dev.halone.shell.BedModeTrigger
import dev.halone.shell.ShellSettings

/** Runs [BedModeTrigger] once a frame on the UI thread and shows its countdown. */
class BedModeController(
    private val head: HeadTracker,
    private val compositor: () -> StereoCompositor?,
    private val settings: ShellSettings,
    private val setBedMode: (Boolean) -> Unit,
) : Choreographer.FrameCallback {
    private val trigger = BedModeTrigger()
    private var shown: Notice? = null
    private var running = false
    private var appliedForwardDegrees: Double? = null

    fun start() {
        running = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        show(null)
        if (head.inBedMode) setBedMode(false)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        tick(frameTimeNanos)
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun tick(nowNs: Long) {
        if (!settings.boolean(ShellSettings.BED_MODE_ENABLED)) {
            trigger.reset()
            show(null)
            if (head.inBedMode) setBedMode(false)
            return
        }
        val forward = settings.number(ShellSettings.BED_MODE_FORWARD_DEGREES)
        if (!head.inBedMode) {
            appliedForwardDegrees = null
        } else if (appliedForwardDegrees != forward) {
            if (appliedForwardDegrees != null) setBedMode(true)
            appliedForwardDegrees = forward
        }
        trigger.dwellSeconds = settings.number(ShellSettings.BED_MODE_DWELL_SECONDS)
        trigger.countdownSeconds = settings.number(ShellSettings.BED_MODE_COUNTDOWN_SECONDS)
        when (val step = trigger.update(head.gravityOrientation, head.inBedMode, nowNs)) {
            BedModeStep.Waiting -> show(null)
            is BedModeStep.Counting -> {
                val change = if (step.entering) "enabled" else "disabled"
                show(Notice("Bed mode will be $change in ${step.secondsLeft}...", step.progress))
            }
            BedModeStep.Toggle -> {
                show(null)
                setBedMode(!head.inBedMode)
            }
        }
    }

    /** Clears only its own countdown, so it does not wipe another notice, such as the volume, every frame. */
    private fun show(notice: Notice?) {
        val target = compositor() ?: return
        if (notice == null) {
            if (shown != null && target.notice == shown) target.notice = null
        } else if (target.notice != notice) {
            target.notice = notice
        }
        shown = notice
    }
}
