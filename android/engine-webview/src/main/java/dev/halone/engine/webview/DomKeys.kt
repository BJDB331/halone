package dev.halone.engine.webview

import android.view.KeyEvent

/**
 * Android key codes as a page's keyboard events name them: `code` for the
 * key's place, and the legacy `keyCode` that terminals and older libraries
 * still read for Backspace, the arrows and Ctrl combinations.
 */
object DomKeys {
    data class Key(val code: String, val keyCode: Int)

    private val NAMED = mapOf(
        KeyEvent.KEYCODE_DEL to Key("Backspace", 8),
        KeyEvent.KEYCODE_TAB to Key("Tab", 9),
        KeyEvent.KEYCODE_ENTER to Key("Enter", 13),
        KeyEvent.KEYCODE_NUMPAD_ENTER to Key("NumpadEnter", 13),
        KeyEvent.KEYCODE_SHIFT_LEFT to Key("ShiftLeft", 16),
        KeyEvent.KEYCODE_SHIFT_RIGHT to Key("ShiftRight", 16),
        KeyEvent.KEYCODE_CTRL_LEFT to Key("ControlLeft", 17),
        KeyEvent.KEYCODE_CTRL_RIGHT to Key("ControlRight", 17),
        KeyEvent.KEYCODE_ALT_LEFT to Key("AltLeft", 18),
        KeyEvent.KEYCODE_ALT_RIGHT to Key("AltRight", 18),
        KeyEvent.KEYCODE_ESCAPE to Key("Escape", 27),
        KeyEvent.KEYCODE_SPACE to Key("Space", 32),
        KeyEvent.KEYCODE_PAGE_UP to Key("PageUp", 33),
        KeyEvent.KEYCODE_PAGE_DOWN to Key("PageDown", 34),
        KeyEvent.KEYCODE_MOVE_END to Key("End", 35),
        KeyEvent.KEYCODE_MOVE_HOME to Key("Home", 36),
        KeyEvent.KEYCODE_DPAD_LEFT to Key("ArrowLeft", 37),
        KeyEvent.KEYCODE_DPAD_UP to Key("ArrowUp", 38),
        KeyEvent.KEYCODE_DPAD_RIGHT to Key("ArrowRight", 39),
        KeyEvent.KEYCODE_DPAD_DOWN to Key("ArrowDown", 40),
        KeyEvent.KEYCODE_INSERT to Key("Insert", 45),
        KeyEvent.KEYCODE_FORWARD_DEL to Key("Delete", 46),
        KeyEvent.KEYCODE_META_LEFT to Key("MetaLeft", 91),
        KeyEvent.KEYCODE_META_RIGHT to Key("MetaRight", 92),
        KeyEvent.KEYCODE_SEMICOLON to Key("Semicolon", 186),
        KeyEvent.KEYCODE_EQUALS to Key("Equal", 187),
        KeyEvent.KEYCODE_COMMA to Key("Comma", 188),
        KeyEvent.KEYCODE_MINUS to Key("Minus", 189),
        KeyEvent.KEYCODE_PERIOD to Key("Period", 190),
        KeyEvent.KEYCODE_SLASH to Key("Slash", 191),
        KeyEvent.KEYCODE_GRAVE to Key("Backquote", 192),
        KeyEvent.KEYCODE_LEFT_BRACKET to Key("BracketLeft", 219),
        KeyEvent.KEYCODE_BACKSLASH to Key("Backslash", 220),
        KeyEvent.KEYCODE_RIGHT_BRACKET to Key("BracketRight", 221),
        KeyEvent.KEYCODE_APOSTROPHE to Key("Quote", 222),
    )

    fun of(keyCode: Int): Key? = NAMED[keyCode] ?: when (keyCode) {
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> ('A' + (keyCode - KeyEvent.KEYCODE_A)).let { Key("Key$it", it.code) }
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> (keyCode - KeyEvent.KEYCODE_0).let { Key("Digit$it", 48 + it) }
        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> (keyCode - KeyEvent.KEYCODE_NUMPAD_0).let { Key("Numpad$it", 96 + it) }
        in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> (keyCode - KeyEvent.KEYCODE_F1 + 1).let { Key("F$it", 111 + it) }
        else -> null
    }
}
