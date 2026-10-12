package dev.halone.glasses.fusion

import dev.halone.glasses.api.ClockSample
import dev.halone.glasses.api.ScanoutEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayClockTest {

    private var host = 1_000_000_000L
    private val clock = DisplayClock { host }

    /** The Air 2 Pro's own numbers: 60.0024 Hz reported, one beacon per 256. */
    private val reportedPeriod = 16_666_000L
    private val truePeriod = 16_666_672L

    private fun beacon(index: Long, deviceNs: Long) =
        ScanoutEvent(index, deviceNs, reportedPeriod, hostTimeNs = host)

    private fun feedBeacons(count: Int, from: Long = 100_000_000_000L) {
        for (i in 0 until count) {
            clock.onBeacon(beacon(i.toLong(), from + i * 256 * truePeriod))
        }
    }

    @Test
    fun `one beacon is not a period`() {
        feedBeacons(1)
        assertFalse(clock.hasTiming)
    }

    @Test
    fun `a long baseline recovers the period better than the device reports it`() {
        feedBeacons(10)
        assertTrue(clock.hasTiming)
        assertEquals(256, clock.refreshesPerBeacon)
        assertEquals(truePeriod.toDouble(), clock.periodNs, 1.0)
        assertEquals(reportedPeriod, clock.reportedPeriodNs)
    }

    @Test
    fun `a refresh rate change starts a new baseline instead of blending two`() {
        feedBeacons(10)
        val from = 100_000_000_000L + 10 * 256 * truePeriod + 5_000_000_000L
        val period90 = 11_111_111L
        for (i in 0 until 3) {
            clock.onBeacon(ScanoutEvent(10L + i, from + i * 256 * period90, period90, hostTimeNs = host))
        }
        assertEquals(period90.toDouble(), clock.periodNs, 1.0)
        assertEquals(period90, clock.reportedPeriodNs)
    }

    @Test
    fun `the grid projects forward from a beacon seconds old`() {
        feedBeacons(2)
        val last = 100_000_000_000L + 256 * truePeriod
        assertEquals((last + truePeriod).toDouble(), clock.refreshAfter(last + 1).toDouble(), 2.0)
        assertEquals(
            (last + 4 * truePeriod).toDouble(),
            clock.refreshAfter(last + 1, count = 4).toDouble(),
            2.0,
        )
    }

    @Test
    fun `a refresh boundary is its own next refresh`() {
        feedBeacons(2)
        val last = 100_000_000_000L + 256 * truePeriod
        assertEquals(last.toDouble(), clock.refreshAfter(last).toDouble(), 2.0)
    }

    /**
     * The phase is what a constant horizon cannot supply: asking at two moments
     * inside one refresh has to name the same photon.
     */
    @Test
    fun `two moments in the same interval aim at the same refresh`() {
        feedBeacons(2)
        val last = 100_000_000_000L + 256 * truePeriod
        val early = clock.refreshAfter(last + 1_000_000)
        val late = clock.refreshAfter(last + 15_000_000)
        assertEquals(early, late)
        assertTrue(late > last + 15_000_000)
    }

    @Test
    fun `the clock offset is what carries a host reading onto the device`() {
        feedBeacons(2)
        clock.onClockSample(ClockSample(offsetNs = -500_000_000_000L, roundTripNs = 2_000_000))
        assertTrue(clock.ready)
        assertEquals(host - 500_000_000_000L, clock.deviceNow())
    }

    @Test
    fun `a quieter exchange displaces a noisier one`() {
        clock.onClockSample(ClockSample(offsetNs = 100, roundTripNs = 8_000_000))
        clock.onClockSample(ClockSample(offsetNs = 200, roundTripNs = 1_000_000))
        clock.onClockSample(ClockSample(offsetNs = 300, roundTripNs = 5_000_000))
        assertEquals(200L, clock.offsetNs)
        assertEquals(3, clock.clockSamples)
    }

    @Test
    fun `an exchange that was queued behind something is discarded`() {
        clock.onClockSample(ClockSample(offsetNs = 100, roundTripNs = 500_000_000))
        assertFalse(clock.hasClock)
    }

    /** Two crystals drift, so the best sample cannot be kept for ever. */
    @Test
    fun `a stale best gives way to a fresh worse one`() {
        clock.onClockSample(ClockSample(offsetNs = 100, roundTripNs = 1_000_000))
        host += 60_000_000_000L
        clock.onClockSample(ClockSample(offsetNs = 999, roundTripNs = 9_000_000))
        assertEquals(999L, clock.offsetNs)
    }

    /** Unplugging the glasses restarts their clock; the old grid is worthless. */
    @Test
    fun `a device clock that restarts starts the fit again`() {
        feedBeacons(5)
        clock.onBeacon(beacon(0, 50_000_000L))
        assertEquals(1, clock.beacons)
        assertFalse(clock.hasTiming)
    }

    @Test
    fun `the horizon is measured from a given sample to a coming refresh`() {
        feedBeacons(2)
        clock.onClockSample(ClockSample(offsetNs = 0, roundTripNs = 1_000_000))
        host = 100_000_000_000L + 256 * truePeriod + 1_000_000
        val sampleAt = host - 2_000_000
        val horizon = clock.horizonMsTo(sampleAt, count = 6)
        // Five whole periods plus the 2 ms the sample is already behind by,
        // plus what is left of the interval the request landed in.
        assertEquals((5 * truePeriod + 2_000_000 + (truePeriod - 1_000_000)) / 1e6f, horizon, 0.01f)
    }
}

/**
 * The phase lock, which is what stands between a measured horizon and one that
 * alternates by a whole refresh. See [RefreshPhase].
 */
class RefreshPhaseTest {

    @Test
    fun `the first reading is taken as it stands`() {
        val phase = RefreshPhase()
        assertEquals(0.4f, phase.update(0.4f), 1e-6f)
    }

    @Test
    fun `a steady phase is reproduced`() {
        val phase = RefreshPhase()
        repeat(50) { phase.update(0.4f) }
        assertEquals(0.4f, phase.fraction, 1e-3f)
        assertEquals(0L, phase.crossings)
    }

    /**
     * The case that motivated this: a frame sitting on the boundary, where the
     * raw answer alternates between the two ends of the cycle.
     */
    @Test
    fun `flapping across the boundary does not move the phase`() {
        val phase = RefreshPhase()
        phase.update(0.99f)
        repeat(60) { phase.update(if (it % 2 == 0) 0.01f else 0.99f) }
        val distanceFromBoundary = minOf(phase.fraction, 1f - phase.fraction)
        assertTrue("phase ran to ${phase.fraction}", distanceFromBoundary < 0.1f)
    }

    /** Real drift is slow, and has to be followed rather than rejected. */
    @Test
    fun `a slow drift is tracked`() {
        val phase = RefreshPhase()
        phase.update(0.2f)
        var raw = 0.2f
        repeat(400) {
            raw += 0.001f
            phase.update(raw % 1f)
        }
        assertEquals(0.6f, phase.fraction, 0.05f)
    }

    /** Drifting past the end of the cycle is the frame landing one refresh later. */
    @Test
    fun `wrapping past the end counts as a crossing`() {
        val phase = RefreshPhase()
        phase.update(0.9f)
        var raw = 0.9f
        repeat(600) {
            raw = (raw + 0.001f) % 1f
            phase.update(raw)
        }
        assertEquals(1L, phase.crossings)
        assertEquals(0.5f, phase.fraction, 0.05f)
    }

    @Test
    fun `a reset forgets the lock`() {
        val phase = RefreshPhase()
        phase.update(0.4f)
        phase.reset()
        assertTrue(phase.fraction.isNaN())
        assertEquals(0L, phase.crossings)
    }
}
