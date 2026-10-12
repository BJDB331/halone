package dev.halone.app.pointer

import dev.halone.app.pointer.TouchpadGestures.Companion.DOUBLE_TAP_MS
import dev.halone.app.pointer.TouchpadGestures.Companion.HOLD_MS
import org.junit.Assert.assertEquals
import org.junit.Test

class TouchpadGesturesTest {
    private val events = mutableListOf<String>()
    private val pad = TouchpadGestures(object : TouchpadGestures.Listener {
        override fun onMove(dxDp: Float, dyDp: Float) { events += "move %.0f,%.0f".format(dxDp, dyDp) }
        override fun onScroll(dxDp: Float, dyDp: Float) { events += "scroll %.0f,%.0f".format(dxDp, dyDp) }
        override fun onScrollEnd() { events += "scrolled" }
        override fun onTap(fingers: Int) { events += "tap$fingers" }
        override fun onHoldStart(fingers: Int) { events += "hold$fingers" }
        override fun onHoldEnd(fingers: Int) { events += "release$fingers" }
        override fun onDragStart() { events += "drag" }
        override fun onDragEnd() { events += "drop" }
    })

    @Test
    fun `one finger moves the cursor from the first sample`() {
        pad.down(0, 100f, 100f)
        pad.move(1, 103f, 100f)
        pad.move(1, 110f, 95f)
        pad.up(400)

        assertEquals(listOf("move 3,0", "move 7,-5"), events)
    }

    @Test
    fun `a quick still touch is a tap`() {
        pad.down(0, 0f, 0f)
        pad.move(1, 2f, 1f)
        pad.up(120)

        assertEquals(listOf("move 2,1", "tap1"), events)
    }

    @Test
    fun `a move is not a tap however quick`() {
        pad.down(0, 0f, 0f)
        pad.move(1, 30f, 0f)
        pad.up(100)

        assertEquals(listOf("move 30,0"), events)
    }

    @Test
    fun `two fingers scroll by their centroid, without a jump when the second lands`() {
        pad.down(0, 100f, 100f)
        pad.fingersChanged(10, 2, 150f, 100f)
        pad.move(2, 150f, 130f)
        pad.move(2, 150f, 170f)
        pad.fingersChanged(300, 1, 100f, 170f)
        pad.up(320)

        assertEquals(listOf("scroll 0,30", "scroll 0,40", "scrolled"), events)
    }

    @Test
    fun `two fingers that barely move are a tap, never a scroll`() {
        pad.down(0, 100f, 100f)
        pad.fingersChanged(10, 2, 150f, 100f)
        pad.move(2, 152f, 103f)
        pad.move(2, 151f, 104f)
        pad.up(150)

        assertEquals(listOf("tap2"), events)
    }

    @Test
    fun `a two-finger scroll keeps the movement made before it started`() {
        pad.down(0, 100f, 100f)
        pad.fingersChanged(10, 2, 150f, 100f)
        pad.move(2, 150f, 106f)
        pad.move(2, 150f, 112f)
        pad.move(2, 150f, 120f)
        pad.up(400)

        assertEquals(listOf("scroll 0,12", "scroll 0,8", "scrolled"), events)
    }

    @Test
    fun `a double tap held down drags until it lifts`() {
        pad.down(0, 0f, 0f)
        pad.up(100)
        pad.down(100 + DOUBLE_TAP_MS - 50, 0f, 0f)
        pad.move(1, 40f, 0f)
        pad.up(900)

        assertEquals(listOf("tap1", "drag", "move 40,0", "drop"), events)
    }

    @Test
    fun `a slow second touch is not a drag`() {
        pad.down(0, 0f, 0f)
        pad.up(100)
        pad.down(100 + DOUBLE_TAP_MS + 50, 0f, 0f)
        pad.move(1, 40f, 0f)
        pad.up(900)

        assertEquals(listOf("tap1", "move 40,0"), events)
    }

    @Test
    fun `a two-finger tap does not arm a drag`() {
        pad.down(0, 0f, 0f)
        pad.fingersChanged(10, 2, 0f, 0f)
        pad.up(120)
        pad.down(200, 0f, 0f)
        pad.up(300)

        assertEquals(listOf("tap2", "tap1"), events)
    }

    @Test
    fun `three fingers kept still hold, and moving during a hold moves the cursor`() {
        pad.down(0, 0f, 0f)
        pad.fingersChanged(10, 2, 0f, 0f)
        pad.fingersChanged(20, 3, 0f, 0f)
        assertEquals(20 + HOLD_MS, pad.nextDeadline())
        pad.tick(20 + HOLD_MS)
        pad.move(3, 0f, 25f)
        pad.up(2_000)

        assertEquals(listOf("hold3", "move 0,25", "release3"), events)
    }

    @Test
    fun `a consumed touch is neither tap nor hold, but still points`() {
        pad.down(0, 0f, 0f)
        pad.consume()
        pad.move(1, 5f, 0f)
        pad.tick(HOLD_MS + 10)
        pad.up(120)

        assertEquals(listOf("move 5,0"), events)
    }

    @Test
    fun `fingers that have moved never hold`() {
        pad.down(0, 0f, 0f)
        pad.move(1, 40f, 0f)
        pad.tick(HOLD_MS + 100)
        pad.up(HOLD_MS + 200)

        assertEquals(listOf("move 40,0"), events)
    }
}
