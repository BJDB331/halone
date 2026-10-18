package dev.halone.app.stereo

import android.content.Context
import android.view.Display
import dev.halone.compositor.Panel
import dev.halone.compositor.PanelEngine
import dev.halone.compositor.PanelPose
import dev.halone.compositor.Scene
import dev.halone.compositor.SmoothFollow
import dev.halone.compositor.StereoCompositor
import dev.halone.display.GlassesDisplaySession
import dev.halone.display.RenderPresentation
import dev.halone.glasses.api.DisplayMode
import dev.halone.glasses.api.GlassesDevice
import dev.halone.glasses.api.Optics
import dev.halone.glasses.fusion.DisplayClock
import dev.halone.glasses.fusion.HeadTracker

/**
 * Wires the layers together: `core-display` gets the glasses into
 * stereo and hosts a render surface, `core-compositor` draws the scene, and an
 * engine paints each panel.
 *
 * There is deliberately almost nothing here. Everything this class used to do
 * itself now lives in a module that can be tested and reused; what remains is
 * the ordering between them.
 */
class GlassesStereoSession(
    private val context: Context,
    private val log: (String) -> Unit,
) {
    private val displays = GlassesDisplaySession(context, log)
    private val presentation = RenderPresentation(context, log)

    val scene = Scene()

    var compositor: StereoCompositor? = null
        private set

    private var optics: Optics? = null

    suspend fun enterStereo(device: GlassesDevice, mode: DisplayMode? = null): Display? =
        displays.enterStereo(device, mode)

    /** Builds the compositor and shows it. Must run on the UI thread. */
    fun show(
        display: Display,
        tracker: HeadTracker,
        optics: Optics,
        displayClock: DisplayClock? = null,
    ) {
        dismiss()
        this.optics = optics
        compositor = StereoCompositor(tracker, optics, scene, log).also {
            // The tracker already loaded it, and the two must agree: the head
            // frame the tracker publishes is defined by the same eye rotations
            // the compositor renders with.
            it.calibration = tracker.calibration
            it.displayClock = displayClock
            presentation.show(display, it)
        }
    }

    /**
     * Puts a panel in the scene. The compositor runs the engine handshake on its
     * next frame, so this is safe to call at any point after [show].
     */
    fun openPanel(
        id: String,
        engine: PanelEngine,
        follow: SmoothFollow = SmoothFollow(),
        verticalFill: Float = PANEL_VERTICAL_FILL,
        distanceMetres: Float? = null,
    ): Panel? {
        val panel = sizedPanel(id, engine, follow, verticalFill, distanceMetres) ?: return null
        scene.open(panel)
        log("  opened $panel at %.1f m".format(panel.pose.distanceMetres))
        return panel
    }

    /**
     * Gives the scene its dock and launcher, which sit outside the arrangement.
     * Sized like any panel; the scene places them every frame.
     */
    fun openOverlays(dock: Panel, launcher: Panel) {
        scene.setOverlays(dock, launcher)
        log("  opened overlays '${dock.id}' and '${launcher.id}'")
    }

    fun closeOverlays() {
        listOfNotNull(scene.dock, scene.launcher, scene.popup).forEach { compositor?.detach(it) ?: it.engine.close() }
        scene.setOverlays(null, null)
        scene.setPopupPanel(null)
    }

    /** A panel sized from the field of view, not in metres: distance is a comfort choice, angular size the layout one. */
    fun sizedPanel(
        id: String,
        engine: PanelEngine,
        follow: SmoothFollow = SmoothFollow(),
        verticalFill: Float = PANEL_VERTICAL_FILL,
        distanceMetres: Float? = null,
    ): Panel? {
        val compositor = compositor ?: run { log("  not in stereo"); return null }
        val distance = distanceMetres ?: optics?.virtualImageDistanceMetres ?: DEFAULT_PANEL_DISTANCE_M
        return Panel(id, engine, PanelPose(distanceMetres = distance), follow).apply {
            fitToFov(compositor.verticalFovDegrees, verticalFill)
        }
    }

    fun closePanel(panel: Panel) {
        scene.remove(panel)
        compositor?.detach(panel) ?: panel.engine.close()
        // A drift, not a jump: closing a panel is a layout change like any
        // other, and the panels left behind should be seen to take the room.
        scene.layOutCylindrical(immediate = false)
        log("  closed panel '${panel.id}'")
    }

    fun dismiss() {
        // The presentation goes first: it stops the render thread, so nothing is
        // mid-draw against a texture we are about to release.
        presentation.dismiss()
        compositor = null
        scene.panels.forEach {
            it.engine.close()
            it.detachTexture()
        }
        scene.clear()
    }

    /** Returns the glasses to a mono mode so the phone is usable again. */
    fun leaveStereo(device: GlassesDevice) = displays.leaveStereo(device)

    companion object {
        /** Leaves headroom at the edges, where these optics are least comfortable. */
        const val PANEL_VERTICAL_FILL = 0.75f
        private const val DEFAULT_PANEL_DISTANCE_M = 4f
    }
}
