package dev.halone.app.panel

import android.app.Activity
import android.text.Editable
import android.text.Selection
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.halone.engine.plugin.KeyboardTarget
import dev.halone.engine.plugin.PluginKeyboard
import dev.halone.plugin.KeyboardRequest
import dev.halone.plugin.KeyboardState
import dev.halone.plugin.TextInputEvent

/**
 * The phone's keyboard for plugin panels. As for Gecko pages, a view on the
 * phone stands in for the panel and hands the keyboard a real
 * `InputConnection`; this one keeps a copy of the field to answer the
 * keyboard's questions from (the text around the cursor, for suggestions) and
 * sends every edit on to the plugin, which applies it to its own field.
 */
class PhoneKeyboard(
    private val activity: Activity,
    /** False while a physical keyboard is connected: its keys go to the panel as keys. */
    private val wanted: () -> Boolean,
    private val onShown: () -> Unit,
    private val onHidden: () -> Unit,
    /**
     * A captured mouse's events, which Android hands to whichever view has
     * focus: while this one is being typed into, they still move the cursor.
     */
    private val onCapturedPointer: (android.view.MotionEvent) -> Boolean,
) : PluginKeyboard {
    private var target: KeyboardTarget? = null
    private var request = KeyboardRequest()
    private val field: Editable = Editable.Factory.getInstance().newEditable("")
    private var imeSeen = false

    private val keyboard get() = activity.getSystemService(InputMethodManager::class.java)

    private val standIn: View by lazy {
        object : View(activity) {
            override fun onCheckIsTextEditor() = target != null
            override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
                target ?: return null
                outAttrs.inputType = request.inputType
                outAttrs.imeOptions = request.imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
                outAttrs.hintText = request.hint
                outAttrs.initialSelStart = Selection.getSelectionStart(field)
                outAttrs.initialSelEnd = Selection.getSelectionEnd(field)
                return Connection(this)
            }
        }.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setOnCapturedPointerListener { _, event -> onCapturedPointer(event) }
            activity.findViewById<ViewGroup>(android.R.id.content).addView(this, FrameLayout.LayoutParams(1, 1))
            viewTreeObserver.addOnGlobalLayoutListener(imeWatcher)
        }
    }

    /** The wearer can put the keyboard away on the phone; the plugin hears so, and a tap on its field brings it back. */
    private val imeWatcher = ViewTreeObserver.OnGlobalLayoutListener {
        val visible = ViewCompat.getRootWindowInsets(standIn)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        if (visible) {
            imeSeen = true
        } else if (imeSeen && target != null) {
            imeSeen = false
            target?.onTextInput(TextInputEvent.Dismissed)
            release()
        }
    }

    override fun show(target: KeyboardTarget, request: KeyboardRequest) {
        if (!wanted()) return
        val previous = this.target
        if (previous != null && previous !== target) previous.onTextInput(TextInputEvent.Dismissed)
        this.target = target
        this.request = request
        set(request.state)
        imeSeen = false
        standIn.requestFocus()
        keyboard.restartInput(standIn)
        keyboard.showSoftInput(standIn, 0)
        if (previous == null) onShown()
    }

    override fun update(target: KeyboardTarget, state: KeyboardState) {
        if (target !== this.target) return
        val text = field.toString()
        val start = Selection.getSelectionStart(field)
        val end = Selection.getSelectionEnd(field)
        if (state.text == text && state.selectionStart == start && state.selectionEnd == end) return
        set(state)
        if (state.text != text) {
            keyboard.restartInput(standIn)
        } else {
            keyboard.updateSelection(standIn, state.selectionStart, state.selectionEnd, -1, -1)
        }
    }

    override fun hide(target: KeyboardTarget) {
        if (target !== this.target) return
        keyboard.hideSoftInputFromWindow(standIn.windowToken, 0)
        release()
    }

    private fun release() {
        target = null
        standIn.clearFocus()
        onHidden()
    }

    private fun set(state: KeyboardState) {
        BaseInputConnection.removeComposingSpans(field)
        field.replace(0, field.length, state.text)
        Selection.setSelection(field, state.selectionStart, state.selectionEnd)
    }

    /** Applies each edit to the copy, as any editor would, and passes it on. */
    private inner class Connection(view: View) : BaseInputConnection(view, true) {
        override fun getEditable(): Editable = field

        private fun send(event: TextInputEvent) = target?.onTextInput(event)

        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            super.commitText(text, newCursorPosition)
            send(TextInputEvent.Commit(text.toString(), newCursorPosition))
            return true
        }

        override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
            super.setComposingText(text, newCursorPosition)
            send(TextInputEvent.Compose(text.toString(), newCursorPosition))
            return true
        }

        override fun setComposingRegion(start: Int, end: Int): Boolean {
            super.setComposingRegion(start, end)
            send(TextInputEvent.ComposingRegion(start, end))
            return true
        }

        override fun finishComposingText(): Boolean {
            super.finishComposingText()
            send(TextInputEvent.FinishComposing)
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            super.deleteSurroundingText(beforeLength, afterLength)
            send(TextInputEvent.DeleteSurrounding(beforeLength, afterLength))
            return true
        }

        /** In code points to the keyboard, in UTF-16 units to the plugin. */
        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
            val start = Selection.getSelectionStart(field).coerceAtLeast(0)
            val end = Selection.getSelectionEnd(field).coerceAtLeast(0)
            val before = start - offsetByCodePoints(start, -beforeLength)
            val after = offsetByCodePoints(end, afterLength) - end
            return deleteSurroundingText(before, after)
        }

        private fun offsetByCodePoints(from: Int, count: Int): Int =
            runCatching { Character.offsetByCodePoints(field, from, count) }.getOrElse { if (count < 0) 0 else field.length }

        override fun setSelection(start: Int, end: Int): Boolean {
            super.setSelection(start, end)
            send(TextInputEvent.Select(start, end))
            return true
        }

        override fun performEditorAction(editorAction: Int): Boolean {
            send(TextInputEvent.EditorAction(editorAction))
            return true
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            target?.onKey(event)
            if (event.action == KeyEvent.ACTION_DOWN) applyKey(event)
            return true
        }

        /** Keeps the copy in step with the keys a keyboard sends as keys. */
        private fun applyKey(event: KeyEvent) {
            val start = Selection.getSelectionStart(field).coerceAtLeast(0)
            val end = Selection.getSelectionEnd(field).coerceAtLeast(0)
            when (event.keyCode) {
                KeyEvent.KEYCODE_DEL -> if (start != end) field.delete(start, end) else if (start > 0) field.delete(offsetByCodePoints(start, -1), start)
                KeyEvent.KEYCODE_FORWARD_DEL -> if (start != end) field.delete(start, end) else if (end < field.length) field.delete(end, offsetByCodePoints(end, 1))
                else -> event.unicodeChar.takeIf { it != 0 }?.let { field.replace(start, end, String(Character.toChars(it))) }
            }
        }
    }
}
