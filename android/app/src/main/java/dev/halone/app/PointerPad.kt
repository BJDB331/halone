package dev.halone.app

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.halone.app.pointer.PadGestures

/**
 * The phone screen as a click surface while pointing.
 *
 * In stereo the glasses show the panel, not the phone, so the wearer is
 * touching a screen they cannot see: the pad itself is one large hit area,
 * and the few buttons sit in a strip along the top edge, out of the way.
 *
 * The gestures themselves are [PadGestures].
 */
class PointerPad(
    private val activity: Activity,
    listener: PadGestures.Listener,
    private val actions: Actions,
) {
    interface Actions {
        fun recenterView()
        fun recenterPointer()
        fun screenshot()
        fun toggleRecording()
        fun nextPointerMode()
        fun toggleFollow()
    }

    private var view: View? = null
    private val gestures = PadGestures(listener)
    private val density = activity.resources.displayMetrics.density

    private var mode = "phone"
    private var recording = false
    private var following = true

    private var modeIcon: TextView? = null
    private var modeLabel: TextView? = null
    private var recenterPointer: View? = null
    private var record: TextView? = null
    private var switchMode: View? = null
    private var follow: TextView? = null
    private var recordLabel: TextView? = null
    private var followLabel: TextView? = null
    private var modeHint: TextView? = null
    private var stats: TextView? = null

    /** Everything the pad shows, hidden together while the phone screen is off. */
    private val visibleParts = mutableListOf<View>()
    private var wakeStrip: View? = null
    private var screenOff = false
    private var lastStats = ""

    /** Whether the bottom line shows display diagnostics: a developer setting, off by default. */
    var showStats: Boolean = false
        set(value) {
            field = value
            activity.runOnUiThread { applyStats() }
        }

    /** When set, the pad is a touchpad and every touch goes here instead. */
    @Volatile
    var touchpad: ((MotionEvent) -> Unit)? = null

    fun show() {
        activity.runOnUiThread {
            if (view != null) return@runOnUiThread
            val root = activity.findViewById<View>(android.R.id.content) as ViewGroup

            val icon = Symbols.view(activity, "", 120f, Color.WHITE).apply { alpha = 0.85f }
            val label = TextView(activity).apply {
                textSize = 14f
                setTextColor(Color.argb(0x99, 0xff, 0xff, 0xff))
                gravity = Gravity.CENTER
                letterSpacing = 0.08f
            }
            modeIcon = icon
            modeLabel = label
            val hint = TextView(activity).apply {
                textSize = 13f
                setTextColor(Color.argb(0x66, 0xff, 0xff, 0xff))
                gravity = Gravity.CENTER
            }
            modeHint = hint

            // Two labelled rows instead of seven bare icons: what the wearer
            // does to the view, then capture and the phone itself. Labels,
            // because the buttons are found by glancing down, not by memory.
            val viewRow = group(
                labelled("center_focus_strong", "Recenter", "Recenter view") { actions.recenterView() },
                labelled("my_location", "Pointer", "Recenter pointer") { actions.recenterPointer() }.also { recenterPointer = it },
                labelled("moving", "Follow", "World lock or follow") { actions.toggleFollow() }.also {
                    follow = it.getChildAt(0) as TextView
                    followLabel = it.getChildAt(1) as TextView
                },
                labelled("swap_horiz", "Mode", "Switch pointer mode") { actions.nextPointerMode() }.also { switchMode = it },
            )
            val captureRow = group(
                labelled("photo_camera", "Screenshot", "Screenshot") { actions.screenshot() },
                labelled("radio_button_checked", "Record", "Record") { actions.toggleRecording() }.also {
                    record = it.getChildAt(0) as TextView
                    recordLabel = it.getChildAt(1) as TextView
                },
                labelled("brightness_6", "Screen off", "Turn the phone screen off") { setScreenOff(true) },
            )
            val bar = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(viewRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(captureRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            }

            val pad = object : FrameLayout(activity) {
                private val tick = Runnable { decide() }

                override fun onTouchEvent(event: MotionEvent): Boolean {
                    touchpad?.let {
                        it(event)
                        return true
                    }
                    val at = event.eventTime
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            gestures.down(at, event.x / density, event.y / density)
                        }
                        MotionEvent.ACTION_MOVE -> gestures.move(event.x / density, event.y / density)
                        MotionEvent.ACTION_POINTER_DOWN -> gestures.fingerAdded(at, event.pointerCount)
                        MotionEvent.ACTION_POINTER_UP -> gestures.fingerLifted()
                        MotionEvent.ACTION_UP -> gestures.up(at)
                        MotionEvent.ACTION_CANCEL -> gestures.cancel()
                    }
                    schedule()
                    return true
                }

                private fun decide() {
                    gestures.tick(SystemClock.uptimeMillis())
                    schedule()
                }

                // A hold must fire without lifting: the wearer cannot see the
                // screen, so waiting for a release to decide feels unresponsive.
                private fun schedule() {
                    removeCallbacks(tick)
                    val deadline = gestures.nextDeadline() ?: return
                    postDelayed(tick, (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0L))
                }
            }.apply {
                setBackgroundColor(Color.BLACK)
                isClickable = true
                addView(Grid(activity).also { visibleParts += it }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                val centre = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    addView(icon)
                    addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
                    addView(hint, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
                }
                addView(centre, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
                addView(bar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))
                visibleParts += centre
                visibleParts += bar
                // While the screen is off, a tap along the top edge is the way
                // back; everywhere else stays a working pad, in the dark.
                wakeStrip = View(activity).apply {
                    visibility = View.GONE
                    isClickable = true
                    contentDescription = "Turn the phone screen back on"
                    setOnClickListener { setScreenOff(false) }
                }.also {
                    addView(it, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(160), Gravity.TOP))
                }
                stats = TextView(activity).apply {
                    textSize = 12f
                    setTextColor(Color.argb(0x66, 0xff, 0xff, 0xff))
                    gravity = Gravity.CENTER
                    setPadding(0, dp(12), 0, dp(12))
                }.also {
                    addView(it, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { bottomMargin = dp(12) })
                }
                ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
                    val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                    bar.setPadding(bars.left + dp(8), bars.top + dp(8), bars.right + dp(8), dp(8))
                    insets
                }
            }

            root.addView(pad, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            pad.bringToFront()
            ViewCompat.requestApplyInsets(pad)
            view = pad
            applyStats()
            refresh()
        }
    }

    /** [mode] is the input setting's, or `mouse` while a connected mouse or trackpad drives the pointer. */
    fun setMode(mode: String) = activity.runOnUiThread {
        this.mode = mode
        refresh()
    }

    fun setRecording(recording: Boolean) = activity.runOnUiThread {
        this.recording = recording
        refresh()
    }

    /** Not one of [visibleParts]: it has its own setting, and stays dark with the screen. */
    private fun applyStats() {
        stats?.text = lastStats
        stats?.visibility = if (showStats && !screenOff) View.VISIBLE else View.GONE
    }

    /** A line of display diagnostics along the bottom: refresh rate and frames drawn. */
    fun setStats(text: String) = activity.runOnUiThread {
        lastStats = text
        if (showStats) stats?.text = text
    }

    fun setFollowing(following: Boolean) = activity.runOnUiThread {
        this.following = following
        refresh()
    }

    private fun refresh() {
        val (symbol, name) = when (mode) {
            "phone" -> "screen_rotation_alt" to "Gyro Mode"
            "touchpad" -> "trackpad_input" to "Trackpad Mode"
            "mouse" -> "mouse" to "Mouse Mode"
            else -> "eye_tracking" to "Gaze Mode"
        }
        modeIcon?.text = symbol
        modeLabel?.text = name
        modeHint?.text = when (mode) {
            "phone" -> "Aim the phone · tap anywhere to click"
            "touchpad" -> "Swipe to move · tap to click · two fingers to scroll"
            "mouse" -> "Using the connected mouse"
            else -> "Look to point · tap anywhere to click"
        }
        recenterPointer?.visibility = if (mode == "phone") View.VISIBLE else View.GONE
        switchMode?.isEnabled = mode != "mouse"
        switchMode?.alpha = if (mode != "mouse") 1f else 0.3f
        record?.text = if (recording) "stop_circle" else "radio_button_checked"
        record?.setTextColor(if (recording) RECORDING else Color.WHITE)
        record?.contentDescription = if (recording) "Stop recording" else "Record"
        recordLabel?.text = if (recording) "Stop" else "Record"
        recordLabel?.setTextColor(if (recording) RECORDING else LABEL)
        follow?.text = if (following) "moving" else "keep"
        follow?.contentDescription = if (following) "Following: tap to lock windows in place" else "World-locked: tap to have windows follow"
        followLabel?.text = if (following) "Following" else "Locked"
    }

    /** A rounded strip holding a row of buttons, sharing the width evenly. */
    private fun group(vararg buttons: View) = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(4), dp(4), dp(4), dp(4))
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(GROUP)
        }
        buttons.forEach { addView(it, LinearLayout.LayoutParams(0, dp(60), 1f)) }
    }

    /** An icon over its name; child 0 is the icon, child 1 the name, so callers can change either. */
    private fun labelled(symbol: String, name: String, description: String, onClick: () -> Unit) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        contentDescription = description
        isClickable = true
        isFocusable = true
        background = RippleDrawable(
            ColorStateList.valueOf(PRESSED),
            null,
            GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.WHITE)
            },
        )
        setOnClickListener { onClick() }
        addView(Symbols.view(activity, symbol, 22f, Color.WHITE))
        addView(TextView(activity).apply {
            text = name
            textSize = 11f
            setTextColor(LABEL)
            gravity = Gravity.CENTER
            maxLines = 1
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2) })
    }

    /**
     * The phone screen "off" while the glasses stay on: every pixel black
     * and the backlight as low as the window may set it. On an OLED phone
     * (OnePlus 12) black pixels are unlit, so the screen is dark and draws
     * next to nothing — but the phone is awake, so the glasses' display, the
     * tracking and the pad all keep working. Really sleeping the phone would
     * lock it and take the glasses' picture with it.
     *
     * The pad still takes gestures in the dark; a tap along the top edge,
     * where the buttons were, or either volume button turns the screen back
     * on. With the Android apps helper running, [onScreenOff] powers the
     * panel itself off as well.
     */
    /** Hears every switch, so the panel itself can be powered off where that is possible. */
    var onScreenOff: ((Boolean) -> Unit)? = null

    val isScreenOff: Boolean get() = screenOff

    fun setScreenOff(off: Boolean) = activity.runOnUiThread {
        if (off == screenOff) return@runOnUiThread
        screenOff = off
        onScreenOff?.invoke(off)
        visibleParts.forEach { it.visibility = if (off) View.INVISIBLE else View.VISIBLE }
        applyStats()
        wakeStrip?.visibility = if (off) View.VISIBLE else View.GONE
        activity.window.attributes = activity.window.attributes.apply {
            screenBrightness =
                if (off) android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
                else android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        tick()
    }

    /** A long-press tick in the hand holding the phone, for something the wearer cannot see start. */
    fun tick() {
        view?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
    }

    fun hide() {
        setScreenOff(false)
        activity.runOnUiThread {
            visibleParts.clear()
            wakeStrip = null
            view?.let { (it.parent as? ViewGroup)?.removeView(it) }
            view = null
            modeIcon = null
            modeLabel = null
            recenterPointer = null
            record = null
            recordLabel = null
            followLabel = null
            modeHint = null
            switchMode = null
            follow = null
        }
    }

    private fun dp(value: Int) = (value * density).toInt()

    /** Barely there: enough to tell the screen is a surface, not enough to light the room. */
    private class Grid(activity: Activity) : View(activity) {
        private val spacing = 32 * activity.resources.displayMetrics.density
        private val paint = Paint().apply {
            color = Color.argb(0x0e, 0xff, 0xff, 0xff)
            strokeWidth = activity.resources.displayMetrics.density
        }

        override fun onDraw(canvas: Canvas) {
            var x = (width % spacing) / 2
            while (x < width) {
                canvas.drawLine(x, 0f, x, height.toFloat(), paint)
                x += spacing
            }
            var y = (height % spacing) / 2
            while (y < height) {
                canvas.drawLine(0f, y, width.toFloat(), y, paint)
                y += spacing
            }
        }
    }

    private companion object {
        val PRESSED = Color.argb(0x30, 0x5c, 0xc8, 0xff)
        val BUTTON = Color.argb(0x26, 0xff, 0xff, 0xff)
        val RECORDING = Color.rgb(0xff, 0x6b, 0x6b)
        val GROUP = Color.argb(0x1a, 0xff, 0xff, 0xff)
        val LABEL = Color.argb(0xb3, 0xff, 0xff, 0xff)
    }
}
