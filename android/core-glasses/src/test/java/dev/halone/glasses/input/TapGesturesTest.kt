package dev.halone.glasses.input

import dev.halone.glasses.input.TapGestures.Companion.DOUBLE_WINDOW_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TapGesturesTest {
    private val events = mutableListOf<String>()
    private val doubles = mutableSetOf<TapSide>()
    private val gestures = TapGestures({ it in doubles }, object : TapGestures.Listener {
        override fun onSingle(side: TapSide, at: Long) { events += "single-$side@$at" }
        override fun onDouble(side: TapSide, at: Long) { events += "double-$side@$at" }
    })

    @Test
    fun `without a double action a tap is a single at once`() {
        gestures.tap(TapSide.LEFT, 0)
        gestures.tap(TapSide.LEFT, 200)

        assertEquals(listOf("single-LEFT@0", "single-LEFT@200"), events)
        assertNull(gestures.nextDeadline())
    }

    @Test
    fun `with a double action two quick taps are one double`() {
        doubles += TapSide.LEFT
        gestures.tap(TapSide.LEFT, 0)
        gestures.tap(TapSide.LEFT, 220)
        gestures.tick(1_000)

        assertEquals(listOf("double-LEFT@0"), events)
    }

    @Test
    fun `with a double action a lone tap is a single once the window has passed`() {
        doubles += TapSide.RIGHT
        gestures.tap(TapSide.RIGHT, 0)
        gestures.tick(DOUBLE_WINDOW_MS)
        assertEquals(emptyList<String>(), events)

        gestures.tick(gestures.nextDeadline()!!)
        assertEquals(listOf("single-RIGHT@0"), events)
    }

    @Test
    fun `taps too far apart are two singles`() {
        doubles += TapSide.LEFT
        gestures.tap(TapSide.LEFT, 0)
        gestures.tap(TapSide.LEFT, DOUBLE_WINDOW_MS + 50)
        gestures.tick(2_000)

        assertEquals(listOf("single-LEFT@0", "single-LEFT@${DOUBLE_WINDOW_MS + 50}"), events)
    }

    @Test
    fun `a tap on the other side settles the waiting one as a single`() {
        doubles += TapSide.LEFT
        gestures.tap(TapSide.LEFT, 0)
        gestures.tap(TapSide.RIGHT, 100)

        assertEquals(listOf("single-LEFT@0", "single-RIGHT@100"), events)
    }
}
