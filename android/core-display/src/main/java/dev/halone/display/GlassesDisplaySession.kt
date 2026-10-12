package dev.halone.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import dev.halone.glasses.api.DisplayMode
import dev.halone.glasses.api.GlassesDevice
import dev.halone.glasses.api.preferredMonoMode
import dev.halone.glasses.api.preferredStereoMode
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Gets the glasses into the display mode we want, and hands back the `Display`
 * to render on.
 *
 * The awkward part is not the rendering, it is the ordering. The HID mode
 * command is the real mode selector — the glasses only advertise 3840x1080 once
 * they are in SBS — and switching makes the host **destroy and re-add the
 * display with a new id**. So the sequence has to be: write the mode over HID,
 * wait for the new display to arrive, and only then build anything against it.
 * Anything holding the old `Display` is stale.
 */
class GlassesDisplaySession(
    context: Context,
    private val log: (String) -> Unit,
) {
    private val displayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    /** Switches the glasses to stereo, waiting out the display churn it causes. */
    suspend fun enterStereo(device: GlassesDevice, mode: DisplayMode? = null): Display? {
        val control = device.display
        if (control == null) {
            log("  this device has no display control; cannot switch to stereo")
            return null
        }

        val stereo = mode ?: control.preferredStereoMode()
        if (stereo == null) {
            log("  this device advertises no stereo mode")
            return null
        }

        val current = control.currentMode().getOrNull()
        if (current?.key == stereo.key) {
            log("  already in ${current.key}; reusing the current display")
            return findGlassesDisplay(device)
        }

        log("  switching to $stereo (display will be recreated)")
        val before = findGlassesDisplay(device)?.displayId

        control.setMode(stereo).onFailure {
            log("  mode switch failed: ${it.message}")
            return null
        }

        val added = awaitNewPresentationDisplay(before)
        if (added == null) {
            log("  no new display arrived after the mode switch")
            return null
        }
        log("  new display ${added.displayId} \"${added.name}\", mode " +
            "${added.mode.physicalWidth}x${added.mode.physicalHeight}")
        return added
    }

    /** Returns the glasses to a mono mode so the phone is usable again. */
    fun leaveStereo(device: GlassesDevice) {
        val control = device.display ?: return
        val mono = control.preferredMonoMode() ?: return
        log("  restoring $mono: ${control.setMode(mono)}")
    }

    fun findGlassesDisplay(device: GlassesDevice): Display? {
        val candidates = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        return candidates.firstOrNull { display ->
            device.displayNameHints.any { display.name.contains(it, ignoreCase = true) }
        } ?: candidates.firstOrNull()
    }

    /**
     * Waits for a presentation display whose id differs from the one we had.
     * The mode switch tears the old one down, so watching for *any* add would
     * race with the removal of the display we were just using.
     */
    private suspend fun awaitNewPresentationDisplay(previousId: Int?): Display? =
        suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            var settled = false

            val listener = object : DisplayManager.DisplayListener {
                private fun consider(displayId: Int, reason: String) {
                    if (settled || displayId == previousId) return
                    val display = displayManager.getDisplay(displayId) ?: return
                    if (display.flags and Display.FLAG_PRESENTATION == 0) return
                    settled = true
                    displayManager.unregisterDisplayListener(this)
                    log("  display $displayId $reason")
                    continuation.resume(display)
                }

                override fun onDisplayAdded(displayId: Int) = consider(displayId, "added")
                override fun onDisplayChanged(displayId: Int) = consider(displayId, "changed")
                override fun onDisplayRemoved(displayId: Int) = Unit
            }

            displayManager.registerDisplayListener(listener, handler)
            continuation.invokeOnCancellation {
                displayManager.unregisterDisplayListener(listener)
            }

            handler.postDelayed({
                if (settled) return@postDelayed
                settled = true
                displayManager.unregisterDisplayListener(listener)
                // The glasses may have re-appeared before the listener existed.
                continuation.resume(
                    displayManager
                        .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
                        .firstOrNull { it.displayId != previousId }
                )
            }, DISPLAY_SETTLE_MS)
        }

    private companion object {
        const val DISPLAY_SETTLE_MS = 4_000L
    }
}
