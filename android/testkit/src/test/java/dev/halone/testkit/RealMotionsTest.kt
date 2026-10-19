package dev.halone.testkit

import dev.halone.glasses.api.AxisConvention
import dev.halone.glasses.api.AxisConvention.Axis
import dev.halone.testkit.analysis.AxisSolver
import dev.halone.testkit.capture.MotionMeter
import dev.halone.testkit.capture.SegmentRecorder
import dev.halone.testkit.profiles.XrealOneProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

/** The solver and the live checks against a real One Pro wearer's movements (see `fixtures/xrealone/README.md`). */
class RealMotionsTest {

    private class Sample(val t: Long, val g: FloatArray, val a: FloatArray)

    private val segments: Map<String, List<Sample>> by lazy {
        val file = File("../../fixtures/xrealone/one-pro-motions.csv.gz")
        GZIPInputStream(file.inputStream()).bufferedReader().useLines { lines ->
            lines.drop(1).map { it.split(',') }.groupBy({ it[0] }) { c ->
                Sample(c[1].toLong(), floatArrayOf(c[2].toFloat(), c[3].toFloat(), c[4].toFloat()), floatArrayOf(c[5].toFloat(), c[6].toFloat(), c[7].toFloat()))
            }
        }
    }

    private fun recorded(label: String): SegmentRecorder.Samples {
        val recorder = SegmentRecorder()
        recorder.begin(label)
        recorder.go()
        segments.getValue(label).forEach { recorder.onImu(it.t, it.g[0], it.g[1], it.g[2], it.a[0], it.a[1], it.a[2]) }
        recorder.end()
        return recorder.samples(label)!!
    }

    @Test
    fun `the solver is confident on a real session and finds the known frame`() {
        val bias = AxisSolver.meanGyro(recorded("rest"))
        val swings = AxisSolver.EXPECTED.keys.associateWith { AxisSolver.swing(recorded(it), bias) }
        val result = AxisSolver.solve(swings, AxisSolver.meanAccel(recorded("level")))

        assertEquals(result.problems.toString(), AxisSolver.Status.CONFIDENT, result.status)
        assertEquals(AxisConvention(Axis.X_POS, Axis.Y_NEG, Axis.Z_NEG), result.convention)
        assertTrue("mount ${result.mountAngleDegrees}", result.mountAngleDegrees!! in 25.0..36.0)
        assertTrue("gravity ${result.gravityFromUpDegrees}", result.gravityFromUpDegrees!! < 15.0)
    }

    @Test
    fun `every real movement passes the live check it was recorded under`() {
        for (motion in XrealOneProfile.MOTIONS.filter { it.label in segments }) {
            val meter = MotionMeter()
            segments.getValue(motion.label).forEach { meter.onGyro(it.t, it.g[0], it.g[1], it.g[2]) }
            val reading = meter.read()
            val passes = when (val check = motion.check) {
                is XrealOneProfile.Check.Still -> reading.peakDegreesPerSec <= check.maxDegreesPerSec && reading.turnedDegrees <= check.maxTurnedDegrees
                is XrealOneProfile.Check.Turn -> reading.turnedDegrees >= check.minDegrees
                XrealOneProfile.Check.None -> true
            }
            assertTrue("${motion.label}: turned %.1f°, peak %.1f°/s".format(reading.turnedDegrees, reading.peakDegreesPerSec), passes)
        }
    }
}
