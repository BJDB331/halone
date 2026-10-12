package dev.halone.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

object DisplayProbe {
    fun report(context: Context): String {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val all = dm.displays
        val presentation = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)

        return buildString {
            appendLine("displays=${all.size} presentation-capable=${presentation.size}")
            appendLine()
            for (d in all) {
                val isPresentation = presentation.any { it.displayId == d.displayId }
                appendLine("display ${d.displayId}: \"${d.name}\"")
                appendLine("  state=${stateName(d.state)} valid=${d.isValid} presentation=$isPresentation")
                appendLine("  flags=${flagList(d.flags)}")
                d.mode.let { appendLine("  active mode: id=${it.modeId} ${it.physicalWidth}x${it.physicalHeight} @${"%.2f".format(it.refreshRate)}") }
                appendLine("  supported modes:")
                for (m in d.supportedModes) {
                    val marker = if (m.modeId == d.mode.modeId) " <- active" else ""
                    val sbs = if (m.physicalWidth >= 2 * m.physicalHeight) "  [SBS candidate]" else ""
                    appendLine("    id=${m.modeId} ${m.physicalWidth}x${m.physicalHeight} @${"%.2f".format(m.refreshRate)}$sbs$marker")
                }
                appendLine()
            }
            if (presentation.isEmpty()) {
                appendLine("No external display. Connect the glasses and re-run.")
                appendLine("On Samsung devices, dismiss/disable DeX so the display stays a")
                appendLine("plain presentation display rather than a desktop session.")
            }
        }
    }

    fun externalDisplay(context: Context): Display? {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()
            ?: dm.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
    }

    /** Widest mode whose aspect ratio looks like two 16:9 panels glued together. */
    fun sbsMode(display: Display): Display.Mode? =
        display.supportedModes
            .filter { it.physicalWidth >= 2 * it.physicalHeight }
            .maxByOrNull { it.physicalWidth.toLong() * it.physicalHeight + it.refreshRate.toLong() }

    private fun stateName(state: Int) = when (state) {
        Display.STATE_OFF -> "OFF"
        Display.STATE_ON -> "ON"
        Display.STATE_DOZE -> "DOZE"
        Display.STATE_DOZE_SUSPEND -> "DOZE_SUSPEND"
        Display.STATE_ON_SUSPEND -> "ON_SUSPEND"
        else -> "$state"
    }

    private fun flagList(flags: Int): String {
        val names = mutableListOf<String>()
        if (flags and Display.FLAG_PRESENTATION != 0) names += "PRESENTATION"
        if (flags and Display.FLAG_PRIVATE != 0) names += "PRIVATE"
        if (flags and Display.FLAG_SECURE != 0) names += "SECURE"
        if (flags and Display.FLAG_SUPPORTS_PROTECTED_BUFFERS != 0) names += "PROTECTED_BUFFERS"
        if (flags and Display.FLAG_ROUND != 0) names += "ROUND"
        return if (names.isEmpty()) "none" else names.joinToString("|")
    }
}
