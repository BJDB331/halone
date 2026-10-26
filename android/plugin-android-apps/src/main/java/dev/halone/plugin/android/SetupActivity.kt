package dev.halone.plugin.android

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.halone.plugin.android.adb.AdbSetup
import dev.halone.plugin.android.adb.PairingNotification
import rikka.shizuku.Shizuku
import rikka.sui.Sui

/**
 * On the phone: Android apps on the glasses, set up as a checklist in
 * Halone's own dark style. Each step says whether it is done and has the
 * one button that does it; the optional extras come after.
 */
class SetupActivity : Activity() {
    private class Step(val card: LinearLayout, val badge: TextView, val status: TextView, val action: TextView, val number: Int)

    private lateinit var summary: TextView
    private lateinit var wireless: Step
    private lateinit var pairing: Step
    private lateinit var start: Step
    private lateinit var bluetooth: Step
    private lateinit var shizuku: Step
    private lateinit var code: EditText
    private var bluetoothAsked = false
    private val adbChanged: () -> Unit = { runOnUiThread(::refresh) }
    private val helperChanged: () -> Unit = { runOnUiThread(::refresh) }

    private val binderReceived = Shizuku.OnBinderReceivedListener { runOnUiThread(::refresh) }
    private val binderDead = Shizuku.OnBinderDeadListener { runOnUiThread(::refresh) }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread(::refresh) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(32))
        }
        column.addView(text(getString(R.string.setup_heading), 28f, Color.WHITE, bold = true))
        column.addView(text(getString(R.string.setup_intro), 15f, MUTED).apply { setPadding(0, dp(8), 0, 0) })
        summary = text("", 15f, ACCENT, bold = true).apply { setPadding(0, dp(16), 0, dp(4)) }
        column.addView(summary)

        wireless = step(column, 1, R.string.setup_step_wireless_title, R.string.setup_step_wireless_body)
        wireless.action.setText(R.string.setup_adb_developer)
        wireless.action.setOnClickListener { openDeveloperOptions() }

        pairing = step(column, 2, R.string.setup_step_pair_title, R.string.setup_step_pair_body)
        pairing.action.setText(R.string.setup_adb_pair)
        pairing.action.setOnClickListener { startPairing() }
        code = EditText(this).apply {
            hint = getString(R.string.setup_adb_code_hint)
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            setHintTextColor(FAINT)
            backgroundTintList = ColorStateList.valueOf(FAINT)
        }
        val codeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(code, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(button(getString(R.string.setup_adb_pair_code), primary = false) {
                AdbSetup.pair(this@SetupActivity, code.text.toString().filter { it.isDigit() })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) })
        }
        pairing.card.addView(codeRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
            leftMargin = dp(44)
        })

        start = step(column, 3, R.string.setup_step_start_title, R.string.setup_step_start_body)
        start.action.setText(R.string.setup_adb_connect)
        start.action.setOnClickListener { AdbSetup.connect(this) }

        column.addView(text(getString(R.string.setup_optional), 13f, FAINT, bold = true).apply {
            letterSpacing = 0.1f
            setPadding(dp(4), dp(28), 0, dp(4))
        })
        bluetooth = step(column, 0, R.string.setup_bluetooth_title, R.string.setup_bluetooth_ask)
        shizuku = step(column, 0, R.string.setup_shizuku_title, R.string.setup_shizuku_missing)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            fitsSystemWindows = true
            addView(column)
        })

        AdbSetup.addListener(adbChanged)
        HelperBridge.addListener(helperChanged)
        Sui.init(packageName)
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        AdbSetup.removeListener(adbChanged)
        HelperBridge.removeListener(helperChanged)
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        refresh()
        if (requestCode == NOTIFICATION_REQUEST && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startPairing()
    }

    private fun refresh() {
        AdbSetup.refresh(this)
        val paired = AdbSetup.paired(this)
        val ready = HelperBridge.alive() || Privileged.missingAccess(this) == null
        val working = AdbSetup.phase == AdbSetup.Phase.WORKING

        summary.text = when {
            ready -> getString(R.string.setup_summary_ready)
            working -> AdbSetup.message
            AdbSetup.phase == AdbSetup.Phase.FAILED -> AdbSetup.message
            paired -> getString(R.string.setup_summary_paired)
            else -> getString(R.string.setup_summary_new)
        }
        summary.setTextColor(
            when {
                ready -> GOOD
                AdbSetup.phase == AdbSetup.Phase.FAILED -> WARN
                else -> ACCENT
            },
        )

        mark(wireless, done = paired || ready, status = null, actionShown = !paired && !ready)
        mark(pairing, done = paired, status = if (paired) getString(R.string.setup_step_pair_done) else null, actionShown = !paired && !working)
        (code.parent as View).visibility = if (paired) View.GONE else View.VISIBLE
        mark(
            start, done = ready,
            status = if (ready) getString(R.string.setup_step_start_done) else if (working) AdbSetup.message else null,
            actionShown = paired && !ready && !working,
        )

        val bluetoothState = when {
            hasBluetooth() -> getString(R.string.setup_bluetooth_ready)
            bluetoothAsked && !shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT) -> getString(R.string.setup_bluetooth_blocked)
            else -> getString(R.string.setup_bluetooth_ask)
        }
        mark(bluetooth, done = hasBluetooth(), status = bluetoothState, actionShown = !hasBluetooth())
        if (bluetoothAsked && !shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)) {
            bluetooth.action.setText(R.string.setup_open_settings)
            bluetooth.action.setOnClickListener {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            }
        } else {
            bluetooth.action.setText(R.string.setup_allow)
            bluetooth.action.setOnClickListener { askBluetooth() }
        }

        val (shizukuText, shizukuAction, onShizuku) = when (Privileged.missingAccess(this)) {
            Privileged.State.NO_SHIZUKU -> Triple<Int, Int, () -> Unit>(R.string.setup_shizuku_missing, 0) { }
            Privileged.State.NOT_RUNNING -> Triple(R.string.setup_shizuku_stopped, R.string.setup_open_shizuku) { openShizuku() }
            Privileged.State.NO_PERMISSION -> Triple(R.string.setup_shizuku_ask, R.string.setup_allow) { Privileged.requestPermission() }
            Privileged.State.DENIED -> Triple(R.string.setup_shizuku_denied, R.string.setup_open_shizuku) { openShizuku() }
            else -> Triple(R.string.setup_shizuku_ready, 0) { }
        }
        mark(shizuku, done = Privileged.missingAccess(this) == null, status = getString(shizukuText), actionShown = shizukuAction != 0)
        if (shizukuAction != 0) shizuku.action.setText(shizukuAction)
        shizuku.action.setOnClickListener { onShizuku() }
    }

    /** A step's badge (its number, or a tick when done), its status line and whether its button shows. */
    private fun mark(step: Step, done: Boolean, status: String?, actionShown: Boolean) {
        step.badge.text = if (done) "✓" else if (step.number > 0) step.number.toString() else "•"
        step.badge.setTextColor(if (done) Color.BLACK else Color.WHITE)
        (step.badge.background as GradientDrawable).setColor(if (done) GOOD else BADGE)
        step.status.visibility = if (status == null) View.GONE else View.VISIBLE
        step.status.text = status
        step.action.visibility = if (actionShown) View.VISIBLE else View.GONE
        step.card.alpha = if (done && step.number > 0) 0.75f else 1f
    }

    private fun step(parent: LinearLayout, number: Int, title: Int, body: Int): Step {
        val badge = text(if (number > 0) number.toString() else "•", 15f, Color.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(BADGE)
            }
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(getString(title), 17f, Color.WHITE, bold = true))
            addView(text(getString(body), 14f, MUTED).apply { setPadding(0, dp(4), 0, 0) })
        }
        val status = text("", 14f, GOOD).apply { setPadding(0, dp(6), 0, 0) }
        texts.addView(status)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(badge, LinearLayout.LayoutParams(dp(32), dp(32)))
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(12) })
        }
        val action = button("", primary = true) {}
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(CARD)
            }
            addView(header)
            addView(action, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
                leftMargin = dp(44)
            })
        }
        parent.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        return Step(card, badge, status, action, number)
    }

    private fun button(label: String, primary: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(if (primary) Color.BLACK else Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(dp(20), dp(10), dp(20), dp(10))
        isClickable = true
        isFocusable = true
        background = RippleDrawable(
            ColorStateList.valueOf(PRESSED),
            GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(if (primary) ACCENT else BADGE)
            },
            null,
        )
        setOnClickListener { onClick() }
    }

    /** Code entry in the notification shade, then Developer options, where Wireless debugging's pairing dialog is. */
    private fun startPairing() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
            return
        }
        PairingNotification.show(this)
        openDeveloperOptions()
    }

    private fun openDeveloperOptions() {
        runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
            .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    private fun askBluetooth() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        bluetoothAsked = true
        requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), BLUETOOTH_REQUEST)
    }

    private fun hasBluetooth() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun openShizuku() {
        packageManager.getLaunchIntentForPackage(Privileged.SHIZUKU_PACKAGE)?.let(::startActivity)
    }

    private fun text(value: String, sp: Float, colour: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(colour)
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val BLUETOOTH_REQUEST = 1
        const val NOTIFICATION_REQUEST = 2

        // Halone's phone palette, as on its home screen.
        val ACCENT = Color.rgb(0x5c, 0xc8, 0xff)
        val GOOD = Color.rgb(0x6f, 0xd6, 0x9a)
        val WARN = Color.rgb(0xff, 0xb4, 0x5c)
        val MUTED = Color.rgb(0x8b, 0x98, 0xab)
        val FAINT = Color.rgb(0x4a, 0x55, 0x66)
        val CARD = Color.rgb(0x10, 0x14, 0x1b)
        val BADGE = Color.rgb(0x1d, 0x25, 0x33)
        val PRESSED = Color.argb(0x40, 0xff, 0xff, 0xff)
    }
}
