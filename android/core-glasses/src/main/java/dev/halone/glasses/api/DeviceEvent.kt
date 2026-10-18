package dev.halone.glasses.api

/** Out-of-band things the glasses tell us about, unprompted. */
sealed interface DeviceEvent {
    /**
     * The user changed the display mode with the physical buttons, so our idea
     * of the current mode is stale.
     */
    data class DisplayModeChanged(val mode: DisplayMode?) : DeviceEvent

    data class ButtonPressed(val button: Button) : DeviceEvent

    data class BrightnessChanged(val level: Int) : DeviceEvent

    data class ScreenPower(val on: Boolean) : DeviceEvent

    /**
     * The glasses took over world-locking, or gave it back — see
     * [OnboardPinning]. The host must draw head-locked while [enabled].
     */
    data class OnboardPinningChanged(val enabled: Boolean) : DeviceEvent

    /** The device went away — unplugged, slept, or stopped responding. */
    data class Disconnected(val reason: String) : DeviceEvent

    /**
     * The glasses would not give up their own calibration, so a bundled one is
     * standing in. Worth surfacing rather than logging quietly: every per-unit
     * number downstream — IPD, bias, principal point — now belongs to a
     * different pair of glasses.
     */
    data class CalibrationFallback(val reason: String) : DeviceEvent

    enum class Button { POWER, BRIGHTNESS_UP, BRIGHTNESS_DOWN, VOLUME_UP, VOLUME_DOWN, UNKNOWN }
}

fun interface DeviceEventListener {
    fun onEvent(event: DeviceEvent)
}
