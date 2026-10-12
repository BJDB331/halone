package dev.halone.engine.webview

import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject

data class SelectOption(val label: String, val disabled: Boolean, val selected: Boolean, val group: String?)

/** A page's `<select>` to choose from, its corner at ([xPx], [yPx]) in the page. */
data class SelectPopup(val options: List<SelectOption>, val multiple: Boolean, val xPx: Float, val yPx: Float)

/**
 * A page's dropdowns, shown by whoever hosts the panel. WebView's own is an
 * Android dialog, which cannot open from a panel's window and takes the whole
 * app down when it tries; a script at document start stops it from opening
 * and asks here instead.
 */
class WebViewSelects private constructor(private val onOpen: (SelectPopup, answer: (List<Int>?) -> Unit) -> Unit) {
    private fun receive(text: String, reply: JavaScriptReplyProxy) {
        val message = runCatching { JSONObject(text) }.getOrNull() ?: return
        if (message.optString("type") != "open") return
        val id = message.optInt("id")
        val options = message.optJSONArray("options") ?: JSONArray()
        val popup = SelectPopup(
            options = (0 until options.length()).map { index ->
                val option = options.getJSONObject(index)
                SelectOption(
                    label = option.optString("label"),
                    disabled = option.optBoolean("disabled"),
                    selected = option.optBoolean("selected"),
                    group = option.optString("group").takeIf { it.isNotEmpty() && !option.isNull("group") },
                )
            },
            multiple = message.optBoolean("multiple"),
            xPx = message.optDouble("x", 0.0).toFloat(),
            yPx = message.optDouble("y", 0.0).toFloat(),
        )
        onOpen(popup) { chosen ->
            if (chosen != null) reply.postMessage(JSONObject().put("type", "choose").put("id", id).put("indices", JSONArray(chosen)).toString())
        }
    }

    companion object {
        private const val OBJECT_NAME = "haloneSelect"

        /** Call before the first load. Null when the WebView cannot run the script, which leaves its own dropdown. */
        fun install(view: WebView, origin: String, onOpen: (SelectPopup, (List<Int>?) -> Unit) -> Unit): WebViewSelects? {
            val supported = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
                WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
            if (!supported) return null
            val selects = WebViewSelects(onOpen)
            val origins = setOf(origin)
            WebViewCompat.addWebMessageListener(view, OBJECT_NAME, origins) { _, message: WebMessageCompat, _, isMainFrame, reply ->
                if (isMainFrame) message.data?.let { selects.receive(it, reply) }
            }
            WebViewCompat.addDocumentStartJavaScript(view, SCRIPT, origins)
            return selects
        }

        private val SCRIPT = """
            (() => {
              const channel = window.$OBJECT_NAME;
              if (!channel) return;
              let next = 1, pending = null;
              const open = (select) => {
                select.focus();
                const box = select.getBoundingClientRect();
                const options = [...select.options].map((o) => ({
                  label: o.label || o.text,
                  disabled: o.disabled || (o.parentElement.tagName === 'OPTGROUP' && o.parentElement.disabled),
                  selected: o.selected,
                  group: o.parentElement.tagName === 'OPTGROUP' ? o.parentElement.label : null,
                }));
                pending = { id: next++, select };
                channel.postMessage(JSON.stringify({ type: 'open', id: pending.id, multiple: select.multiple, x: box.left, y: box.bottom, options }));
              };
              document.addEventListener('mousedown', (event) => {
                const select = event.target instanceof Element ? event.target.closest('select') : null;
                if (!select || select.disabled || event.button !== 0) return;
                event.preventDefault();
                open(select);
              }, true);
              document.addEventListener('keydown', (event) => {
                const select = document.activeElement;
                if (!(select instanceof HTMLSelectElement) || select.disabled) return;
                const opens = event.key === ' ' || event.key === 'Enter' || (event.altKey && (event.key === 'ArrowDown' || event.key === 'ArrowUp'));
                if (!opens) return;
                event.preventDefault();
                open(select);
              }, true);
              channel.onmessage = (event) => {
                const message = JSON.parse(event.data);
                if (message.type !== 'choose' || !pending || message.id !== pending.id) return;
                const { select } = pending;
                pending = null;
                const chosen = new Set(message.indices);
                if (select.multiple) [...select.options].forEach((o, i) => (o.selected = chosen.has(i)));
                else if (message.indices.length) select.selectedIndex = message.indices[0];
                select.dispatchEvent(new Event('input', { bubbles: true }));
                select.dispatchEvent(new Event('change', { bubbles: true }));
              };
            })();
        """.trimIndent()
    }
}
