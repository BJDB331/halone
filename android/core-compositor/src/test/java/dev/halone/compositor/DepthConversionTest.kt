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

class DepthPostTest {
    private val n = 64

    private fun scene(near: Float, far: Float) = FloatArray(n * n) { i ->
        val y = i / n
        val x = i % n
        if (x in 24 until 40 && y in 30 until 56) near else far
    }

    private fun at(map: ByteArray, x: Int, y: Int, size: Int = DepthPost.OUTPUT_SIZE) = (map[y * size + x].toInt() and 0xFF) / 255f

    @Test
    fun `nearer parts come out nearer, around the window's own distance`() {
        val map = DepthPost.normalise(scene(near = 3f, far = 0.5f), n)
        val s = DepthPost.OUTPUT_SIZE
        val box = at(map, s / 2, (s * 0.67f).toInt())
        val background = at(map, s / 10, s / 10)
        assertTrue("box $box should be nearer than background $background", box > background + 0.2f)
        assertTrue(background in 0.3f..0.6f)
    }

    @Test
    fun `a flat picture, like a page of text, gets no depth instead of stretched noise`() {
        val page = FloatArray(n * n) { 1f + ((it * 7919) % 13) * 0.002f }
        val map = DepthPost.normalise(page, n)
        map.forEach { assertEquals(0.5f, (it.toInt() and 0xFF) / 255f, 0.02f) }
    }

    @Test
    fun `successive frames are blended so depth does not flicker, but a scene cut replaces the last`() {
        val first = DepthPost.normalise(scene(3f, 0.5f), n)
        val s = DepthPost.OUTPUT_SIZE
        // A new shot: depth running across the whole picture the other way.
        val leftNear = FloatArray(n * n) { i -> 0.5f + 3f * (1f - (i % n) / (n - 1f)) }
        val rightNear = FloatArray(n * n) { i -> 0.5f + 3f * ((i % n) / (n - 1f)) }
        val before = DepthPost.normalise(leftNear, n)
        val cut = DepthPost.normalise(rightNear, n, previous = before)
        val fresh = DepthPost.normalise(rightNear, n)
        assertEquals(at(fresh, s / 10, s / 2), at(cut, s / 10, s / 2), 0.01f)

        val again = DepthPost.normalise(scene(3.2f, 0.5f), n, previous = first)
        assertEquals(at(first, s / 2, (s * 0.67f).toInt()), at(again, s / 2, (s * 0.67f).toInt()), 0.05f)
    }
}
