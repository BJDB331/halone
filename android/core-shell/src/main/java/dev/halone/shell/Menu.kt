package dev.halone.shell

import org.json.JSONArray
import org.json.JSONObject

/** One row of a context menu, or a separator line when [id] is null. */
data class MenuItem(
    val id: String?,
    val label: String = "",
    val disabled: Boolean = false,
    val danger: Boolean = false,
    val checked: Boolean = false,
) {
    val separator: Boolean get() = id == null

    fun toJson(): JSONObject =
        if (separator) JSONObject().put("separator", true)
        else JSONObject().put("id", id).put("label", label).put("disabled", disabled).put("danger", danger).put("checked", checked)

    companion object {
        val SEPARATOR = MenuItem(null)

        fun parse(json: JSONObject): MenuItem {
            if (json.optBoolean("separator")) return SEPARATOR
            val id = json.opt("id") as? String ?: throw HaloneException("invalid-params", "a menu item needs an id")
            val label = json.opt("label") as? String ?: throw HaloneException("invalid-params", "a menu item needs a label")
            return MenuItem(id, label.take(MenuLayout.MAX_LABEL), json.optBoolean("disabled"), json.optBoolean("danger"), json.optBoolean("checked"))
        }

        fun parseList(array: JSONArray?): List<MenuItem> {
            array ?: throw HaloneException("invalid-params", "items must be a list")
            val items = (0 until array.length()).map { parse(array.optJSONObject(it) ?: JSONObject()) }
                .dropWhile { it.separator }
                .dropLastWhile { it.separator }
            if (items.none { !it.separator }) throw HaloneException("invalid-params", "a menu needs at least one item")
            if (items.size > MenuLayout.MAX_ITEMS) throw HaloneException("invalid-params", "a menu has at most ${MenuLayout.MAX_ITEMS} items")
            return items
        }
    }
}

class MenuRequest(
    val menuId: Int,
    val items: List<MenuItem>,
    /** The window or overlay it is shown over, and where on it. */
    val sourceId: String,
    val xPx: Float,
    val yPx: Float,
    /** A dialog instead of a menu: the protocol's `DialogSpec`. */
    val dialog: JSONObject? = null,
    /** The item or button chosen, or null, with what a dialog's fields held. */
    val onChosen: (String?, JSONObject) -> Unit,
) {
    /** A dialog stays up when the wearer presses elsewhere; a menu goes. */
    val modal: Boolean get() = dialog != null

    fun accepts(itemId: String): Boolean {
        val buttons = dialog?.optJSONArray("buttons") ?: return items.any { it.id == itemId && !it.disabled }
        return (0 until buttons.length()).any { buttons.optJSONObject(it)?.optString("id") == itemId }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("menuId", menuId)
        .put("items", JSONArray(items.map { it.toJson() }))
        .put("dialog", dialog ?: JSONObject.NULL)
}

/**
 * The menu overlay's size for a list of items. The menu page lays its rows out
 * to the same measures (`web/apps/system/src/menu`), and the simulator keeps a
 * copy in `menuSize`, so the panel is the right size before the page draws.
 */
object MenuLayout {
    const val ROW_PX = 48
    const val SEPARATOR_PX = 17
    const val PADDING_PX = 8
    const val MIN_WIDTH_PX = 240
    const val MAX_WIDTH_PX = 480
    const val MAX_ITEMS = 500

    /** Past this a menu scrolls, as a long dropdown does. */
    const val MAX_HEIGHT_PX = 560
    const val MAX_LABEL = 60

    /** An average character at the menu's 20px type, plus room for the row's padding. */
    private const val CHAR_PX = 10.5f
    private const val ROW_PADDING_PX = 56

    fun size(items: List<MenuItem>): Pair<Int, Int> {
        val longest = items.maxOfOrNull { it.label.length } ?: 0
        val width = (longest * CHAR_PX + ROW_PADDING_PX).toInt().coerceIn(MIN_WIDTH_PX, MAX_WIDTH_PX)
        val height = 2 * PADDING_PX + items.sumOf { if (it.separator) SEPARATOR_PX else ROW_PX }
        return width to height.coerceAtMost(MAX_HEIGHT_PX)
    }
}

/**
 * A dialog's size in the menu overlay, which lays it out to the same measures:
 * a 600px card of 22px type, its message wrapped and scrolled past a height.
 */
object DialogLayout {
    const val WIDTH_PX = 600
    const val FILES_WIDTH_PX = 960
    const val FILES_HEIGHT_PX = 680
    const val PADDING_PX = 32
    const val TITLE_PX = 52
    const val LINE_PX = 31
    const val MAX_MESSAGE_PX = 320
    const val FIELD_PX = 76
    const val BUTTONS_PX = 88
    const val CHOICE_PX = 52
    const val MAX_HEIGHT_PX = 640

    /** The pickers: a six-week calendar, a year of months, time steppers, and a palette with sliders. */
    const val CALENDAR_HEIGHT_PX = 620
    const val MONTH_HEIGHT_PX = 460
    const val TIME_HEIGHT_PX = 400
    const val COLOR_HEIGHT_PX = 580

    private const val CHAR_PX = 11.5f

    fun size(dialog: JSONObject): Pair<Int, Int> {
        val kind = dialog.optString("kind")
        when (kind) {
            "files" -> return FILES_WIDTH_PX to FILES_HEIGHT_PX
            "color" -> return WIDTH_PX to COLOR_HEIGHT_PX
            "datetime" -> return when (dialog.optString("inputType")) {
                "time" -> WIDTH_PX to TIME_HEIGHT_PX
                "month" -> WIDTH_PX to MONTH_HEIGHT_PX
                "datetime-local" -> FILES_WIDTH_PX to CALENDAR_HEIGHT_PX
                else -> WIDTH_PX to CALENDAR_HEIGHT_PX
            }
        }
        val title = if (dialog.optString("title").isBlank()) 0 else TITLE_PX
        val perLine = ((WIDTH_PX - 2 * PADDING_PX) / CHAR_PX).toInt()
        val message = dialog.optString("message")
        val lines = if (message.isEmpty()) 0 else message.split('\n').sumOf { maxOf(1, (it.length + perLine - 1) / perLine) }
        val body = when (kind) {
            "text" -> FIELD_PX
            "login" -> FIELD_PX * if (dialog.isNull("username")) 1 else 2
            "choices" -> (dialog.optJSONArray("choices")?.length() ?: 0) * CHOICE_PX
            else -> 0
        }
        val height = 2 * PADDING_PX + title + minOf(lines * LINE_PX, MAX_MESSAGE_PX) + body + BUTTONS_PX
        return WIDTH_PX to height.coerceAtMost(MAX_HEIGHT_PX)
    }
}
