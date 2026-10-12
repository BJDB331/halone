package dev.halone.compositor

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImmersiveGeometryTest {
    @Test
    fun `full and half stereo frames keep the eye's own shape`() {
        assertEquals(16f / 9f, ImmersiveGeometry.eyeAspect(3840, 1080, StereoLayout.SIDE_BY_SIDE), 1e-4f)
        assertEquals(16f / 9f, ImmersiveGeometry.eyeAspect(1920, 1080, StereoLayout.SIDE_BY_SIDE), 1e-4f)
        assertEquals(16f / 9f, ImmersiveGeometry.eyeAspect(1920, 2160, StereoLayout.TOP_BOTTOM), 1e-4f)
        assertEquals(16f / 9f, ImmersiveGeometry.eyeAspect(1920, 1080, StereoLayout.TOP_BOTTOM), 1e-4f)
        assertEquals(16f / 9f, ImmersiveGeometry.eyeAspect(0, 0, StereoLayout.MONO), 1e-4f)
    }

    @Test
    fun `the left eye takes the left or top half`() {
        assertArrayEquals(floatArrayOf(0f, 0f, 0.5f, 1f), ImmersiveGeometry.eyeRect(StereoLayout.SIDE_BY_SIDE, 0), 0f)
        assertArrayEquals(floatArrayOf(0.5f, 0f, 0.5f, 1f), ImmersiveGeometry.eyeRect(StereoLayout.SIDE_BY_SIDE, 1), 0f)
        assertArrayEquals(floatArrayOf(0f, 0f, 1f, 0.5f), ImmersiveGeometry.eyeRect(StereoLayout.TOP_BOTTOM, 0), 0f)
        assertArrayEquals(floatArrayOf(0f, 0.5f, 1f, 0.5f), ImmersiveGeometry.eyeRect(StereoLayout.TOP_BOTTOM, 1), 0f)
        assertArrayEquals(floatArrayOf(0f, 0f, 1f, 1f), ImmersiveGeometry.eyeRect(StereoLayout.MONO, 1), 0f)
    }

    @Test
    fun `forward is the middle of the frame and right is a quarter on`() {
        assertArrayEquals(floatArrayOf(0.5f, 0.5f), ImmersiveGeometry.equirect(0f, 0f, -1f, halfSphere = false), 1e-5f)
        assertArrayEquals(floatArrayOf(0.75f, 0.5f), ImmersiveGeometry.equirect(1f, 0f, 0f, halfSphere = false), 1e-5f)
        assertArrayEquals(floatArrayOf(0.5f, 0.25f), ImmersiveGeometry.equirect(0f, 1f, -1f, halfSphere = false), 1e-5f)
        assertArrayEquals(floatArrayOf(1f, 0.5f), ImmersiveGeometry.equirect(1f, 0f, 0f, halfSphere = true), 1e-5f)
    }

    @Test
    fun `zooming out doubles the angle from the gaze and leaves the gaze alone`() {
        val forward = floatArrayOf(0f, 0f, -1f)
        val half = Math.sqrt(0.5).toFloat()
        assertArrayEquals(forward, ImmersiveGeometry.widen(forward, forward, 0.5f), 1e-6f)
        assertArrayEquals(floatArrayOf(1f, 0f, 0f), ImmersiveGeometry.widen(floatArrayOf(half, 0f, -half), forward, 0.5f), 1e-5f)
        assertArrayEquals(floatArrayOf(0f, half, -half), ImmersiveGeometry.widen(floatArrayOf(0f, half, -half), forward, 1f), 1e-6f)
        assertArrayEquals(floatArrayOf(0f, 0f, 1f), ImmersiveGeometry.widen(floatArrayOf(0f, 1f, 0f), forward, 0.4f), 1e-5f)
    }

    @Test
    fun `each eye sees a near sphere from its own side`() {
        val forward = floatArrayOf(0f, 0f, -1f)
        val left = ImmersiveGeometry.sphereHit(floatArrayOf(-0.032f, 0f, 0f), forward, 6f)
        val right = ImmersiveGeometry.sphereHit(floatArrayOf(0.032f, 0f, 0f), forward, 6f)
        assertEquals(-0.032f / 6f, left[0], 1e-4f)
        assertEquals(0.032f / 6f, right[0], 1e-4f)
        assertArrayEquals(forward, ImmersiveGeometry.sphereHit(floatArrayOf(0f, 0f, 0f), forward, 6f), 1e-6f)
    }

    @Test
    fun `nothing is drawn behind a half sphere`() {
        assertNull(ImmersiveGeometry.equirect(0f, 0f, 1f, halfSphere = true))
    }
}
