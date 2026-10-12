package dev.halone.app.panel

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import org.mozilla.geckoview.GeckoSession

/**
 * The phone's keyboard for web pages in Gecko panels.
 *
 * Gecko hands out a real `InputConnection` for whatever it has focused, so
 * unlike a WebView panel nothing has to be copied across: the keyboard talks
 * to Gecko directly through a view on the phone that stands in for the panel.
 * Composition, autocorrect and swipe typing all behave as in any app.
 *
 * One stand-in serves every session, following whichever last asked for the
 * keyboard. Gecko calls the delegate on the UI thread.
 */
class GeckoKeyboard(
    private val activity: Activity,
    /** False while a physical keyboard is connected: it types into Gecko directly. */
    private val wanted: () -> Boolean,
    private val onShown: () -> Unit,
    private val onHidden: () -> Unit,
    /**
     * A captured mouse's events, which Android hands to whichever view has
     * focus: while this one is being typed into, they still move the cursor.
     */
    private val onCapturedPointer: (android.view.MotionEvent) -> Boolean,
) {
    private var active: GeckoSession? = null
    private var showing = false

    private val keyboard get() = activity.getSystemService(InputMethodManager::class.java)

    private val standIn: View by lazy {
        object : View(activity) {
            override fun onCheckIsTextEditor() = active != null
            override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? =
                active?.textInput?.onCreateInputConnection(outAttrs)
        }.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setOnCapturedPointerListener { _, event -> onCapturedPointer(event) }
            activity.findViewById<ViewGroup>(android.R.id.content).addView(this, FrameLayout.LayoutParams(1, 1))
        }
    }

    private val delegate = object : GeckoSession.TextInputDelegate {
        override fun restartInput(session: GeckoSession, reason: Int) {
            if (reason == GeckoSession.TextInputDelegate.RESTART_REASON_FOCUS) active = session
            if (session === active) keyboard.restartInput(standIn)
        }

        override fun showSoftInput(session: GeckoSession) {
            active = session
            if (!wanted()) return
            standIn.requestFocus()
            keyboard.restartInput(standIn)
            keyboard.showSoftInput(standIn, 0)
            if (!showing) {
                showing = true
                onShown()
            }
        }

        override fun hideSoftInput(session: GeckoSession) {
            if (session === active) hide()
        }

        override fun updateSelection(session: GeckoSession, selStart: Int, selEnd: Int, compositionStart: Int, compositionEnd: Int) {
            if (session === active) keyboard.updateSelection(standIn, selStart, selEnd, compositionStart, compositionEnd)
        }

        override fun updateExtractedText(session: GeckoSession, request: ExtractedTextRequest, text: ExtractedText) {
            if (session === active) keyboard.updateExtractedText(standIn, request.token, text)
        }

        override fun updateCursorAnchorInfo(session: GeckoSession, info: CursorAnchorInfo) {
            if (session === active) keyboard.updateCursorAnchorInfo(standIn, info)
        }
    }

    fun attach(session: GeckoSession) {
        session.textInput.setView(standIn)
        session.textInput.setDelegate(delegate)
    }

    fun detach(session: GeckoSession) {
        if (session !== active) return
        hide()
        active = null
    }

    private fun hide() {
        keyboard.hideSoftInputFromWindow(standIn.windowToken, 0)
        standIn.clearFocus()
        if (showing) {
            showing = false
            onHidden()
        }
    }
}
