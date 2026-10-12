package dev.halone.engine.webview

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DomKeysTest {
    @Test
    fun `keys carry the code and legacy keyCode a browser gives them`() {
        assertEquals(DomKeys.Key("Backspace", 8), DomKeys.of(KeyEvent.KEYCODE_DEL))
        assertEquals(DomKeys.Key("ArrowUp", 38), DomKeys.of(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(DomKeys.Key("KeyC", 67), DomKeys.of(KeyEvent.KEYCODE_C))
        assertEquals(DomKeys.Key("Digit7", 55), DomKeys.of(KeyEvent.KEYCODE_7))
        assertEquals(DomKeys.Key("Numpad3", 99), DomKeys.of(KeyEvent.KEYCODE_NUMPAD_3))
        assertEquals(DomKeys.Key("F12", 123), DomKeys.of(KeyEvent.KEYCODE_F12))
        assertEquals(DomKeys.Key("Quote", 222), DomKeys.of(KeyEvent.KEYCODE_APOSTROPHE))
        assertNull(DomKeys.of(KeyEvent.KEYCODE_UNKNOWN))
    }
}
