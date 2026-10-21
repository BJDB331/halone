package dev.halone.compositor

import dev.halone.compositor.gl.DepthMaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import kotlin.math.abs
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

class DepthStabiliserTest {
    private val n = 64
    private val s = DepthStabiliser.OUTPUT_SIZE

    /** A near box low in the middle, in front of a far background; its picture is a bright box on grey. */
    private fun depth(near: Float = 3f, far: Float = 0.5f, boxX: Int = 24) = FloatArray(n * n) { i ->
        if (i % n in boxX until boxX + 16 && i / n in 30 until 56) near else far
    }

    private fun picture(boxX: Int = 24, background: Float = 0.4f) = FloatArray(n * n) { i ->
        if (i % n in boxX until boxX + 16 && i / n in 30 until 56) 0.9f else background
    }

    private fun at(map: DepthMap, x: Int, y: Int) = (map.texels[y * map.size + x].toInt() and 0xFF) / 255f
    private val boxX = s / 2
    private val boxY = (s * 0.67f).toInt()

    @Test
    fun `nearer parts come out nearer`() {
        val map = DepthStabiliser().next(depth(), n, picture(), n)
        assertTrue(at(map, boxX, boxY) > at(map, s / 10, s / 10) + 0.4f)
    }

    @Test
    fun `the guided filter keeps an edge the picture also has, where a plain blur smears it`() {
        val size = 32
        val step = FloatArray(size * size) { if (it % size >= 16) 1f else 0f }
        val noisy = FloatArray(size * size) { step[it] * 0.8f + 0.1f + ((it * 7919) % 7) * 0.01f }
        val guided = DepthPost.guidedFilter(step, noisy, size, 2, 0.0025f)
        val blurred = DepthPost.boxMean(noisy, size, 2)
        val justOutside = 10 * size + 15
        val justInside = 10 * size + 16
        assertTrue(guided[justInside] - guided[justOutside] > 2f * (blurred[justInside] - blurred[justOutside]))
    }

    @Test
    fun `a flat picture, like a page of text, keeps the gentle framing shape instead of stretched noise`() {
        val page = FloatArray(n * n) { 1f + ((it * 7919) % 13) * 0.002f }
        val map = DepthStabiliser().next(page, n, FloatArray(n * n) { 0.95f }, n)
        for ((x, y) in listOf(s / 2 to s / 2, s / 4 to s / 3, 3 * s / 4 to 2 * s / 3)) {
            assertEquals(DepthPost.framingDepth((x + 0.5f) / s, (y + 0.5f) / s), at(map, x, y), 0.03f)
        }
    }

    @Test
    fun `history holds still parts steady`() {
        val stabiliser = DepthStabiliser(smoothing = 0.6f)
        val first = stabiliser.next(depth(near = 3f), n, picture(), n)
        // Same picture, model output wobbling: the map moves only part way.
        val second = stabiliser.next(depth(near = 3f, far = 1.2f), n, picture(), n)
        val fresh = DepthStabiliser(smoothing = 0f).next(depth(near = 3f, far = 1.2f), n, picture(), n)
        val corner = at(second, s / 10, s / 10)
        assertTrue(corner in minOf(at(first, s / 10, s / 10), at(fresh, s / 10, s / 10))..maxOf(at(first, s / 10, s / 10), at(fresh, s / 10, s / 10)))
        assertTrue(abs(corner - at(fresh, s / 10, s / 10)) > 0.01f || abs(at(first, s / 10, s / 10) - at(fresh, s / 10, s / 10)) < 0.01f)
    }

    @Test
    fun `where the picture changed, history is dropped, so moving things leave no trail`() {
        val stabiliser = DepthStabiliser(smoothing = 0.9f)
        stabiliser.next(depth(boxX = 8), n, picture(boxX = 8), n)
        val moved = stabiliser.next(depth(boxX = 40), n, picture(boxX = 40), n)
        val fresh = DepthStabiliser(smoothing = 0f).next(depth(boxX = 40), n, picture(boxX = 40), n)
        // Where the box was: the picture changed there, so it follows the new frame.
        val wasBox = (15f * s / n).toInt()
        assertEquals(at(fresh, wasBox, boxY), at(moved, wasBox, boxY), 0.05f)
        assertFalse(stabiliser.lastCut)
    }

    @Test
    fun `a cut drops all history`() {
        val stabiliser = DepthStabiliser(smoothing = 0.9f)
        stabiliser.next(depth(), n, picture(background = 0.1f), n)
        val leftNear = FloatArray(n * n) { i -> 0.5f + 3f * (1f - (i % n) / (n - 1f)) }
        val cut = stabiliser.next(leftNear, n, FloatArray(n * n) { 0.9f }, n)
        assertTrue(stabiliser.lastCut)
        val fresh = DepthStabiliser(smoothing = 0f).next(leftNear, n, FloatArray(n * n) { 0.9f }, n)
        assertEquals(at(fresh, s / 10, s / 2), at(cut, s / 10, s / 2), 0.01f)
    }
}
