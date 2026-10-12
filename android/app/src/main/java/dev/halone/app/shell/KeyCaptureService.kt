package dev.halone.app.shell

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Takes keys before Android's own shortcuts do, which no ordinary app can:
 * Alt+Tab, Meta and PrintScreen reach Halone instead of opening the app
 * switcher, DeX or a phone screenshot. The wearer turns it on in Android's
 * accessibility settings; while no environment is running, [router] is null
 * and every key goes on as usual.
 */
class KeyCaptureService : AccessibilityService() {
    override fun onServiceConnected() {
        running = true
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = router?.invoke(event) ?: false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    companion object {
        /** Whether the wearer has turned the service on. */
        @Volatile
        var running = false
            private set

        /** Decides each key while an environment runs: true takes it from Android and the app alike. Main thread. */
        @Volatile
        var router: ((KeyEvent) -> Boolean)? = null
    }
}
