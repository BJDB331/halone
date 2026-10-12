package dev.halone.engine.webview

import android.net.Uri
import android.view.KeyEvent
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

/** The text field a page has focused, as the phone's keyboard needs to know it. */
data class TextField(
    val value: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val inputType: String,
    val multiline: Boolean,
    val enterKeyHint: String,
    val placeholder: String,
)

/**
 * Typing into a page on a virtual display, from the phone's own keyboard.
 *
 * Android attaches its keyboard only to windows on displays that ask for one,
 * which a virtual display cannot without a system permission, so the keyboard
 * stays on the phone and this carries text across: a script at document start
 * reports focus, and the host writes the field's whole text back, firing the
 * `input` event frameworks listen for.
 */
/** The phone's clipboard, for copy and paste from a physical keyboard. */
interface TextClipboard {
    fun read(): String?
    fun write(text: String)
}

class WebViewTextInput private constructor(
    private val onField: (TextField?) -> Unit,
    private val clipboard: TextClipboard?,
) {
    private var reply: JavaScriptReplyProxy? = null

    fun setText(value: String, selectionStart: Int, selectionEnd: Int) = send(
        JSONObject().put("type", "set").put("value", value).put("start", selectionStart).put("end", selectionEnd),
    )

    /** The keyboard's action key: Enter, then the form's submit if nothing handled it. */
    fun submit() = send(JSONObject().put("type", "action"))

    fun dismiss() = send(JSONObject().put("type", "done"))

    /**
     * A key from a physical keyboard. Dispatched keys never reach a WebView
     * whose window lacks focus, as a panel's always does, so the page script
     * fires the DOM events and does the editing a real key would have done.
     * Only presses are sent; the script follows each with its release.
     */
    fun key(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return domKey(event) != null
        val key = domKey(event) ?: return false
        val dom = DomKeys.of(event.keyCode)
        send(
            JSONObject()
                .put("type", "key")
                .put("key", key)
                .put("code", dom?.code ?: "")
                .put("keyCode", dom?.keyCode ?: 0)
                .put("ctrl", event.isCtrlPressed)
                .put("alt", event.isAltPressed)
                .put("shift", event.isShiftPressed)
                .put("meta", event.isMetaPressed)
                .put("repeat", event.repeatCount > 0),
        )
        return true
    }

    private fun domKey(event: KeyEvent): String? = NAMED_KEYS[event.keyCode]
        ?: event.getUnicodeChar(event.metaState and KeyEvent.META_CTRL_MASK.inv() and KeyEvent.META_META_MASK.inv())
            .takeIf { it > 0 && event.keyCode != KeyEvent.KEYCODE_UNKNOWN }
            ?.let { String(Character.toChars(it)) }

    private fun send(message: JSONObject) {
        reply?.postMessage(message.toString())
    }

    private fun receive(text: String, replyProxy: JavaScriptReplyProxy) {
        reply = replyProxy
        val message = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (message.optString("type")) {
            "ready" -> return
            "copy" -> return message.optString("text").takeIf { it.isNotEmpty() }?.let { clipboard?.write(it) } ?: Unit
            "paste-request" -> return send(JSONObject().put("type", "paste").put("id", message.optInt("id")).put("text", clipboard?.read() ?: ""))
        }
        onField(
            if (message.optString("type") != "focus") null
            else TextField(
                value = message.optString("value"),
                selectionStart = message.optInt("start"),
                selectionEnd = message.optInt("end"),
                inputType = message.optString("inputType", "text"),
                multiline = message.optBoolean("multiline"),
                enterKeyHint = message.optString("enterKeyHint"),
                placeholder = message.optString("placeholder"),
            ),
        )
    }

    companion object {
        private const val OBJECT_NAME = "haloneTextInput"

        private val NAMED_KEYS = mapOf(
            KeyEvent.KEYCODE_DEL to "Backspace",
            KeyEvent.KEYCODE_FORWARD_DEL to "Delete",
            KeyEvent.KEYCODE_DPAD_LEFT to "ArrowLeft",
            KeyEvent.KEYCODE_DPAD_RIGHT to "ArrowRight",
            KeyEvent.KEYCODE_DPAD_UP to "ArrowUp",
            KeyEvent.KEYCODE_DPAD_DOWN to "ArrowDown",
            KeyEvent.KEYCODE_MOVE_HOME to "Home",
            KeyEvent.KEYCODE_MOVE_END to "End",
            KeyEvent.KEYCODE_PAGE_UP to "PageUp",
            KeyEvent.KEYCODE_PAGE_DOWN to "PageDown",
            KeyEvent.KEYCODE_ENTER to "Enter",
            KeyEvent.KEYCODE_NUMPAD_ENTER to "Enter",
            KeyEvent.KEYCODE_TAB to "Tab",
            KeyEvent.KEYCODE_ESCAPE to "Escape",
            KeyEvent.KEYCODE_SHIFT_LEFT to "Shift",
            KeyEvent.KEYCODE_SHIFT_RIGHT to "Shift",
            KeyEvent.KEYCODE_CTRL_LEFT to "Control",
            KeyEvent.KEYCODE_CTRL_RIGHT to "Control",
            KeyEvent.KEYCODE_ALT_LEFT to "Alt",
            KeyEvent.KEYCODE_ALT_RIGHT to "Alt",
            KeyEvent.KEYCODE_META_LEFT to "Meta",
            KeyEvent.KEYCODE_META_RIGHT to "Meta",
            KeyEvent.KEYCODE_INSERT to "Insert",
        ) + (KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12).associateWith { "F${it - KeyEvent.KEYCODE_F1 + 1}" }

        val supported: Boolean
            get() = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
                WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

        /** Call before the first load: the script applies to documents that start afterwards. */
        fun install(view: WebView, origin: String, clipboard: TextClipboard?, onField: (TextField?) -> Unit): WebViewTextInput? {
            if (!supported) return null
            val input = WebViewTextInput(onField, clipboard)
            val origins = setOf(origin)
            WebViewCompat.addWebMessageListener(view, OBJECT_NAME, origins) { _, message: WebMessageCompat, _: Uri, isMainFrame, replyProxy ->
                if (isMainFrame) message.data?.let { input.receive(it, replyProxy) }
            }
            WebViewCompat.addDocumentStartJavaScript(view, SCRIPT, origins)
            return input
        }

        private val SCRIPT = """
            (() => {
              const channel = window.$OBJECT_NAME;
              if (!channel) return;
              const NOT_TEXT = ['button', 'checkbox', 'radio', 'range', 'submit', 'reset', 'file', 'color', 'image', 'hidden'];
              const editable = (el) => el && (el.tagName === 'TEXTAREA' || (el.tagName === 'INPUT' && !NOT_TEXT.includes(el.type)));
              let field = null;
              // The field's text as the phone's keyboard last sent it, which a
              // page that empties its own field, as a terminal does, no longer holds.
              let sent = null;
              const report = () => channel.postMessage(JSON.stringify(field ? {
                type: 'focus', value: field.value, start: field.selectionStart ?? field.value.length,
                end: field.selectionEnd ?? field.value.length, inputType: field.type || 'text',
                multiline: field.tagName === 'TEXTAREA', enterKeyHint: field.enterKeyHint || '', placeholder: field.placeholder || ''
              } : { type: 'blur' }));
              // A panel's window never has focus, so focus events do not fire:
              // what is focused is read from activeElement after anything that
              // could have moved it, and on a slow poll for changes made in code.
              const check = () => {
                const active = document.activeElement;
                const next = editable(active) ? active : null;
                if (next === field) return;
                field = next;
                sent = null;
                report();
              };
              const soon = () => setTimeout(check, 0);
              for (const type of ['focusin', 'focusout', 'pointerup', 'click', 'keyup']) document.addEventListener(type, soon, true);
              setInterval(check, 250);
              const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]):not([type=hidden]), select, textarea, [tabindex]:not([tabindex="-1"])';
              const edit = (el, text, start, end) => {
                el.setRangeText(text, start, end, 'end');
                el.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: text ? 'insertText' : 'deleteContent', data: text || null }));
              };
              // The prototype's setter, not el.value: frameworks track the
              // value through it and would otherwise miss the change.
              const setValue = (el, value) => {
                const proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
                Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, value);
              };
              // Number and email fields have no caret to edit at, so keys edit their end.
              const caretless = (el) => { try { return el.selectionStart === null; } catch (_) { return true; } };
              const typeAtEnd = (el, m) => {
                const shortcut = m.ctrl || m.meta;
                let next = null;
                if (m.key.length === 1 && !shortcut) next = el.value + m.key;
                else if (m.key === 'Backspace') next = el.value.slice(0, -1);
                else if ((m.key === 'ArrowUp' || m.key === 'ArrowDown') && el.type === 'number') {
                  try { m.key === 'ArrowUp' ? el.stepUp() : el.stepDown(); } catch (_) { return; }
                  el.dispatchEvent(new Event('input', { bubbles: true }));
                  return el.dispatchEvent(new Event('change', { bubbles: true }));
                } else if (m.key === 'Enter') return el.form && el.form.requestSubmit();
                else if (m.key === 'Escape') return el.blur();
                if (next === null) return;
                setValue(el, next);
                el.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: m.key === 'Backspace' ? 'deleteContentBackward' : 'insertText', data: m.key.length === 1 ? m.key : null }));
              };
              const moveCaret = (el, to, extend) => {
                const anchor = el.selectionDirection === 'backward' ? el.selectionEnd : el.selectionStart;
                if (extend) el.setSelectionRange(Math.min(anchor, to), Math.max(anchor, to), to < anchor ? 'backward' : 'forward');
                else el.setSelectionRange(to, to);
              };
              const typeInto = (el, m) => {
                if (caretless(el)) return typeAtEnd(el, m);
                const start = el.selectionStart ?? el.value.length, end = el.selectionEnd ?? start;
                const shortcut = m.ctrl || m.meta;
                if (m.key.length === 1 && !shortcut) return edit(el, m.key, start, end);
                if (shortcut && m.key.toLowerCase() === 'a') return el.setSelectionRange(0, el.value.length);
                switch (m.key) {
                  case 'Backspace': return start !== end ? edit(el, '', start, end) : start > 0 && edit(el, '', start - 1, start);
                  case 'Delete': return start !== end ? edit(el, '', start, end) : end < el.value.length && edit(el, '', start, start + 1);
                  case 'ArrowLeft': case 'ArrowRight': {
                    const right = m.key === 'ArrowRight';
                    if (!m.shift && start !== end) return moveCaret(el, right ? end : start, false);
                    const focus = el.selectionDirection === 'backward' ? start : end;
                    return moveCaret(el, Math.min(el.value.length, Math.max(0, focus + (right ? 1 : -1))), m.shift);
                  }
                  case 'Home': case 'ArrowUp': return moveCaret(el, 0, m.shift);
                  case 'End': case 'ArrowDown': return moveCaret(el, el.value.length, m.shift);
                  case 'Enter':
                    if (el.tagName === 'TEXTAREA') return edit(el, '\n', start, end);
                    return el.form && el.form.requestSubmit();
                  case 'Escape': return el.blur();
                }
              };
              const pageKey = (m) => {
                const target = document.activeElement;
                const page = window.innerHeight * 0.9;
                const scroll = { ArrowDown: 40, ArrowUp: -40, PageDown: page, PageUp: -page, ' ': m.shift ? -page : page }[m.key];
                if (scroll) return window.scrollBy({ top: scroll, behavior: 'smooth' });
                if (m.key === 'Home' || m.key === 'End') return window.scrollTo({ top: m.key === 'Home' ? 0 : document.body.scrollHeight });
                if (m.key === 'Enter' && target && target !== document.body) return target.click();
                if (m.key === 'Tab') {
                  const all = [...document.querySelectorAll(FOCUSABLE)].filter((e) => e.offsetParent !== null);
                  if (!all.length) return;
                  const at = all.indexOf(target);
                  all[(at + (m.shift ? -1 : 1) + all.length) % all.length].focus();
                }
              };
              // A page's own copy, cut and paste happen only for keys the
              // browser itself saw, never for these; so they are done here,
              // with the page's clipboard events still fired for it to take.
              let nextPaste = 1, pasting = null;
              const selectedText = () => {
                if (field && !caretless(field)) return field.value.slice(field.selectionStart, field.selectionEnd);
                return String(window.getSelection() ?? '');
              };
              const clipboardKey = (target, op) => {
                if (op === 'v') {
                  pasting = { id: nextPaste++, target };
                  return channel.postMessage(JSON.stringify({ type: 'paste-request', id: pasting.id }));
                }
                const data = new DataTransfer();
                const taken = !target.dispatchEvent(new ClipboardEvent(op === 'x' ? 'cut' : 'copy', { clipboardData: data, bubbles: true, cancelable: true }));
                const text = taken ? data.getData('text/plain') : selectedText();
                if (!text) return;
                channel.postMessage(JSON.stringify({ type: 'copy', text }));
                if (op === 'x' && !taken && field) {
                  if (caretless(field)) {
                    setValue(field, '');
                    field.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'deleteByCut' }));
                  } else edit(field, '', field.selectionStart, field.selectionEnd);
                }
              };
              const paste = (text) => {
                const { target } = pasting;
                pasting = null;
                if (!text) return;
                const data = new DataTransfer();
                data.setData('text/plain', text);
                if (!target.dispatchEvent(new ClipboardEvent('paste', { clipboardData: data, bubbles: true, cancelable: true })) || !field) return;
                if (caretless(field)) {
                  setValue(field, field.value + text);
                  field.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertFromPaste', data: text }));
                } else edit(field, text, field.selectionStart, field.selectionEnd);
              };
              const keyFrom = (m) => {
                const target = field || document.activeElement || document.body;
                const init = {
                  key: m.key, code: m.code, keyCode: m.keyCode, which: m.keyCode, ctrlKey: m.ctrl, altKey: m.alt, shiftKey: m.shift,
                  metaKey: m.meta, repeat: m.repeat, bubbles: true, cancelable: true,
                };
                const handled = !target.dispatchEvent(new KeyboardEvent('keydown', init));
                const clip = (m.ctrl || m.meta) && !m.alt && ['c', 'x', 'v'].includes(m.key.toLowerCase());
                if (!handled && clip) clipboardKey(target, m.key.toLowerCase());
                else if (!handled && !['Shift', 'Control', 'Alt', 'Meta'].includes(m.key)) {
                  if (field && m.key !== 'Tab') typeInto(field, m);
                  else pageKey(m);
                }
                target.dispatchEvent(new KeyboardEvent('keyup', init));
                check();
              };
              channel.postMessage(JSON.stringify({ type: 'ready' }));
              channel.onmessage = (event) => {
                const message = JSON.parse(event.data);
                if (message.type === 'key') return keyFrom(message);
                if (message.type === 'paste') return pasting && pasting.id === message.id && paste(message.text);
                const el = field;
                if (!el) return;
                if (message.type === 'set') {
                  // What changed since the last text, as a typist would have
                  // done it: deletions as Backspace presses a page may take for
                  // itself, then the inserted text as the input's data.
                  const before = sent ?? el.value, after = message.value;
                  sent = after;
                  let head = 0;
                  while (head < before.length && head < after.length && before[head] === after[head]) head++;
                  let tail = 0;
                  while (tail < before.length - head && tail < after.length - head && before[before.length - 1 - tail] === after[after.length - 1 - tail]) tail++;
                  const removed = before.length - head - tail, inserted = after.slice(head, after.length - tail);
                  let pageTyped = false;
                  for (let i = 0; i < removed; i++) {
                    const init = { key: 'Backspace', code: 'Backspace', keyCode: 8, which: 8, bubbles: true, cancelable: true };
                    if (!el.dispatchEvent(new KeyboardEvent('keydown', init))) pageTyped = true;
                    el.dispatchEvent(new KeyboardEvent('keyup', init));
                  }
                  if (pageTyped) {
                    if (inserted) el.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText', data: inserted }));
                    return;
                  }
                  setValue(el, after);
                  try { el.setSelectionRange(message.start, message.end); } catch (_) {}
                  el.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: inserted ? 'insertText' : 'deleteContentBackward', data: inserted || null }));
                } else if (message.type === 'action') {
                  const init = { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true, cancelable: true };
                  const handled = !el.dispatchEvent(new KeyboardEvent('keydown', init));
                  el.dispatchEvent(new KeyboardEvent('keyup', init));
                  if (!handled && el.form && el.tagName !== 'TEXTAREA') el.form.requestSubmit();
                } else if (message.type === 'done') {
                  el.blur();
                }
              };
            })();
        """.trimIndent()
    }
}
