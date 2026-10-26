package dev.halone.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Where the glasses are on the way to a running environment, as the home screen tells it. */
enum class GlassesStatus(val title: String, val dimmed: Boolean) {
    DISCONNECTED("Glasses Disconnected", true),
    AWAITING_PERMISSION("Awaiting Permission", true),
    NO_VIDEO_OUTPUT("Video Output Not Supported", true),
    LINK_OFF("Network Connection Off", true),
    READY("Glasses Ready", false),
    STARTING("Starting…", false),
    RUNNING("Environment Running", false),
}

/** One line of the home screen's status card: what it is, how it stands, and what a tap on it does. */
data class StatusRow(
    val symbol: String,
    val label: String,
    val value: String,
    /** True all is well, false something needs doing, null just information. */
    val good: Boolean?,
    val onClick: (() -> Unit)? = null,
)

/**
 * What the phone shows when it is not a pointer pad: the glasses, whether
 * they are ready, and the button that starts the environment on them.
 */
class HomeScreen(
    context: Context,
    private val onPrimary: (GlassesStatus) -> Unit,
    onSettings: () -> Unit,
    onShareReport: () -> Unit,
    onDismissReport: () -> Unit,
) {
    private val density = context.resources.displayMetrics.density
    private var status = GlassesStatus.DISCONNECTED

    private val icon = Symbols.view(context, "eyeglasses", 120f, Color.WHITE)

    private val title = TextView(context).apply {
        textSize = 28f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
    }

    private val detail = TextView(context).apply {
        textSize = 16f
        setTextColor(MUTED)
        gravity = Gravity.CENTER
    }

    /** A second, smaller action under the detail, for a fix the detail suggests. */
    private val secondary = TextView(context).apply {
        textSize = 16f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(ACCENT)
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        visibility = View.GONE
    }

    private val primary = TextView(context).apply {
        textSize = 20f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        setOnClickListener { onPrimary(status) }
    }

    private val settings = Symbols.view(context, "settings", 28f, Color.WHITE).apply {
        contentDescription = "Settings"
        isClickable = true
        isFocusable = true
        background = RippleDrawable(ColorStateList.valueOf(PRESSED), null, GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) })
        setOnClickListener { onSettings() }
    }

    /**
     * The glasses, their display and Android apps at a glance, so it is
     * clear everything is set before putting the glasses on. A row that
     * needs something done says so, and opens where to do it.
     */
    private val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4), dp(4), dp(4), dp(4))
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(CARD)
        }
        visibility = View.GONE
    }

    fun renderDetails(rows: List<StatusRow>) {
        card.removeAllViews()
        card.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
        rows.forEachIndexed { index, row ->
            if (index > 0) card.addView(View(card.context).apply { setBackgroundColor(DIVIDER) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { leftMargin = dp(52); rightMargin = dp(12) })
            card.addView(statusRow(row))
        }
    }

    private fun statusRow(row: StatusRow): View = LinearLayout(card.context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        row.onClick?.let { click ->
            isClickable = true
            isFocusable = true
            background = RippleDrawable(ColorStateList.valueOf(PRESSED), null, GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.WHITE)
            })
            setOnClickListener { click() }
        }
        val tint = when (row.good) {
            true -> GOOD
            false -> WARN
            null -> MUTED
        }
        addView(Symbols.view(context, row.symbol, 22f, tint), LinearLayout.LayoutParams(dp(28), dp(28)))
        addView(TextView(context).apply {
            text = row.label
            textSize = 15f
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(12) })
        addView(TextView(context).apply {
            text = row.value
            textSize = 14f
            setTextColor(if (row.good == false) WARN else MUTED)
            gravity = Gravity.END
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) })
        if (row.onClick != null) addView(Symbols.view(context, "chevron_right", 20f, FAINT), LinearLayout.LayoutParams(dp(24), dp(24)))
    }

    /** Shown after a crash, until shared or dismissed. */
    private val crashRow = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        visibility = View.GONE
        addView(TextView(context).apply {
            text = "Halone stopped unexpectedly last time."
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(link(context, "Share Report", onShareReport))
            addView(link(context, "Dismiss", onDismissReport))
        })
    }

    private fun link(context: Context, label: String, onClick: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 16f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(ACCENT)
        setPadding(dp(16), dp(10), dp(16), dp(10))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    fun showCrashReport(visible: Boolean) {
        crashRow.visibility = if (visible) View.VISIBLE else View.GONE
    }

    val view: View = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(icon, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(title, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(20) })
            addView(detail, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            addView(secondary, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
            addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(24) })
        }
        addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
            leftMargin = dp(24)
            rightMargin = dp(24)
            bottomMargin = dp(80)
        })
        addView(settings, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.TOP or Gravity.END))
        addView(primary, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(64), Gravity.BOTTOM))
        addView(crashRow, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        val version = TextView(context).apply {
            text = "Halone ${BuildConfig.VERSION_NAME}"
            textSize = 12f
            setTextColor(FAINT)
            gravity = Gravity.CENTER
        }
        addView(version, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            (settings.layoutParams as FrameLayout.LayoutParams).apply {
                topMargin = bars.top + dp(12)
                rightMargin = bars.right + dp(12)
            }
            (primary.layoutParams as FrameLayout.LayoutParams).apply {
                leftMargin = bars.left + dp(24)
                rightMargin = bars.right + dp(24)
                bottomMargin = bars.bottom + dp(48)
            }
            (version.layoutParams as FrameLayout.LayoutParams).bottomMargin = bars.bottom + dp(16)
            (crashRow.layoutParams as FrameLayout.LayoutParams).apply {
                leftMargin = bars.left + dp(24)
                rightMargin = bars.right + dp(24)
                bottomMargin = bars.bottom + dp(48 + 64 + 12)
            }
            requestLayout()
            insets
        }
    }

    /**
     * [help] is the device's own explanation for [GlassesStatus.LINK_OFF];
     * [notice] is a hint about what just went wrong, shown under the detail.
     */
    fun render(
        status: GlassesStatus,
        model: String?,
        help: String? = null,
        notice: String? = null,
        action: Pair<String, () -> Unit>? = null,
    ) {
        secondary.visibility = if (action != null) View.VISIBLE else View.GONE
        secondary.text = action?.first
        secondary.setPadding(dp(16), dp(10), dp(16), dp(10))
        secondary.setOnClickListener { action?.second?.invoke() }
        this.status = status
        icon.alpha = if (status.dimmed) 0.3f else 1f
        title.text = status.title
        val text = when (status) {
            GlassesStatus.DISCONNECTED -> "Connect your glasses to the phone's USB-C port"
            GlassesStatus.AWAITING_PERMISSION -> listOfNotNull(model, "Allow Halone to use them").joinToString("\n")
            GlassesStatus.NO_VIDEO_OUTPUT -> "The glasses are connected, but this phone's USB-C port does not send video " +
                "(DisplayPort Alt Mode), so it cannot drive them. Halone needs a phone that can show its screen on a USB-C display."
            GlassesStatus.LINK_OFF -> listOfNotNull(model, help).joinToString("\n")
            else -> model.orEmpty()
        }
        detail.text = listOfNotNull(text.takeIf { it.isNotEmpty() }, notice).joinToString("\n\n")
        val (label, enabled) = when (status) {
            GlassesStatus.DISCONNECTED -> "Start Environment" to false
            GlassesStatus.AWAITING_PERMISSION -> "Allow Access" to true
            GlassesStatus.NO_VIDEO_OUTPUT -> "Start Environment" to false
            GlassesStatus.LINK_OFF -> "Turn It On" to true
            GlassesStatus.READY -> "Start Environment" to true
            GlassesStatus.STARTING -> "Starting…" to false
            GlassesStatus.RUNNING -> "Stop Environment" to true
        }
        primary.text = label
        primary.isEnabled = enabled
        val fill = when {
            !enabled -> DISABLED
            status == GlassesStatus.RUNNING -> STOP
            else -> ACCENT
        }
        primary.setTextColor(if (enabled && status != GlassesStatus.RUNNING) Color.BLACK else Color.WHITE)
        primary.background = RippleDrawable(
            ColorStateList.valueOf(PRESSED),
            GradientDrawable().apply {
                cornerRadius = dp(32).toFloat()
                setColor(fill)
            },
            null,
        )
    }

    private fun dp(value: Int) = (value * density).toInt()

    private companion object {
        val ACCENT = Color.rgb(0x5c, 0xc8, 0xff)
        val STOP = Color.rgb(0x5a, 0x1f, 0x24)
        val DISABLED = Color.rgb(0x1d, 0x25, 0x33)
        val MUTED = Color.rgb(0x8b, 0x98, 0xab)
        val FAINT = Color.rgb(0x4a, 0x55, 0x66)
        val PRESSED = Color.argb(0x40, 0xff, 0xff, 0xff)
        val CARD = Color.rgb(0x10, 0x14, 0x1b)
        val DIVIDER = Color.rgb(0x1e, 0x25, 0x30)
        val GOOD = Color.rgb(0x6f, 0xd6, 0x9a)
        val WARN = Color.rgb(0xff, 0xb4, 0x5c)
    }
}
