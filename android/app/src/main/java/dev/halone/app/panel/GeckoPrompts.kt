package dev.halone.app.panel

import android.content.Context
import android.net.Uri
import dev.halone.shell.MenuItem
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate.BasePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse

/**
 * A page's own dialogs, shown by the shell over its window: a dropdown as a
 * menu where the wearer pressed, alerts, confirmations, questions and site
 * logins as dialogs, date, time and colour fields as pickers, a file upload
 * as a picker over shared storage, and a
 * blocked pop-up as a question in the prompt bar. A dialog the page takes
 * back, as when it navigates away, goes from the glasses too.
 */
class GeckoPrompts(
    private val context: Context,
    private val panel: GeckoPanel,
    private val host: BrowserHost,
    /** Where on the window the wearer last pressed, in its pixels, which a dropdown opens from. */
    private val lastPress: () -> Pair<Float, Float>,
) : PromptDelegate {

    /** Shows what [open] puts up, completing with [respond] once the wearer answers, unless the page has let the prompt go. */
    private fun show(
        prompt: BasePrompt,
        open: ((String?, JSONObject) -> Unit) -> Int?,
        respond: (String?, JSONObject) -> PromptResponse,
    ): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        val id = open { choice, answer -> if (!prompt.isComplete) result.complete(respond(choice, answer)) }
            ?: return GeckoResult.fromValue(prompt.dismiss())
        prompt.setDelegate(object : PromptDelegate.PromptInstanceDelegate {
            override fun onPromptDismiss(prompt: BasePrompt) = host.cancelPopup(id)
        })
        return result
    }

    private fun dialog(kind: String, title: String?, message: String?, vararg buttons: JSONObject) = JSONObject()
        .put("kind", kind)
        .put("title", title.orEmpty())
        .put("message", message.orEmpty())
        .put("buttons", JSONArray(buttons.toList()))

    private fun button(id: String, label: String, primary: Boolean = false) =
        JSONObject().put("id", id).put("label", label).put("primary", primary)

    private val ok = button(OK, "OK", primary = true)
    private val cancel = button(CANCEL, "Cancel")

    override fun onAlertPrompt(session: GeckoSession, prompt: PromptDelegate.AlertPrompt) =
        show(prompt, { host.showDialog(dialog("message", prompt.title, prompt.message, ok), it) }) { _, _ -> prompt.dismiss() }

    override fun onButtonPrompt(session: GeckoSession, prompt: PromptDelegate.ButtonPrompt) =
        show(prompt, { host.showDialog(dialog("message", prompt.title, prompt.message, cancel, ok), it) }) { choice, _ ->
            prompt.confirm(if (choice == OK) PromptDelegate.ButtonPrompt.Type.POSITIVE else PromptDelegate.ButtonPrompt.Type.NEGATIVE)
        }

    override fun onTextPrompt(session: GeckoSession, prompt: PromptDelegate.TextPrompt) =
        show(prompt, {
            host.showDialog(dialog("text", prompt.title, prompt.message, cancel, ok).put("value", prompt.defaultValue.orEmpty()), it)
        }) { choice, answer -> if (choice == OK) prompt.confirm(answer.optString("text")) else prompt.dismiss() }

    override fun onAuthPrompt(session: GeckoSession, prompt: PromptDelegate.AuthPrompt): GeckoResult<PromptResponse> {
        val options = prompt.authOptions
        val passwordOnly = options.flags and PromptDelegate.AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD != 0
        val spec = dialog("login", prompt.title ?: "Sign in", prompt.message, cancel, button(OK, "Sign in", primary = true))
            .put("username", if (passwordOnly) JSONObject.NULL else options.username.orEmpty())
            .put("password", options.password.orEmpty())
        return show(prompt, { host.showDialog(spec, it) }) { choice, answer ->
            when {
                choice != OK -> prompt.dismiss()
                passwordOnly -> prompt.confirm(answer.optString("password"))
                else -> prompt.confirm(answer.optString("username"), answer.optString("password"))
            }
        }
    }

    /** One dropdown choice a menu row, a group a heading over its own; several at once a list in a dialog. */
    override fun onChoicePrompt(session: GeckoSession, prompt: PromptDelegate.ChoicePrompt): GeckoResult<PromptResponse> {
        val items = items(prompt.choices.orEmpty())
        if (items.none { !it.separator && !it.disabled }) return GeckoResult.fromValue(prompt.dismiss())
        if (prompt.type == PromptDelegate.ChoicePrompt.Type.MULTIPLE) {
            val spec = dialog("choices", prompt.title, prompt.message, cancel, ok)
                .put("choices", JSONArray(items.filter { !it.separator }.map { it.toJson() }))
            return show(prompt, { host.showDialog(spec, it) }) { choice, answer ->
                if (choice != OK) return@show prompt.dismiss()
                val selected = answer.optJSONArray("selected") ?: JSONArray()
                prompt.confirm(Array(selected.length()) { selected.optString(it) })
            }
        }
        val (x, y) = lastPress()
        return show(prompt, { answer -> host.showMenu(items, x, y) { choice -> answer(choice, JSONObject()) } }) { choice, _ ->
            if (choice == null) prompt.dismiss() else prompt.confirm(choice)
        }
    }

    private fun items(choices: Array<out PromptDelegate.ChoicePrompt.Choice>): List<MenuItem> = choices.flatMap { choice ->
        when {
            choice.separator -> listOf(MenuItem.SEPARATOR)
            choice.items != null -> listOf(MenuItem("group:${choice.id}", choice.label.orEmpty(), disabled = true)) +
                items(choice.items!!).map { if (it.separator) it else it.copy(label = "    " + it.label) }
            else -> listOf(MenuItem(choice.id, choice.label.orEmpty(), disabled = choice.disabled, checked = choice.selected))
        }
    }

    override fun onFilePrompt(session: GeckoSession, prompt: PromptDelegate.FilePrompt): GeckoResult<PromptResponse> {
        if (prompt.type == PromptDelegate.FilePrompt.Type.FOLDER) return GeckoResult.fromValue(prompt.dismiss())
        val multiple = prompt.type == PromptDelegate.FilePrompt.Type.MULTIPLE
        val spec = dialog("files", if (multiple) "Choose files to upload" else "Choose a file to upload", null, cancel, button(OK, "Upload", primary = true))
            .put("accept", JSONArray(prompt.mimeTypes.orEmpty().toList()))
            .put("multiple", multiple)
        return show(prompt, { host.showDialog(spec, it) }) { choice, answer ->
            val paths = answer.optJSONArray("paths") ?: JSONArray()
            val uris = (0 until paths.length()).map { Uri.fromFile(File(paths.optString(it))) }
            when {
                choice != OK || uris.isEmpty() -> prompt.dismiss()
                multiple -> prompt.confirm(context, uris.toTypedArray())
                else -> prompt.confirm(context, uris.first())
            }
        }
    }

    override fun onBeforeUnloadPrompt(session: GeckoSession, prompt: PromptDelegate.BeforeUnloadPrompt) =
        show(prompt, {
            host.showDialog(
                dialog("message", "Leave this page?", "Changes you made may not be saved.", button(CANCEL, "Stay"), button(OK, "Leave", primary = true)),
                it,
            )
        }) { choice, _ -> prompt.confirm(if (choice == OK) AllowOrDeny.ALLOW else AllowOrDeny.DENY) }

    override fun onRepostConfirmPrompt(session: GeckoSession, prompt: PromptDelegate.RepostConfirmPrompt) =
        show(prompt, {
            host.showDialog(
                dialog("message", "Send the form again?", "The page will repeat what it did, such as a payment.", cancel, button(OK, "Resend", primary = true)),
                it,
            )
        }) { choice, _ -> prompt.confirm(if (choice == OK) AllowOrDeny.ALLOW else AllowOrDeny.DENY) }

    override fun onDateTimePrompt(session: GeckoSession, prompt: PromptDelegate.DateTimePrompt): GeckoResult<PromptResponse> {
        val (inputType, title) = when (prompt.type) {
            PromptDelegate.DateTimePrompt.Type.MONTH -> "month" to "Choose a month"
            PromptDelegate.DateTimePrompt.Type.WEEK -> "week" to "Choose a week"
            PromptDelegate.DateTimePrompt.Type.TIME -> "time" to "Choose a time"
            PromptDelegate.DateTimePrompt.Type.DATETIME_LOCAL -> "datetime-local" to "Choose a date and time"
            else -> "date" to "Choose a date"
        }
        val spec = dialog("datetime", prompt.title?.takeIf { it.isNotBlank() } ?: title, null, button(CLEAR, "Clear"), cancel, button(OK, "Done", primary = true))
            .put("inputType", inputType)
            .put("value", prompt.defaultValue.orEmpty())
            .put("min", prompt.minValue ?: JSONObject.NULL)
            .put("max", prompt.maxValue ?: JSONObject.NULL)
            .put("step", prompt.stepValue ?: JSONObject.NULL)
        return show(prompt, { host.showDialog(spec, it) }) { choice, answer ->
            when (choice) {
                OK -> prompt.confirm(answer.optString("text"))
                CLEAR -> prompt.confirm("")
                else -> prompt.dismiss()
            }
        }
    }

    override fun onColorPrompt(session: GeckoSession, prompt: PromptDelegate.ColorPrompt): GeckoResult<PromptResponse> {
        val spec = dialog("color", prompt.title?.takeIf { it.isNotBlank() } ?: "Choose a colour", null, cancel, button(OK, "Done", primary = true))
            .put("value", prompt.defaultValue.orEmpty())
            .put("swatches", JSONArray(prompt.predefinedValues.orEmpty().toList()))
        return show(prompt, { host.showDialog(spec, it) }) { choice, answer ->
            if (choice == OK) prompt.confirm(answer.optString("text")) else prompt.dismiss()
        }
    }

    /** A pop-up the page opened without a press: the prompt bar asks, and an allowed one opens as a tab. */
    override fun onPopupPrompt(session: GeckoSession, prompt: PromptDelegate.PopupPrompt): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        val site = panel.tabs.firstOrNull { it.session === session }?.url?.let { runCatching { java.net.URI(it).host }.getOrNull() } ?: "This page"
        panel.askFor(session, "popup", site) { allow ->
            if (!prompt.isComplete) result.complete(prompt.confirm(if (allow) AllowOrDeny.ALLOW else AllowOrDeny.DENY))
        }
        return result
    }

    private companion object {
        const val OK = "ok"
        const val CANCEL = "cancel"
        const val CLEAR = "clear"
    }
}
