package dev.halone.shell

import dev.halone.glasses.math.Quaternion
import org.junit.Assert.assertEquals
import org.junit.Test

class BedModeTriggerTest {
    private val trigger = BedModeTrigger(dwellSeconds = 5.0, countdownSeconds = 3.0)
    private val level = Quaternion.IDENTITY
    private val ceiling = Quaternion.fromPitchDegrees(80f)
    private fun seconds(s: Double) = (s * 1e9).toLong()

    private fun lookingUp(at: Double, inBed: Boolean = false) = trigger.update(ceiling, inBed, seconds(at))

    @Test
    fun `looking up waits out the dwell before counting down`() {
        assertEquals(BedModeStep.Waiting, lookingUp(0.0))
        assertEquals(BedModeStep.Waiting, lookingUp(4.9))
        assertEquals(BedModeStep.Counting(entering = true, secondsLeft = 3, progress = 0f), lookingUp(5.0))
        val counting = lookingUp(6.5) as BedModeStep.Counting
        assertEquals(2, counting.secondsLeft)
        assertEquals(0.5f, counting.progress, 1e-4f)
        assertEquals(1, (lookingUp(7.9) as BedModeStep.Counting).secondsLeft)
        assertEquals(BedModeStep.Toggle, lookingUp(8.0))
    }

    @Test
    fun `looking back down cancels the countdown`() {
        lookingUp(0.0)
        lookingUp(6.0)
        assertEquals(BedModeStep.Waiting, trigger.update(level, false, seconds(6.1)))
        assertEquals(BedModeStep.Waiting, lookingUp(6.2))
        assertEquals(BedModeStep.Waiting, lookingUp(11.1))
    }

    @Test
    fun `a gaze that sags a little does not cancel`() {
        lookingUp(0.0)
        assertEquals(BedModeStep.Waiting, trigger.update(Quaternion.fromPitchDegrees(55f), false, seconds(4.0)))
        assertEquals(BedModeStep.Toggle, lookingUp(8.0))
    }

    @Test
    fun `a moderate upward glance never starts it`() {
        repeat(20) { assertEquals(BedModeStep.Waiting, trigger.update(Quaternion.fromPitchDegrees(55f), false, seconds(it.toDouble()))) }
    }

    @Test
    fun `in bed mode sitting upright counts down to leave`() {
        assertEquals(BedModeStep.Waiting, lookingUp(0.0, inBed = true))
        assertEquals(BedModeStep.Waiting, trigger.update(level, true, seconds(1.0)))
        val counting = trigger.update(level, true, seconds(6.0)) as BedModeStep.Counting
        assertEquals(false, counting.entering)
        assertEquals(BedModeStep.Toggle, trigger.update(level, true, seconds(9.0)))
    }

    @Test
    fun `the timings follow the settings`() {
        trigger.dwellSeconds = 1.0
        trigger.countdownSeconds = 1.0
        lookingUp(0.0)
        assertEquals(1, (lookingUp(1.5) as BedModeStep.Counting).secondsLeft)
        assertEquals(BedModeStep.Toggle, lookingUp(2.0))
    }
}
