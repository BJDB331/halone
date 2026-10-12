package dev.halone.app.pointer

import dev.halone.app.pointer.PadGestures.Companion.holdMs
import org.junit.Assert.assertEquals
import org.junit.Test

class PadGesturesTest {
    private val events = mutableListOf<String>()
    private val gestures = PadGestures(object : PadGestures.Listener {
        override fun onTap(fingers: Int) { events += "tap$fingers" }
        override fun onHoldStart(fingers: Int) { events += "hold$fingers" }
        override fun onHoldEnd(fingers: Int) { events += "release$fingers" }
        override fun onScrollStart() { events += "scroll" }
        override fun onScrollMove(dxDp: Float, dyDp: Float) { events += "move %.0f,%.0f".format(dxDp, dyDp) }
        override fun onScrollEnd() { events += "scrolled" }
    })

    @Test
    fun `a short still touch is a one-finger tap`() {
        gestures.down(0, 100f, 100f)
        gestures.move(105f, 103f)
        gestures.up(150)

        assertEquals(listOf("tap1"), events)
    }

    @Test
    fun `travel past the deadzone scrolls by the finger's movement`() {
        gestures.down(0, 100f, 100f)
        gestures.move(100f, 120f)
        gestures.move(100f, 150f)
        gestures.up(300)

        assertEquals(listOf("scroll", "move 0,20", "move 0,30", "scrolled"), events)
    }

    @Test
    fun `a scroll is never also a tap or a hold`() {
        gestures.down(0, 0f, 0f)
        gestures.move(30f, 0f)
        gestures.tick(holdMs(1) + 10)
        gestures.up(holdMs(1) + 20)

        assertEquals(listOf("scroll", "move 30,0", "scrolled"), events)
    }

    @Test
    fun `holding one finger still holds until it lifts, whatever it does meanwhile`() {
        gestures.down(0, 0f, 0f)
        assertEquals(holdMs(1), gestures.nextDeadline())
        gestures.tick(holdMs(1))
        gestures.move(40f, 0f)
        gestures.up(2_000)

        assertEquals(listOf("hold1", "release1"), events)
    }

    @Test
    fun `a slow lift without a tick is neither tap nor hold`() {
        gestures.down(0, 0f, 0f)
        gestures.up(holdMs(1) + 5)

        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `two fingers held hold, and lifting either releases`() {
        gestures.down(0, 0f, 0f)
        gestures.fingerAdded(20, 2)
        gestures.tick(20 + holdMs(2))
        gestures.fingerLifted()
        gestures.up(900)

        assertEquals(listOf("hold2", "release2"), events)
    }

    @Test
    fun `two fingers lifted early are a two-finger tap`() {
        gestures.down(0, 0f, 0f)
        gestures.fingerAdded(10, 2)
        gestures.tick(150)
        gestures.fingerLifted()
        gestures.up(180)

        assertEquals(listOf("tap2"), events)
    }

    @Test
    fun `fingers arriving one at a time make one three-finger gesture`() {
        gestures.down(0, 0f, 0f)
        gestures.fingerAdded(15, 2)
        gestures.fingerAdded(40, 3)
        gestures.tick(15 + holdMs(2))
        gestures.fingerLifted()
        gestures.fingerLifted()
        gestures.up(420)

        assertEquals(listOf("tap3"), events)
    }

    @Test
    fun `three fingers held hold once, without lifting`() {
        gestures.down(0, 0f, 0f)
        gestures.fingerAdded(10, 2)
        gestures.fingerAdded(30, 3)
        assertEquals(30 + holdMs(3), gestures.nextDeadline())
        gestures.tick(30 + holdMs(3))
        assertEquals(listOf("hold3"), events)

        gestures.fingerLifted()
        gestures.fingerLifted()
        gestures.up(2_000)
        assertEquals(listOf("hold3", "release3"), events)
    }

    @Test
    fun `lifting all fingers at once is still a tap of that many`() {
        gestures.down(0, 0f, 0f)
        gestures.fingerAdded(10, 2)
        gestures.fingerAdded(20, 3)
        gestures.up(150)

        assertEquals(listOf("tap3"), events)
    }

    @Test
    fun `a finger joining a hold does not change it`() {
        gestures.down(0, 0f, 0f)
        gestures.fingerAdded(10, 2)
        gestures.tick(10 + holdMs(2))
        gestures.fingerAdded(400, 3)
        gestures.tick(400 + holdMs(3))
        gestures.up(1_200)

        assertEquals(listOf("hold2", "release2"), events)
    }

    @Test
    fun `swiping only scrolls with one finger`() {
        gestures.down(0, 0f, 0f)
        gestures.fingerAdded(10, 2)
        gestures.move(0f, 60f)
        gestures.fingerLifted()
        gestures.up(200)

        assertEquals(listOf("tap2"), events)
    }

    @Test
    fun `cancelling ends whatever was under way`() {
        gestures.down(0, 0f, 0f)
        gestures.move(0f, 40f)
        gestures.cancel()

        assertEquals(listOf("scroll", "move 0,40", "scrolled"), events)
    }

    @Test
    fun `a hold whose pointer stays put opens the menu once it has been still long enough`() {
        assertEquals(PadGestures.HoldIntent.UNDECIDED, PadGestures.holdIntent(200, 0.5f))
        assertEquals(PadGestures.HoldIntent.MENU, PadGestures.holdIntent(PadGestures.STILL_MS, 0.5f))
    }

    @Test
    fun `a hold whose pointer moves is a drag, however soon`() {
        assertEquals(PadGestures.HoldIntent.DRAG, PadGestures.holdIntent(40, 1.5f))
        assertEquals(PadGestures.HoldIntent.DRAG, PadGestures.holdIntent(2_000, 1.5f))
    }

    @Test
    fun `letting go of a still hold before it is decided is a long press too`() {
        assertEquals(PadGestures.HoldIntent.MENU, PadGestures.holdIntent(100, 0.2f, released = true))
    }
}
