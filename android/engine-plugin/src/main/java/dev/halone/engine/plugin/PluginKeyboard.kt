package dev.halone.engine.plugin

import android.view.KeyEvent
import dev.halone.plugin.KeyboardRequest
import dev.halone.plugin.KeyboardState
import dev.halone.plugin.TextInputEvent

/** The phone's keyboard, as the shell lends it to plugin panels, one at a time. Main thread. */
interface PluginKeyboard {
    fun show(target: KeyboardTarget, request: KeyboardRequest)
    fun update(target: KeyboardTarget, state: KeyboardState)

    /** Puts the keyboard away if [target] has it. */
    fun hide(target: KeyboardTarget)
}

/** Where the keyboard's edits go: a plugin panel. */
interface KeyboardTarget {
    fun onTextInput(event: TextInputEvent)
    fun onKey(event: KeyEvent)
}
