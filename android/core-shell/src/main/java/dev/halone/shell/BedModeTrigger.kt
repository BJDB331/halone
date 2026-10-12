package dev.halone.shell

import dev.halone.glasses.math.Quaternion
import dev.halone.glasses.math.Vec3
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

sealed interface BedModeStep {
    data object Waiting : BedModeStep

    /** [secondsLeft] counts down to one; [progress] runs from zero to one. */
    data class Counting(val entering: Boolean, val secondsLeft: Int, val progress: Float) : BedModeStep

    data object Toggle : BedModeStep
}

/**
 * Decides when to enter or leave bed mode from the head's orientation against
 * gravity: looking steeply up enters it, sitting upright leaves it. Each holds
 * for [dwellSeconds] before a visible countdown of [countdownSeconds], and
 * either ends the moment the posture does.
 */
class BedModeTrigger(
    var dwellSeconds: Double = 5.0,
    var countdownSeconds: Double = 3.0,
) {
    private var sinceNs: Long? = null

    fun update(gravityOrientation: Quaternion, inBedMode: Boolean, nowNs: Long): BedModeStep {
        val holding = sinceNs != null
        val posture = if (inBedMode) {
            gravityOrientation.rotate(UP).y >= if (holding) UPRIGHT_HOLD else UPRIGHT_START
        } else {
            gravityOrientation.rotate(FORWARD).y >= if (holding) LOOKING_UP_HOLD else LOOKING_UP_START
        }
        if (!posture) {
            sinceNs = null
            return BedModeStep.Waiting
        }
        val start = sinceNs ?: nowNs.also { sinceNs = it }
        val counted = (nowNs - start) / 1e9 - dwellSeconds
        if (counted < 0) return BedModeStep.Waiting
        if (counted >= countdownSeconds) {
            sinceNs = null
            return BedModeStep.Toggle
        }
        return BedModeStep.Counting(
            entering = !inBedMode,
            secondsLeft = ceil(countdownSeconds - counted).toInt().coerceAtLeast(1),
            progress = (counted / countdownSeconds).toFloat(),
        )
    }

    fun reset() {
        sinceNs = null
    }

    private companion object {
        val FORWARD = Vec3(0f, 0f, -1f)
        val UP = Vec3(0f, 1f, 0f)

        /** Gaze elevation: 60° to start, and it may sag to 50° without cancelling. */
        val LOOKING_UP_START = sin(Math.toRadians(60.0)).toFloat()
        val LOOKING_UP_HOLD = sin(Math.toRadians(50.0)).toFloat()

        /** Head tilt from vertical: within 30° to start, 40° to hold. */
        val UPRIGHT_START = cos(Math.toRadians(30.0)).toFloat()
        val UPRIGHT_HOLD = cos(Math.toRadians(40.0)).toFloat()
    }
}
