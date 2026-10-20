package dev.halone.compositor

import dev.halone.compositor.gl.DepthMaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DepthConversionTest {

    @Test
    fun `the framing guess averages the window's own distance, so turning it on does not move the window`() {
        val n = 200
        var sum = 0.0
        for (row in 0 until n) for (column in 0 until n) {
            sum += DepthMaps.framingDepth((column + 0.5f) / n, (row + 0.5f) / n)
        }
        assertEquals(0.5, sum / (n * n), 0.01)
    }

    @Test
    fun `the middle is nearer than the edges, and the bottom nearer than the top`() {
        val middle = DepthMaps.framingDepth(0.5f, 0.5f)
        assertTrue(middle > DepthMaps.framingDepth(0.05f, 0.5f))
        assertTrue(DepthMaps.framingDepth(0.5f, 0.95f) > DepthMaps.framingDepth(0.5f, 0.05f))
        for (u in listOf(0f, 0.5f, 1f)) for (v in listOf(0f, 0.5f, 1f)) {
            assertTrue(DepthMaps.framingDepth(u, v) in 0f..1f)
        }
    }

    @Test
    fun `the 3D button sits left of the shape button, or of pin without it, and only hits when shown`() {
        val w = 1280
        val h = 720
        val pin = TitleBar.pin(w, h)
        val size = TitleBar.size(w, h)
        assertEquals(size.left - TitleBar.BUTTON_GAP_PX - TitleBar.BUTTON_PX, TitleBar.depth(w, h, shape = true).left, 1e-3f)
        assertEquals(pin.left - TitleBar.BUTTON_GAP_PX - TitleBar.BUTTON_PX, TitleBar.depth(w, h, shape = false).left, 1e-3f)

        val button = TitleBar.depth(w, h, shape = false)
        val x = button.left + button.width / 2f
        val y = button.top + button.height / 2f
        assertEquals(HitPart.DEPTH, TitleBar.partAt(w, h, x, y, depth = true))
        assertEquals(HitPart.TITLE_BAR, TitleBar.partAt(w, h, x, y, depth = false))
    }
}
