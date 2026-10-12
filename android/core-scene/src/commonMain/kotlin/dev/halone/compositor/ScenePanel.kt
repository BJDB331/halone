package dev.halone.compositor

import dev.halone.glasses.math.toRadians
import kotlin.concurrent.Volatile

/**
 * Where a panel sits around the wearer.
 *
 * Angles rather than a position, because the layout is a cylinder: only about
 * one comfortable panel fits in a 37.7 degree field of view, so panels are
 * reached by turning the head, not by tiling a wall.
 *
 * Size is in metres but should never be *authored* in metres — see
 * [Panel.fitToFov]. Distance is a comfort choice constrained by the optics,
 * which form the virtual image at one fixed distance.
 */
data class PanelPose(
    /** Heading around the wearer. Smooth follow moves this; layout sets it. */
    val yawRadians: Float = 0f,
    /** Elevation. Zero is eye level. */
    val pitchRadians: Float = 0f,
    val distanceMetres: Float = 4f,
    val widthMetres: Float = 1.28f,
    val heightMetres: Float = 0.80f,
)

/**
 * Whether, and how, a panel re-anchors itself to the wearer's heading.
 *
 * Yaw has no absolute reference, so heading error accumulates as the wearer
 * turns — gyro scale error and imperfect bias absorption both feed it, and no
 * amount of accelerometer correction can help. Left alone a panel wanders out
 * of the field of view and the wearer ends up turned sideways to read it.
 *
 * Rather than pretend the tracking can be made perfect, a panel may follow.
 * Inside [deadzoneDegrees] it is perfectly world-locked, which is what makes the
 * scene feel solid; only sustained error outside that band pulls it, and slowly
 * enough to read as the panel drifting into place rather than as the panel
 * chasing your head.
 *
 * This is deliberately **per panel**, not a global rendering mode: a reference
 * document wants to stay where you left it, while the panel you are working in
 * should come with you. `enabled = false` is a pinned panel.
 */
data class SmoothFollow(
    val enabled: Boolean = true,
    val deadzoneDegrees: Float = 12f,
    /** Seconds to close most of the gap once outside the deadzone. */
    val timeConstantSeconds: Float = 2.5f,
    /**
     * Floor on angular speed while returning to the slot, in degrees per second.
     *
     * An exponential return alone crawls for the last stretch: closing 45 degrees
     * to within a degree takes about thirteen seconds at a 2.5 s constant, which
     * a wearer reads as the panel never arriving. Slow is the point — a
     * correction the wearer can watch is mistuned — but not arriving is worse.
     */
    val followDegreesPerSecond: Float = 6f,
    /**
     * Seconds to close the gap while crossing over a panel it is not allowed to
     * rest on.
     *
     * Much shorter than [timeConstantSeconds], and for a different reason. That
     * constant is slow on purpose: settling has to read as the panel drifting
     * into place rather than chasing the wearer's head. Crossing has the opposite
     * requirement — the panel is passing through a state we do not want at all,
     * so lingering in it reads as the panel struggling to get past. Worn, a
     * crossing at the settling speed felt like resistance.
     */
    val crossingTimeConstantSeconds: Float = 0.25f,
    /**
     * Floor on angular speed while crossing, in degrees per second.
     *
     * An exponential approach is right for settling — it decelerates into place,
     * which is what makes it read as drift rather than as a slide — but wrong for
     * a crossing, where the last few degrees are exactly the ones still
     * overlapping the panel being passed. No time constant fixes that: the tail
     * is the shape of the curve, not its scale. A rate floor makes the traversal
     * a near-constant-speed sweep, which is what a wearer means by asking for
     * the animation to be faster.
     */
    val crossingDegreesPerSecond: Float = 150f,
    /**
     * Seconds to close the gap when the *layout* moved, rather than the wearer.
     *
     * Settling is slow on purpose: a return the wearer can watch is a correction
     * they did not ask for. A rearrangement is the opposite — they asked for it,
     * by opening a panel or pinning one, and they are waiting for the answer.
     * Worn, the settling constant applied to a rearrangement read as the panels
     * struggling to sort themselves out.
     */
    val layoutTimeConstantSeconds: Float = 0.5f,
    /** Floor on angular speed while rearranging, in degrees per second. */
    val layoutDegreesPerSecond: Float = 60f,
) {
    companion object {
        val PINNED = SmoothFollow(enabled = false)
    }
}

/**
 * One panel in the scene: its pose, its follow behaviour, its engine, and the
 * texture that engine paints into.
 *
 * Everything about a panel lives here rather than on the renderer. The renderer
 * having owned a single panel's pose and heading is exactly what made a second
 * panel impossible.
 */
abstract class ScenePanel(
    /** Stable identity, for logs and for the window-management API. */
    val id: String,
    pose: PanelPose,
    follow: SmoothFollow = SmoothFollow(),
) {
    abstract val widthPx: Int
    abstract val heightPx: Int

    /** How far the wearer may resize it; the platform sets what the window declares. */
    var sizeLimits: SizeLimits? = null

    /** A Back button in the title bar, for content with no back control of its own, such as a phone app. */
    @Volatile
    var backButton: Boolean = false

    @Volatile
    var pose: PanelPose = pose

    @Volatile
    var follow: SmoothFollow = follow

    /**
     * This panel's place in the layout, as an angle **relative to the wearer's
     * forward**: the heading it returns to on a recentre, and the offset a
     * follower keeps as it follows.
     */
    @Volatile
    var slotYawRadians: Float = pose.yawRadians

    /** Whether the wearer put this panel in its slot, rather than the layout. */
    @Volatile
    var slotPlacedByHand: Boolean = false
        private set

    /**
     * Whether this panel is on its way back to its slot.
     *
     * The deadzone is a gate, not a stopping point: once outside it a panel
     * returns the whole way, so its resting place does not depend on the
     * direction it came from.
     */
    @Volatile
    private var returning = false

    /** Puts the panel back in its slot; pairs with a tracker recentre. */
    fun recentre() {
        pose = pose.copy(yawRadians = slotYawRadians)
    }

    /** Places the panel in a slot at once, for a panel that is just appearing. */
    fun moveToSlot(yawRadians: Float) {
        slotYawRadians = yawRadians
        slotPlacedByHand = false
        pose = pose.copy(yawRadians = yawRadians)
    }

    /**
     * Changes where the panel belongs without moving it there, so following
     * carries it over as a drift. A panel already in the scene should never jump
     * because the layout changed around it.
     *
     * [byHand] marks the slot as the wearer's own choice rather than the
     * layout's. A hand-placed slot is the follower's equivalent of pinning: a
     * claim on a place, which a layout triggered by something unrelated —
     * opening or closing another panel — has no business overruling.
     */
    fun assignSlot(yawRadians: Float, byHand: Boolean = false) {
        slotYawRadians = yawRadians
        slotPlacedByHand = byHand
    }

    /**
     * Sizes the panel as a fraction of the field of view rather than in metres.
     *
     * Metres are the wrong unit for authoring a panel. What decides whether text
     * is readable and whether the panel fits is the *angle* it subtends, so a
     * panel specified in metres silently changes how much of the view it fills
     * whenever the distance or the field of view changes — as it did the moment
     * the measured FoV replaced the spec sheet's, at which point a panel that
     * fitted comfortably overflowed.
     *
     * Distance then becomes a comfort choice (how far away it feels) rather than
     * a sizing one, which is the separation the window manager wants.
     */
    fun fitToFov(
        verticalFovDegrees: Float,
        verticalFill: Float,
        aspect: Float = widthPx.toFloat() / heightPx,
    ) {
        val halfV = toRadians(verticalFovDegrees / 2.0)
        val visibleHeight = 2.0 * pose.distanceMetres * kotlin.math.tan(halfV)
        val height = (visibleHeight * verticalFill).toFloat()
        pose = pose.copy(widthMetres = height * aspect, heightMetres = height)
    }

    /**
     * Half the angle this panel subtends. Collision resolution happens in
     * angle, because depth is not available to resolve it in: the optics form
     * the virtual image at one fixed distance, so moving a panel nearer or
     * further to dodge a neighbour sets vergence against accommodation.
     */
    val angularHalfWidthRadians: Float
        get() = kotlin.math.atan(pose.widthMetres / 2f / pose.distanceMetres)

    /**
     * Half the angle this panel subtends vertically.
     *
     * Elevation is not a place to *resolve* an overlap — there is too little
     * vertical field for that — but it is very much a place to *have* one or
     * not. Two panels at the same heading and different heights do not overlap,
     * and a collision model that only knows headings will insist they do.
     */
    val angularHalfHeightRadians: Float
        get() = kotlin.math.atan(pose.heightMetres / 2f / pose.distanceMetres)

    /**
     * Where following would move this panel to, or null if it would not move.
     *
     * Split from [advanceFollow] so the scene can resolve the proposal against
     * the panel's neighbours before committing it — a panel must not need to
     * know its neighbours exist.
     */
    fun proposedFollowYaw(
        headYawRadians: Float,
        dt: Float,
        timeConstantSeconds: Float = follow.timeConstantSeconds,
    ): Float? = proposedFollowYawTowards(
        wrapPi(headYawRadians + slotYawRadians), dt, timeConstantSeconds,
    )

    /**
     * As [proposedFollowYaw], but toward a target the scene has adjusted — a
     * resting place clear of a neighbour, say. Keeping the dynamics in one place
     * means an adjusted target still arrives as a drift rather than a jump.
     */
    fun proposedFollowYawTowards(
        target: Float,
        dt: Float,
        timeConstantSeconds: Float = follow.timeConstantSeconds,
        minimumDegreesPerSecond: Float = 0f,
        /**
         * Set when the scene has already decided the arrangement should move, so
         * this panel's own band must not veto it. Whether to follow is a question
         * about the whole arrangement, not about one panel.
         */
        alreadyTriggered: Boolean = false,
    ): Float? {
        if (!follow.enabled || dt <= 0f || dt > MAX_FOLLOW_STEP_SECONDS) return null

        val error = wrapPi(target - pose.yawRadians)
        val deadzone = toRadians(follow.deadzoneDegrees.toDouble()).toFloat()

        if (!alreadyTriggered && !returning) {
            // Perfectly world-locked until the wearer's heading has drifted
            // beyond the band. This is what makes the scene feel solid.
            if (kotlin.math.abs(error) <= deadzone) return null
            returning = true
        }
        if (kotlin.math.abs(error) <= SETTLED_RADIANS) {
            returning = false
            return null
        }

        val alpha = (dt / timeConstantSeconds).coerceAtMost(1f)
        var step = error * alpha

        val floorDegrees = maxOf(minimumDegreesPerSecond, follow.followDegreesPerSecond)
        val floor = toRadians(floorDegrees.toDouble()).toFloat() * dt
        if (kotlin.math.abs(step) < floor) {
            // Clamped to the remaining error, so a floor cannot overshoot.
            step = kotlin.math.sign(error) * kotlin.math.min(floor, kotlin.math.abs(error))
        }
        return wrapPi(pose.yawRadians + step)
    }

    /**
     * Advances smooth follow by [dt] seconds. Pure arithmetic, so the behaviour
     * that decides whether the scene feels solid is testable off-device.
     *
     * The deadzone decides **whether** to follow, not where to stop. Stopping at
     * the edge of the band made the resting place depend on which way the wearer
     * had turned — slot plus deadzone one way, slot minus deadzone the other. With
     * a 12 degree band and a slot 15 degrees off centre, turning one way left the
     * panel nearly centred and turning the other left it 27 degrees away, on the
     * edge of a 33 degree field of view, appearing never to arrive. So once
     * pulled, a follower returns the whole way to its slot.
     *
     * A follower chases **its own slot relative to the wearer**, not the
     * wearer's heading. Chasing the heading looks identical with one panel and
     * is catastrophic with two: every follower outside the deadzone clamps to
     * the same edge of it, so they converge onto one heading and, being at the
     * same distance, end up coplanar and z-fighting. Following the slot instead
     * makes the whole layout move as a group and keep its arrangement.
     *
     * Returns true if the panel moved.
     */
    fun advanceFollow(headYawRadians: Float, dt: Float): Boolean {
        val next = proposedFollowYaw(headYawRadians, dt) ?: return false
        pose = pose.copy(yawRadians = next)
        return true
    }

    companion object {
        /** Close enough to its slot to stop, in radians. */
        private val SETTLED_RADIANS = toRadians(0.5).toFloat()

        /**
         * A step longer than this is a stall, not motion. Following across it
         * would dump the whole accumulated error in one frame, which reads as
         * the panel jumping.
         */
        const val MAX_FOLLOW_STEP_SECONDS = 0.5f

        fun wrapPi(angle: Float): Float {
            var a = angle
            while (a > kotlin.math.PI) a -= (2.0 * kotlin.math.PI).toFloat()
            while (a < -kotlin.math.PI) a += (2.0 * kotlin.math.PI).toFloat()
            return a
        }
    }
}
