package dev.halone.glasses.api

import dev.halone.glasses.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Test

class AxisConventionTest {
    @Test
    fun `every axis and sign preserves its component including signed zero`() {
        val values = listOf(
            Vec3(2f, -3f, 5f),
            Vec3(-0f, 0f, Float.NEGATIVE_INFINITY),
            Vec3(Float.NaN, Float.POSITIVE_INFINITY, Float.MIN_VALUE),
        )
        for (axis in AxisConvention.Axis.entries) {
            val convention = AxisConvention(axis, axis, axis)
            for (value in values) {
                val expected = when (axis) {
                    AxisConvention.Axis.X_POS -> value.x
                    AxisConvention.Axis.X_NEG -> -value.x
                    AxisConvention.Axis.Y_POS -> value.y
                    AxisConvention.Axis.Y_NEG -> -value.y
                    AxisConvention.Axis.Z_POS -> value.z
                    AxisConvention.Axis.Z_NEG -> -value.z
                }.toBits()
                val remapped = convention.apply(value)
                assertEquals(expected, remapped.x.toBits())
                assertEquals(expected, remapped.y.toBits())
                assertEquals(expected, remapped.z.toBits())
            }
        }
    }
}
