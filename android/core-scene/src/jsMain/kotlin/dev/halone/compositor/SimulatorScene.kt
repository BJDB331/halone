@file:OptIn(ExperimentalJsExport::class)

package dev.halone.compositor

import dev.halone.glasses.math.Vec3
import dev.halone.glasses.math.toDegrees
import dev.halone.glasses.math.toRadians
import kotlin.math.cos
import kotlin.math.sin

@JsExport
class OverlayState(
    val id: String,
    val yawDegrees: Double,
    val pitchDegrees: Double,
    val opacity: Double,
    val shown: Boolean,
)

@JsExport
class PanelState(
    val id: String,
    val yawDegrees: Double,
    val pitchDegrees: Double,
    val slotDegrees: Double,
    val pinned: Boolean,
    val placedByHand: Boolean,
)

/**
 * The scene for the desktop simulator: angles in degrees in the scene's own
 * convention (positive yaw is to the left), panels by id, sized so that one CSS
 * pixel subtends the same angle as on the glasses.
 */
@JsExport
class SimulatorScene(private val panelDistancePx: Double) {

    private val scene = PanelScene<SimulatorPanel>()

    val anchorYawDegrees: Double get() = degrees(scene.anchorYawRadians)
    val returning: Boolean get() = scene.returning
    val focusedId: String? get() = scene.focused?.id
    val grabbedId: String? get() = scene.grabbed?.id

    fun setCollisionPolicy(policy: String) {
        scene.collisionPolicy = when (policy) {
            "overlap" -> CollisionPolicy.OVERLAP
            "avoid-pinned" -> CollisionPolicy.AVOID_PINNED
            else -> CollisionPolicy.AVOID_ALL
        }
    }

    fun setFollowMarginDegrees(degrees: Double) {
        scene.followMarginDegrees = degrees.toFloat()
    }

    fun setCatchUpSeconds(seconds: Double) {
        scene.catchUpSeconds = seconds.toFloat()
    }

    fun setFollowsHead(follows: Boolean) {
        scene.followsHead = follows
    }

    fun setHeadPitchDegrees(degrees: Double) {
        scene.gazePitchRadians = radians(degrees)
    }

    fun setDockAutoHide(autoHide: Boolean) {
        scene.dockVisibility = if (autoHide) DockVisibility.AUTO_HIDE else DockVisibility.ALWAYS
    }

    fun setWindowsAutoHide(autoHide: Boolean, delaySeconds: Double) {
        scene.windowsAutoHide = autoHide
        scene.windowsHideDelaySeconds = delaySeconds.toFloat()
    }

    val windowsOpacity: Double get() = scene.windowsOpacity.toDouble()

    var launcherRequested: Boolean
        get() = scene.launcherRequested
        set(value) {
            scene.launcherRequested = value
        }

    val launcherShown: Boolean get() = scene.launcherShown

    fun openOverlays(dockId: String, dockWidthPx: Int, dockHeightPx: Int, launcherId: String, launcherWidthPx: Int, launcherHeightPx: Int) {
        scene.setOverlays(panelOf(dockId, dockWidthPx, dockHeightPx), panelOf(launcherId, launcherWidthPx, launcherHeightPx))
    }

    fun overlays(): Array<OverlayState> = listOfNotNull(
        scene.dock?.let { overlayState(it, scene.dockOpacity, scene.dockShown) },
        scene.launcher?.let { overlayState(it, scene.launcherOpacity, scene.launcherShown) },
    ).toTypedArray()

    /** Focuses a window and brings the arrangement round to it, as a click in the dock does. */
    fun focusAndCentre(id: String, headYawDegrees: Double) {
        val panel = panel(id)
        scene.focus(panel)
        scene.centreOn(panel, radians(headYawDegrees))
    }

    fun setSlotSpacingDegrees(degrees: Double) {
        if (degrees.toFloat() != scene.slotSpacingDegrees) scene.layOutCylindrical(degrees.toFloat(), immediate = false)
    }

    fun open(id: String, widthPx: Int, heightPx: Int) {
        scene.open(panelOf(id, widthPx, heightPx))
    }

    private fun panelOf(id: String, widthPx: Int, heightPx: Int): SimulatorPanel {
        val pose = PanelPose(distanceMetres = DISTANCE_METRES, widthMetres = metres(widthPx), heightMetres = metres(heightPx))
        return SimulatorPanel(id, widthPx, heightPx, pose)
    }

    private fun overlayState(panel: SimulatorPanel, opacity: Float, shown: Boolean) = OverlayState(
        id = panel.id,
        yawDegrees = degrees(panel.pose.yawRadians),
        pitchDegrees = degrees(panel.pose.pitchRadians),
        opacity = opacity.toDouble(),
        shown = shown,
    )

    /**
     * Takes up a window's new size, keeping the corner opposite [draggedCorner]
     * (`TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT` or `BOTTOM_RIGHT`) where it was.
     */
    fun resize(id: String, widthPx: Int, heightPx: Int, draggedCorner: String) {
        val panel = panel(id)
        val oldWidth = panel.widthPx
        val oldHeight = panel.heightPx
        panel.widthPx = widthPx
        panel.heightPx = heightPx
        scene.resized(panel, oldWidth, oldHeight, Corner.valueOf(draggedCorner).opposite)
    }

    fun close(id: String) {
        scene.remove(panel(id))
        scene.layOutCylindrical(immediate = false)
    }

    fun setPinned(id: String, pinned: Boolean) {
        val panel = panel(id)
        if (panel.follow.enabled == pinned) scene.setFollow(panel, panel.follow.copy(enabled = !pinned))
    }

    fun bringIntoView(id: String, headYawDegrees: Double): Boolean =
        scene.bringIntoView(panel(id), radians(headYawDegrees))

    fun recentre() = scene.recentre()

    fun advance(headYawDegrees: Double, dt: Double): Boolean =
        scene.advanceFollow(radians(headYawDegrees), dt.toFloat())

    fun hitTest(yawDegrees: Double, pitchDegrees: Double): String? =
        scene.hitTest(direction(yawDegrees, pitchDegrees))?.panel?.id

    fun beginGrab(id: String, aimYawDegrees: Double, aimPitchDegrees: Double): Boolean =
        scene.beginGrab(panel(id), direction(aimYawDegrees, aimPitchDegrees))

    fun updateGrab(aimYawDegrees: Double, aimPitchDegrees: Double) =
        scene.updateGrab(direction(aimYawDegrees, aimPitchDegrees))

    fun endGrab(headYawDegrees: Double) {
        scene.endGrab(radians(headYawDegrees))
    }

    fun cancelGrab() = scene.cancelGrab()

    /** The title bar under a window of this size, as left, top, width, height in its pixels. */
    fun titleBar(widthPx: Int, heightPx: Int): Array<Double> =
        TitleBar.bar(widthPx, heightPx).let { arrayOf(it.left.toDouble(), it.top.toDouble(), it.width.toDouble(), it.height.toDouble()) }

    fun panels(): Array<PanelState> = scene.panels.map {
        PanelState(
            id = it.id,
            yawDegrees = degrees(it.pose.yawRadians),
            pitchDegrees = degrees(it.pose.pitchRadians),
            slotDegrees = degrees(it.slotYawRadians),
            pinned = !it.follow.enabled,
            placedByHand = it.slotPlacedByHand,
        )
    }.toTypedArray()

    fun describe(headYawDegrees: Double): String = scene.describe(radians(headYawDegrees))

    private fun panel(id: String) = scene.find(id) ?: throw IllegalArgumentException("no such panel: $id")

    private fun metres(px: Int) = (DISTANCE_METRES * px / panelDistancePx).toFloat()

    private companion object {
        const val DISTANCE_METRES = 4f

        fun radians(degrees: Double) = toRadians(degrees).toFloat()

        fun degrees(radians: Float) = toDegrees(radians.toDouble())

        fun direction(yawDegrees: Double, pitchDegrees: Double): Vec3 {
            val yaw = radians(yawDegrees)
            val pitch = radians(pitchDegrees)
            return Vec3(-cos(pitch) * sin(yaw), sin(pitch), -cos(pitch) * cos(yaw))
        }
    }
}

private class SimulatorPanel(
    id: String,
    override var widthPx: Int,
    override var heightPx: Int,
    pose: PanelPose,
) : ScenePanel(id, pose)
