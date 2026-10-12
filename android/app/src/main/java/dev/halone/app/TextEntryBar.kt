package dev.halone.app

import android.app.Activity
import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.halone.engine.webview.TextField

/**
 * The phone's own keyboard, for a text field on the glasses.
 *
 * Shown at the top of the phone screen, over the pointer pad, while a page has
 * a field focused. Everything typed is sent whole to the page, so autocorrect,
 * swipe typing and composition all work without the page knowing about them.
 */
class TextEntryBar(
    private val activity: Activity,
    private val onText: (text: String, selectionStart: Int, selectionEnd: Int) -> Unit,
    private val onSubmit: () -> Unit,
    private val onDismiss: () -> Unit,
    /**
     * A captured mouse's events, which Android hands to whichever view has
     * focus: while this one is being typed into, they still move the cursor.
     */
    private val onCapturedPointer: (android.view.MotionEvent) -> Boolean,
) {
    private var bar: View? = null
    private var edit: EditText? = null
    private var echoing = false
    private var multiline = false

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable) {
            val view = edit ?: return
            if (!echoing) onText(s.toString(), view.selectionStart, view.selectionEnd)
        }
    }

    fun show(field: TextField) {
        val view = edit ?: build()
        multiline = field.multiline
        echoing = true
        view.inputType = inputTypeFor(field)
        view.imeOptions = actionFor(field) or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        view.maxLines = if (field.multiline) 4 else 1
        view.hint = field.placeholder.ifEmpty { "Type here" }
        view.setText(field.value)
        view.setSelection(
            field.selectionStart.coerceIn(0, field.value.length),
            field.selectionEnd.coerceIn(0, field.value.length),
        )
        echoing = false
        bar?.visibility = View.VISIBLE
        bar?.bringToFront()
        view.requestFocus()
        keyboard().restartInput(view)
        keyboard().showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hide() {
        val view = edit ?: return
        keyboard().hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
        bar?.visibility = View.GONE
    }

    private fun build(): EditText {
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val input = EditText(activity).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(140, 150, 170))
            setBackgroundColor(Color.rgb(30, 38, 52))
            setPadding(PADDING, PADDING, PADDING, PADDING)
            addTextChangedListener(watcher)
            setOnCapturedPointerListener { _, event -> onCapturedPointer(event) }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_NULL && multiline) return@setOnEditorActionListener false
                onSubmit()
                if (!multiline) {
                    onDismiss()
                    hide()
                }
                true
            }
        }
        val done = Button(activity).apply {
            text = "Done"
            setOnClickListener {
                onDismiss()
                hide()
            }
        }
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.rgb(15, 20, 30))
            setPadding(PADDING, PADDING * 2, PADDING, PADDING)
            addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(done, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            visibility = View.GONE
            isClickable = true
        }
        // Clear of the status bar and any cutout, which otherwise cover the field.
        ViewCompat.setOnApplyWindowInsetsListener(row) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()).top
            view.setPadding(PADDING, top + PADDING, PADDING, PADDING)
            insets
        }
        root.addView(
            row,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP),
        )
        bar = row
        edit = input
        return input
    }

    private fun keyboard() = activity.getSystemService(InputMethodManager::class.java)

    private fun inputTypeFor(field: TextField): Int {
        val variation = when (field.inputType) {
            "password" -> InputType.TYPE_TEXT_VARIATION_PASSWORD
            "email" -> InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            "url" -> InputType.TYPE_TEXT_VARIATION_URI
            "number" -> return InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            "tel" -> return InputType.TYPE_CLASS_PHONE
            else -> InputType.TYPE_TEXT_VARIATION_NORMAL
        }
        val lines = if (field.multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0
        return InputType.TYPE_CLASS_TEXT or variation or lines
    }

    private fun actionFor(field: TextField): Int = when (field.enterKeyHint.ifEmpty { if (field.inputType == "search") "search" else "" }) {
        "go" -> EditorInfo.IME_ACTION_GO
        "search" -> EditorInfo.IME_ACTION_SEARCH
        "send" -> EditorInfo.IME_ACTION_SEND
        "next" -> EditorInfo.IME_ACTION_NEXT
        "enter" -> EditorInfo.IME_ACTION_UNSPECIFIED
        else -> if (field.multiline) EditorInfo.IME_ACTION_UNSPECIFIED else EditorInfo.IME_ACTION_DONE
    }

    private companion object {
        const val PADDING = 24
    }
}
