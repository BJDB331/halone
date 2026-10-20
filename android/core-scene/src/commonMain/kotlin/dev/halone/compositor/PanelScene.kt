package dev.halone.compositor

import dev.halone.glasses.math.Vec3
import dev.halone.glasses.math.formatFixed
import dev.halone.glasses.math.toDegrees
import dev.halone.glasses.math.toRadians
import kotlin.concurrent.Volatile
import kotlin.math.atan

/**
 * What a follower does when the wearer's turn would carry it onto a neighbour.
 *
 * There is no depth to resolve this in — the optics form the virtual image at
 * one fixed distance, so nudging a panel nearer or further to dodge a neighbour
 * trades an overlap for a vergence/accommodation conflict. Resolution is
 * therefore in angle, which also means "the follower wins" is not a distinct
 * option: winning in angle *is* overlapping.
 */
enum class CollisionPolicy {
    /** Nobody avoids anybody. Followers ride straight over pinned panels. */
    OVERLAP,

    /**
     * Pinned panels are obstacles to a follower that is *settling*, but not to
     * one in transit.
     *
     * Pinning is an explicit act and following is a default, so where a panel
     * comes to rest is the pinned panel's call. But a follower that is merely
     * clamped gets stuck against the obstacle's edge for as long as the wearer
     * keeps turning — in angle there is no way around it, and depth and
     * elevation are both unavailable (fixed focal distance; only 19 degrees of
     * vertical field). Stopping there breaks the one promise following makes,
     * and it breaks it exactly when the wearer turns to bring the panel along.
     *
     * So the constraint applies to the resting place, not to the journey: a
     * follower crosses over a pinned panel while catching up and is pushed clear
     * of it once it arrives. The overlap is transient and self-resolving, which
     * is the one kind of overlap that costs nothing.
     */
    AVOID_PINNED,

    /** Followers avoid each other too, not just pinned panels. */
    AVOID_ALL,
}

/** Whether the dock stays up, or only comes up when the pointer goes below the windows. */
enum class DockVisibility { ALWAYS, AUTO_HIDE }

/** Where a ray met a panel, in that panel's pixels. */
data class PanelHit(val x: Float, val y: Float)

/**
 * The nearest panel a ray met. [distanceMetres] is what makes "nearest" mean
 * anything, and is what stops a cursor being drawn on a panel that is behind
 * another one.
 */
data class PanelSceneHit<P : ScenePanel>(
    val panel: P,
    val hit: PanelHit,
    val distanceMetres: Float,
    val part: HitPart = HitPart.CONTENT,
)

/**
 * The panels around the wearer.
 *
 * Everything here is pure arithmetic over the panel records — no GL, no
 * Android — so hit testing and following can be tested off-device. The
 * compositor draws what the scene describes; it does not decide it.
 */
open class PanelScene<P : ScenePanel> {

    private val mutable = concurrentPanelList<P>()

    /**
     * Which side of an obstacle each follower has committed to, by panel id.
     *
     * Latched rather than derived from the follower's current position. Deriving
     * it looked like hysteresis and was not: a crossing carries the panel over
     * the obstacle's centre, so "which side am I on" flips halfway through the
     * journey and takes the decision with it. A small head movement during the
     * crossing then reversed the whole trip. A commitment has to be remembered
     * to mean anything.
     */
    private val committedSide = HashMap<String, Boolean>()

    /**
     * Where each panel was last sent, by id.
     *
     * Kept across frames, not rebuilt each one. A panel pushed clear of a pinned
     * obstacle comes to rest a long way from the slot it was aiming at, and a
     * neighbour that resolves against the *slot* never learns it. Worn, that was
     * a follower parked beside the pinned panel with another follower sitting
     * eight degrees inside it, indefinitely: resolving in slot order meant the
     * first panel asked where the second wanted to be, which was nowhere near
     * where the second was about to be put.
     */
    private val parkedTarget = HashMap<String, Float>()

    /** Iterable from the render thread while the UI thread adds and removes. */
    val panels: List<P> get() = mutable

    /**
     * How followers behave when the wearer's turn would carry one onto a
     * neighbour. Switchable because it is a matter of taste as much as design,
     * and easier judged worn than argued.
     */
    @Volatile
    var collisionPolicy: CollisionPolicy = CollisionPolicy.AVOID_ALL

    /** Remembered so a re-layout does not silently change the spacing. */
    @Volatile
    var slotSpacingDegrees: Float = 30f
        private set

    /** True while the arrangement is on its way back to the wearer's heading. */
    @Volatile
    private var arrangementReturning = false

    /**
     * Whether the motion in progress was asked for.
     *
     * A return the wearer's own head triggered is deliberately slow — they did
     * not ask for it, and watching a correction is worse than not noticing one.
     * A rearrangement is something they asked for and are waiting on, so it gets
     * its own much shorter constant. Same machinery, opposite requirement.
     */
    @Volatile
    private var arrangementCommanded = false

    /**
     * True while the motion in progress is only resolving an overlap.
     *
     * Every panel's destination is then its own current position, so the
     * collision adjustment is the only thing that moves anything and a panel
     * nobody has to move stays exactly where it is. Taking the arrangement's
     * average anchor instead is not "in place": with the panels disagreeing
     * about where the anchor is — which is what an overlap usually means — the
     * average pulls each of them somewhere neither of them was.
     */
    @Volatile
    private var resolvingInPlace = false

    /** The panels an in-place resolution is allowed to move. */
    private val resolvingIds = HashSet<String>()

    /** Each panel's heading after the last return step, to tell a resting panel from a moving one. */
    private val previousYaw = HashMap<String, Float>()

    /**
     * Shifts the whole arrangement so that the panel the wearer was last looking
     * at ends up in front of them, rather than the arrangement's midpoint — which
     * with two panels is the gap between them, the one place they were not
     * looking.
     *
     * Latched when a return begins, not recomputed while it runs: the panels
     * sweep across the wearer's gaze on their way back, so focus would change
     * underneath the decision and drag the destination with it.
     */
    @Volatile
    private var returnOffset = 0f

    /**
     * The heading the arrangement's slots are measured from.
     *
     * Remembered, not derived from where the panels are. Deriving it from live
     * poses makes the band consult a quantity that avoidance changes: pushing a
     * panel clear of a pinned one moves the average, which re-triggers the
     * return, which drives the panel back onto the obstacle it was pushed off.
     * Worn, that was two followers charging at a pinned panel and snapping back,
     * over and over, for as long as the wearer looked at it. Hysteresis without
     * memory is not hysteresis — the same lesson the committed side taught.
     */
    @Volatile
    private var anchorYaw = 0f

    internal val anchorYawRadians: Float get() = anchorYaw

    internal val returning: Boolean get() = arrangementReturning

    /** The heading the anchor is travelling to. */
    private var anchorTarget = 0f

    /**
     * Where the anchor was when a head-triggered return began, so a wearer who
     * looks back can have the arrangement back where it was.
     */
    private var returnOrigin = 0f

    /**
     * True while the motion in progress was started by the head leaving the
     * band, and so should keep answering the head. Latching the heading instead
     * meant a glance away and back sent the panel the whole way over and then
     * the whole way back.
     */
    private var returnTracksHead = false

    /** True once a return has turned round and is heading back to [returnOrigin]. */
    private var returningToOrigin = false

    /** How the arrangement itself moves: slowly when the wearer did not ask. */
    private val arrangementPace = SmoothFollow()

    /**
     * And how briskly when they did ask — by turning to look at something, or by
     * changing the layout.
     *
     * Settable because it is a matter of feel, and feel is settled by wearing
     * it. Note that the arrangement and the panels within it each smooth their
     * own motion, so a catch-up is two lags in series and reads slower than
     * either constant suggests; this sets both.
     */
    @Volatile
    var catchUpSeconds: Float = 0.2f

    @Volatile
    var catchUpDegreesPerSecond: Float = 150f

    /**
     * Whether looking past the arrangement brings it along. Off, the arrangement
     * stays where it is in the room; layout commands and overlap resolution
     * still move panels, because those are not the head asking.
     */
    @Volatile
    var followsHead = true

    /**
     * How far beyond the arrangement's outer edges the wearer may look before
     * it follows. Small, because the arrangement's own extent already does most
     * of the work. A matter of taste, so it is a setting.
     */
    @Volatile
    var followMarginDegrees: Float = 6f

    /**
     * Whether a panel follows the anchor closely during a return the head
     * started, instead of easing in behind it at its own 2.5 s. Two slow lags
     * in series ease in gently, but the panel falls behind the anchor, so when a
     * return turns round the panel runs on until the anchor passes back through
     * it. **[measured]** Worn blind against easing in: "B feels a lot better
     * than A", B being this.
     */
    @Volatile
    var tightPanelTracking = true

    /** Whether a return the head started can be raised to catch-up while it runs. */
    @Volatile
    var catchUpMidMove = true

    /**
     * Where input goes. Focus follows the pointer, so this is the panel the last
     * hit test landed on.
     */
    @Volatile
    var focused: P? = null
        private set

    /**
     * The last panel the wearer's gaze actually landed on.
     *
     * Distinct from [focused], which starts out pointing at the first panel added
     * so that input has somewhere to go. That default is fine for routing a tap
     * and wrong for deciding layout: before the wearer has looked at anything,
     * there is no "panel I was looking at" to bring back.
     */
    @Volatile
    private var lastGazed: P? = null

    /**
     * How fast the head was turning at the last [advanceFollow]. A gaze that
     * lands on a panel mid-turn is passing over it, not looking at it.
     */
    private var headDegreesPerSecond = 0f
    private var lastHeadYaw: Float? = null

    /** How long the head has been turning slower than [LOOKING_DEGREES_PER_SECOND]. */
    private var headStillSeconds = 0f

    fun add(panel: P): P {
        mutable.add(panel)
        if (focused == null) focused = panel
        return panel
    }

    fun remove(panel: P) {
        mutable.remove(panel)
        resizeAnimations.remove(panel)
        if (resizing?.panel === panel) resizing = null
        parkedTarget.remove(panel.id)
        if (grabbed === panel) grabbed = null
        if (lastGazed === panel) lastGazed = null
        committedSide.keys.removeAll { it.startsWith("${panel.id}\u0000") || it.endsWith("\u0000${panel.id}") }
        if (focused === panel) focused = mutable.firstOrNull()
    }

    fun find(id: String): P? = mutable.firstOrNull { it.id == id }

    fun clear() {
        mutable.clear()
        grabbed = null
        committedSide.clear()
        parkedTarget.clear()
        focused = null
        lastGazed = null
    }

    /**
     * Puts every panel back in its slot; pairs with a tracker recentre. A held
     * panel is where the wearer is holding it, which a recentre has no business
     * overruling — and the grab would win the next frame regardless, so leaving
     * it in would only be a fight nobody can see the result of.
     */
    fun recentre() {
        anchorYaw = 0f
        anchorTarget = 0f
        mutable.forEach { if (it !== grabbed) it.recentre() }
    }

    /**
     * Spreads the panels evenly around the wearer, centred on forward.
     *
     * **Pinned panels are left exactly where they are.** A pinned panel is a
     * statement about a place in the room, and a layout triggered by something
     * unrelated — opening another panel, say — has no business overruling it.
     * Found the hard way: opening a third panel silently teleported the pinned
     * one from +36 degrees to straight ahead.
     */
    fun layOutCylindrical(
        spacingDegrees: Float = slotSpacingDegrees,
        immediate: Boolean = true,
        respaceHandPlaced: Boolean = false,
    ) {
        slotSpacingDegrees = spacingDegrees
        // A layout change is a command, not a head movement: the panels should
        // take up their new slots even if the wearer has not moved.
        if (!immediate) {
            arrangementReturning = true
            arrangementCommanded = true
            returnTracksHead = false
            returningToOrigin = false
        }
        // An immediate layout puts the panels at world angles, so that is where
        // the arrangement now hangs from.
        if (immediate) {
            anchorYaw = 0f
            anchorTarget = 0f
        }
        val spacing = toRadians(spacingDegrees.toDouble()).toFloat()
        val movable = mutable.filter {
            it.follow.enabled && (respaceHandPlaced || !it.slotPlacedByHand)
        }
        val centre = (movable.size - 1) / 2f
        movable.forEachIndexed { index, panel ->
            val slot = (index - centre) * spacing
            if (immediate) panel.moveToSlot(slot) else panel.assignSlot(slot)
        }
    }

    /**
     * Pins or unpins a panel, re-laying out whatever still follows.
     *
     * Pinning takes a panel out of the layout, so the followers left behind have
     * more room and should share it: with one pinned panel and one follower, the
     * follower belongs straight ahead, not still holding the slot it was given
     * when they were both moving. Worn, keeping the old slot read as the panel
     * refusing to centre. The re-layout is not immediate — a panel already in the
     * scene drifts to its new slot rather than jumping to it.
     */
    fun setFollow(panel: P, follow: SmoothFollow) {
        panel.follow = follow
        layOutCylindrical(immediate = false)
    }

    /**
     * Brings a following panel in front of the wearer by giving it the layout
     * slot nearest their heading, and the panel that held that slot its old one.
     * A layout change like any other, so both drift. Returns false for a pinned
     * or hand-placed panel: where those are is a choice the wearer made.
     */
    fun bringIntoView(panel: P, headYawRadians: Float): Boolean {
        if (!panel.follow.enabled || panel.slotPlacedByHand || panel !in mutable) return false
        val heading = ScenePanel.wrapPi(headYawRadians - anchorYaw)
        val nearest = mutable
            .filter { it.follow.enabled && !it.slotPlacedByHand }
            .minByOrNull { kotlin.math.abs(ScenePanel.wrapPi(it.slotYawRadians - heading)) }
            ?: return false
        if (nearest !== panel) {
            mutable.replaceEach {
                when {
                    it === panel -> nearest
                    it === nearest -> panel
                    else -> it
                }
            }
            layOutCylindrical(immediate = false)
        }
        return true
    }

    /** Puts the arrangement back in front of the wearer, band or no band. */
    fun requestReturn() {
        arrangementReturning = true
        arrangementCommanded = true
        returnTracksHead = false
        returningToOrigin = false
    }

    /**
     * Adds a panel and gives it a place, without the rest of the scene jumping.
     *
     * The newcomer is the only panel with nowhere to drift *from*, so it is the
     * only one placed outright — at the arrangement's own anchor rather than at
     * a world angle, or it appears wherever the wearer happened to be facing
     * when the scene was created. Everything already in the scene drifts, and
     * drifts at the layout speed: worn, opening a third panel left it lying on
     * top of a hand-placed neighbour until a head movement happened to trigger
     * following, because an immediate layout snaps the panels it owns and
     * nothing at all afterwards.
     */
    fun open(panel: P): P {
        add(panel)
        layOutCylindrical(immediate = false)
        panel.moveToSlot(panel.slotYawRadians)
        panel.pose = panel.pose.copy(
            yawRadians = ScenePanel.wrapPi(anchorYaw + panel.slotYawRadians),
        )
        return panel
    }

    /**
     * The panel the wearer is holding, or null. Its pose is driven by the
     * pointer rather than by the layout for as long as the grab lasts.
     */
    @Volatile
    var grabbed: P? = null
        private set

    /**
     * Where the panel was held, relative to the pointer, when the grab began.
     *
     * Kept so the panel does not snap its centre onto the pointer the moment it
     * is picked up: a window is dragged by the point you took hold of it, and a
     * jump at the start of a drag reads as the panel having been thrown rather
     * than picked up.
     */
    private var grabYawOffset = 0f
    private var grabPitchOffset = 0f

    /**
     * Picks a panel up. [pointerDirection] is where the wearer is aiming, in
     * canonical world coordinates — the same direction the hit test uses, so
     * head gaze and the phone pointer both grab the same way.
     */
    fun beginGrab(panel: P, pointerDirection: Vec3): Boolean {
        val aim = aimAngles(pointerDirection) ?: return false
        grabbed = panel
        centring = null
        grabStartYaw = panel.pose.yawRadians
        grabStartPitch = panel.pose.pitchRadians
        grabYawOffset = ScenePanel.wrapPi(panel.pose.yawRadians - aim.yaw)
        grabPitchOffset = panel.pose.pitchRadians - aim.pitch
        return true
    }

    private var grabStartYaw = 0f
    private var grabStartPitch = 0f

    /**
     * Puts the held panel back where it was picked up, as if it never had been.
     * A press on a title bar that turns out to be a click must not record a
     * placement by hand, which [endGrab] would.
     */
    fun cancelGrab() {
        val panel = grabbed ?: return
        grabbed = null
        panel.pose = panel.pose.copy(yawRadians = grabStartYaw, pitchRadians = grabStartPitch)
    }

    /** Carries the held panel with the pointer. Called once per frame. */
    fun updateGrab(pointerDirection: Vec3) {
        val panel = grabbed ?: return
        val aim = aimAngles(pointerDirection) ?: return
        panel.pose = panel.pose.copy(
            yawRadians = ScenePanel.wrapPi(aim.yaw + grabYawOffset),
            pitchRadians = (aim.pitch + grabPitchOffset)
                .coerceIn(-MAX_PLACEMENT_PITCH, MAX_PLACEMENT_PITCH),
        )
    }

    /**
     * Lets go, and makes where the panel was let go of the place it belongs.
     *
     * The two kinds of panel need opposite things done to them, and this is the
     * frame problem the whole window manager turns on. A pinned panel lives in
     * world angles, so it is already where it was put and there is nothing to
     * record. A follower's position is *derived* from its slot every frame, so
     * leaving the slot alone would have following drag the panel straight back
     * out of the wearer's hands — the placement has to be written down as a
     * slot.
     *
     * **The slot is measured against the arrangement's anchor, not against the
     * wearer's heading.** The wearer places a panel relative to the panels they
     * can see, and those panels are where *their* slots put them, which is only
     * the same thing as the wearer's heading when the arrangement happens to be
     * settled. It is not settled here: the grab froze it while the wearer
     * turned, so the heading has moved and the panels have not. Measuring from
     * the head therefore records a placement nobody made. Worn: a panel put down
     * to the right of its neighbour came back on the left, overlapping it, at
     * the first head movement large enough to make the pair follow — the
     * recorded slot and the seen arrangement disagreed by exactly how far the
     * head had turned during the drag.
     *
     * With no other follower to anchor against, the wearer's heading is the only
     * reference there is, and is then the right one.
     *
     * Elevation needs no such translation either way: pitch has an absolute
     * reference in gravity, so a panel placed above eye level stays there
     * without a slot to remember it by.
     */
    fun endGrab(headYawRadians: Float): P? {
        val panel = grabbed ?: return null
        grabbed = null
        // Which side of a pinned panel a follower keeps to is remembered so a
        // crossing is not bounced back mid-way. A placement by hand is not a
        // crossing: worn, a panel dropped on a pinned one's right half parked
        // to its left, the side it had been dragged from.
        committedSide.keys.removeAll { it.startsWith("${panel.id}\u0000") }
        if (panel.follow.enabled) {
            panel.assignSlot(ScenePanel.wrapPi(panel.pose.yawRadians - anchorYaw), byHand = true)
        }
        return panel
    }

    /**
     * Advances every follower, resolving each proposal against its neighbours.
     *
     * A panel proposes where following would take it; the scene decides whether
     * it may go there. Keeping the decision here is what lets a panel stay
     * ignorant of its neighbours while the layout still has an opinion about
     * them.
     */
    fun advanceFollow(headYawRadians: Float, dt: Float): Boolean {
        lastHeadYaw?.let { previous ->
            if (dt > 0f) {
                headDegreesPerSecond = kotlin.math.abs(
                    toDegrees(ScenePanel.wrapPi(headYawRadians - previous).toDouble()).toFloat()
                ) / dt
            }
        }
        lastHeadYaw = headYawRadians
        headStillSeconds = if (headDegreesPerSecond < LOOKING_DEGREES_PER_SECOND) headStillSeconds + dt else 0f
        advanceOverlays(headYawRadians, dt)
        if (popupShown) placePopup()
        advanceResizeAnimations(dt)
        // Placing a panel is done against the rest of the arrangement, so the
        // rest of the arrangement has to hold still while it happens. Worn, the
        // alternative was unusable: a follower both chased the wearer's heading
        // and re-parked clear of the panel being carried, at crossing speed, so
        // the target the wearer was aiming at moved in response to where they
        // were aiming it. "I can't put it to the right of the second panel — it
        // jumps way ahead." A grab is a layout command, and nothing else in the
        // layout may answer it until it finishes.
        if (grabbed != null) return false

        if (!arrangementReturning) {
            if (hasUnresolvedOverlap()) {
                // An overlap is its own reason to move, and the band must not
                // gate it: worn, two panels left lying on top of each other
                // stayed that way until the wearer happened to turn far enough
                // to trigger following, which is a fix arriving by luck.
                //
                // Resolved *in place*: this offset holds the arrangement where
                // it already is, so every panel's destination is where it
                // belongs now and only the pair that actually overlap move.
                arrangementReturning = true
                arrangementCommanded = true
                resolvingInPlace = true
                resolvingIds.clear()
                returnTracksHead = false
                returnOffset = 0f
                return advanceReturn(headYawRadians, dt)
            }
            if (!hasLeftTheBand(headYawRadians)) return false
            val catchUp = bandExcess > widestPanelRadians()
            // A drift is a correction nobody asked for, so it can wait for the
            // head to come to rest. Starting it mid-glance latched a destination
            // from a gaze still in flight: overshooting the next panel began
            // re-centring the previous one, and settling back cancelled it.
            if (!catchUp && (bandExcess < DRIFT_MIN_EXCESS || headStillSeconds < DRIFT_SETTLE_SECONDS)) {
                return false
            }
            arrangementReturning = true
            resolvingInPlace = false
            returnTracksHead = true
            returningToOrigin = false
            returnOrigin = anchorYaw
            anchorTarget = headYawRadians
            // How far past the band the wearer has looked says what kind of
            // movement this is. A little way past is drift to be corrected
            // without their noticing; a panel's width past is a person who has
            // turned to look at something and is waiting for the arrangement to
            // arrive. Worn: "looking further past should make it quickly follow
            // and centre" — and at the drift pace it read as the panels having
            // a hard time following.
            //
            // Decided here and only ever raised afterwards, by retargetOnHead.
            // Blending it continuously was tried and reverted: it let focus
            // land on the wrong panel, which focusHeld now prevents.
            arrangementCommanded = catchUp
            // Not onto a panel the wearer placed themselves. Anchoring the
            // return on the last panel looked at exists because with two
            // symmetric slots the arrangement's midpoint is the *gap* between
            // them — it is a repair for a layout the wearer did not choose. A
            // hand-placed slot is a choice, and re-centring the arrangement on
            // it is precisely undoing that choice: worn, a panel deliberately
            // parked 40 degrees to the right slid back in front, dragging the
            // arrangement across its neighbour, every time the wearer glanced at
            // it and turned away.
            //
            // Which panel depends on the kind of movement. A catch-up is a
            // wearer who turned away from what they were reading, so it brings
            // that back. A drift is a wearer who has come to rest a little past
            // the arrangement, and the panel they are looking past is the one
            // they were heading for — the last one *looked at* is where the
            // glance started, which worn re-centred the wrong panel.
            val centred = if (catchUp) lastGazed else nearestFollowerTo(headYawRadians)
            returnOffset = centred
                ?.takeIf { it.follow.enabled && !it.slotPlacedByHand }
                ?.let { -it.slotYawRadians }
                ?: 0f
        } else if (returnTracksHead) {
            retargetOnHead(headYawRadians)
        }

        return advanceReturn(headYawRadians, dt)
    }

    /**
     * Keeps a head-triggered return answering the head it was triggered by.
     *
     * Back well inside the band the arrangement started from and it goes back
     * there; anywhere else it goes wherever the wearer is now looking. The pace
     * and the panel it centres on stay as decided at the start — re-deciding
     * those every frame is the change that let focus land on the wrong panel.
     */
    private fun retargetOnHead(headYawRadians: Float) {
        val excessFromOrigin = bandExcessAround(returnOrigin, headYawRadians) ?: return
        returningToOrigin = if (returningToOrigin) {
            excessFromOrigin <= 0f
        } else {
            excessFromOrigin < -BACK_TO_ORIGIN_HYSTERESIS
        }
        if (returningToOrigin) return
        anchorTarget = headYawRadians
        // Up to catch-up, never back down: a wearer who has kept turning is
        // waiting for the arrangement, however little past the band they were
        // when it started to move.
        val ahead = bandExcessAround(anchorYaw, headYawRadians) ?: return
        if (catchUpMidMove && ahead > widestPanelRadians()) arrangementCommanded = true
    }

    /**
     * True while panels are moving fast enough to sweep under a gaze that has
     * not moved. Focus follows the pointer, so without this a panel passing
     * through on its way somewhere else took focus from the one being brought
     * back — the reason the first attempt at a faster catch-up was reverted.
     */
    private val focusHeld: Boolean
        get() = arrangementReturning && !resolvingInPlace && (arrangementCommanded || crossingInProgress)

    private var crossingInProgress = false

    /** Where a panel is headed, before its neighbours have had their say. */
    private fun targetOf(panel: P, headYawRadians: Float): Float =
        if (resolvingInPlace) panel.pose.yawRadians
        else ScenePanel.wrapPi(anchorYaw + panel.slotYawRadians)

    /**
     * One step of whatever motion the arrangement has already decided on.
     *
     * The arrangement moves as *one* thing — the anchor, which is the heading
     * its slots are measured from — and each panel then tracks its own slot
     * relative to that, briskly, with avoidance allowed to push it off. The
     * slowness that makes a return read as drift lives in the anchor; a panel
     * lagging the anchor as well would only make the arrangement look loose.
     */
    private fun advanceReturn(headYawRadians: Float, dt: Float): Boolean {
        var anchorMoved = false
        if (!resolvingInPlace && dt > 0f && dt <= ScenePanel.MAX_FOLLOW_STEP_SECONDS) {
            val target = if (returningToOrigin) returnOrigin else ScenePanel.wrapPi(anchorTarget + returnOffset)
            val error = ScenePanel.wrapPi(target - anchorYaw)
            if (kotlin.math.abs(error) > SETTLED_RADIANS) {
                val seconds = if (arrangementCommanded) catchUpSeconds
                else arrangementPace.timeConstantSeconds
                val perSecond = if (arrangementCommanded) catchUpDegreesPerSecond
                else arrangementPace.followDegreesPerSecond
                var step = error * (dt / seconds).coerceAtMost(1f)
                val floor = toRadians(perSecond.toDouble()).toFloat() * dt
                if (kotlin.math.abs(step) < floor) {
                    step = kotlin.math.sign(error) * kotlin.math.min(floor, kotlin.math.abs(error))
                }
                anchorYaw = ScenePanel.wrapPi(anchorYaw + step)
                anchorMoved = true
            }
        }

        // Resolved in slot order, each panel seeing where the ones before it
        // have actually been *parked* rather than where their slots wanted them.
        // Without that, two followers pushed clear of the same pinned panel both
        // choose the same place beside it — the parking spot is one point, not a
        // queue — and land on each other. Worn, that was a pair of panels
        // repeatedly closing on one another and snapping apart, for as long as
        // the wearer kept looking at the pinned panel.
        var moved = false
        var crossing = false
        if (resolvingInPlace) resolvingIds += overlapping()
        for (panel in mutable.sortedBy { it.slotYawRadians }) {
            // Resolving in place moves the panels that overlap and whatever
            // they push, and nothing else. Worn, one dropped panel had every
            // parked follower re-decide its side from the gaze and cross the room.
            if (resolvingInPlace && panel.id !in resolvingIds) {
                parkedTarget[panel.id] = panel.pose.yawRadians
                continue
            }
            val wanted = targetOf(panel, headYawRadians)
            val resolved = resolveTarget(panel, wanted, headYawRadians, dt)
            parkedTarget[panel.id] = resolved.target
            if (resolved.crossing) crossing = true
            // An overlap is a state to pass through, not to travel at settling
            // speed, so a crossing gets its own much shorter constant.
            val timeConstant = when {
                resolved.crossing -> panel.follow.crossingTimeConstantSeconds
                arrangementCommanded -> catchUpSeconds
                tightPanelTracking -> TIGHT_TRACKING_SECONDS
                else -> panel.follow.timeConstantSeconds
            }
            val floor = when {
                resolved.crossing -> panel.follow.crossingDegreesPerSecond
                arrangementCommanded -> catchUpDegreesPerSecond
                else -> 0f
            }
            val next = panel.proposedFollowYawTowards(
                resolved.target,
                dt,
                timeConstant,
                minimumDegreesPerSecond = floor,
                alreadyTriggered = true,
            ) ?: continue
            if (next == panel.pose.yawRadians) continue
            panel.pose = panel.pose.copy(yawRadians = next)
            moved = true
        }

        // The panels themselves know when they have arrived — and a follower
        // parked clear of a pinned neighbour is deliberately *not* at its slot,
        // so the band cannot be the thing that decides this. Nothing moving means
        // everything has settled, wherever that turned out to be.
        crossingInProgress = crossing
        previousYaw.clear()
        mutable.forEach { previousYaw[it.id] = it.pose.yawRadians }
        if (!moved && !anchorMoved && !correctCentring()) {
            arrangementReturning = false
            arrangementCommanded = false
            resolvingInPlace = false
            resolvingIds.clear()
            returnTracksHead = false
            returningToOrigin = false
        }
        return moved || anchorMoved
    }

    /**
     * Whether any panel is sitting on one it is supposed to give way to.
     *
     * Only pairs where one yields count: two panels the wearer put on top of
     * each other are their own business, and a pinned panel is nobody else's to
     * move.
     */
    private fun hasUnresolvedOverlap(): Boolean = overlapping().isNotEmpty()

    /** The followers currently sitting on a panel they give way to. */
    private fun overlapping(): Set<String> {
        if (collisionPolicy == CollisionPolicy.OVERLAP) return emptySet()
        val found = HashSet<String>()
        for (panel in mutable) {
            if (!panel.follow.enabled || panel === grabbed) continue
            for (other in mutable) {
                if (other === panel || other === grabbed) continue
                if (!yieldsTo(panel, other)) continue
                val pitchGap =
                    kotlin.math.abs(panel.pose.pitchRadians - other.pose.pitchRadians)
                if (pitchGap > panel.angularHalfHeightRadians + other.angularHalfHeightRadians) {
                    continue
                }
                val separation =
                    kotlin.math.abs(ScenePanel.wrapPi(panel.pose.yawRadians - other.pose.yawRadians))
                if (separation <
                    panel.angularHalfWidthRadians + other.angularHalfWidthRadians
                ) found += panel.id
            }
        }
        return found
    }

    /**
     * What the arrangement thinks it is doing, for a wearer who can see that it
     * is doing something else. Reading positions and inferring the rest cost
     * more time this session than any bug in it.
     */
    fun describe(headYawRadians: Float): String = buildString {
        append("head ${degrees(headYawRadians)}° anchor ${degrees(anchorYaw)}° ")
        append("target ${degrees(anchorTarget)}° offset ${degrees(returnOffset)}° ")
        append("returning=$arrangementReturning commanded=$arrangementCommanded ")
        append("inPlace=$resolvingInPlace grabbed=${grabbed?.id} ")
        append("policy=$collisionPolicy overlap=${hasUnresolvedOverlap()} ")
        append("band=${hasLeftTheBand(headYawRadians)}")
        for (panel in mutable.sortedBy { it.slotYawRadians }) {
            // A pinned panel has no destination; asking for one and printing it
            // only invites reading meaning into a number that never moves it.
            val resolved = if (panel.follow.enabled) {
                resolveTarget(panel, targetOf(panel, headYawRadians), headYawRadians, 0f)
            } else {
                null
            }
            append("\n    ${panel.id.padEnd(7)} at ${degrees(panel.pose.yawRadians, "+")}° ")
            append("slot ${degrees(panel.slotYawRadians, "+")}°")
            resolved?.let { append(" -> ${degrees(it.target, "+")}°") }
            if (resolved?.crossing == true) append(" CROSSING")
            append(if (!panel.follow.enabled) " pinned" else if (panel.slotPlacedByHand) " byHand" else "")
        }
    }

    private fun degrees(radians: Float, positivePrefix: String = "") =
        formatFixed(toDegrees(radians.toDouble()), 0, positivePrefix)

    /** Whether [panel] is the one that gives way when it meets [other]. */
    private fun yieldsTo(panel: P, other: P): Boolean = when {
        !other.follow.enabled -> true
        collisionPolicy == CollisionPolicy.AVOID_PINNED -> false
        panel.slotPlacedByHand -> false
        else -> true
    }

    /**
     * Whether the arrangement should be moving at all.
     *
     * Following is a property of the **arrangement**, not of each panel. A
     * per-panel band measured from each panel's own slot means looking straight
     * at a panel 15 degrees off centre already exceeds a 12 degree band, so the
     * whole layout slides over to put its *gap* in front of the wearer — the one
     * place they were not looking. Worn, that made it impossible to look at
     * either of two panels.
     *
     * So the band is measured from the arrangement's own extent: the wearer may
     * look anywhere across the panels, and out to a margin beyond their outer
     * edges, before anything moves. Deriving it from the panels' angular width
     * also means it scales on its own — a wide panel earns a wide band, which a
     * fixed number could never do.
     */
    /** How far past the band the wearer was looking, from the last test. */
    private var bandExcess = 0f

    private fun nearestFollowerTo(headYawRadians: Float): P? =
        mutable.filter { it.follow.enabled && !it.slotPlacedByHand }
            .minByOrNull { kotlin.math.abs(ScenePanel.wrapPi(anchorYaw + it.slotYawRadians - headYawRadians)) }

    /** A panel's own angular width, the unit a wearer measures these in. */
    private fun widestPanelRadians(): Float =
        mutable.filter { it.follow.enabled }
            .maxOfOrNull { 2f * it.angularHalfWidthRadians } ?: 0f

    private fun hasLeftTheBand(headYawRadians: Float): Boolean {
        if (!followsHead) return false
        bandExcess = bandExcessAround(anchorYaw, headYawRadians) ?: return false
        return bandExcess > 0f
    }

    /** How far past the band hung from [anchor] the head is; negative is inside. */
    private fun bandExcessAround(anchor: Float, headYawRadians: Float): Float? {
        val followers = mutable.filter { it.follow.enabled }
        if (followers.isEmpty()) return null

        // The extent has to be a span rather than a radius. Measuring it as the
        // largest |slot| assumes the slots are spread symmetrically about
        // forward, which is true of every layout the *layout* produces and false
        // the moment the wearer places a panel by hand: one slot 80 degrees off
        // to the right made the band 80 degrees wide in *both* directions, so
        // the arrangement stopped following at all. A span degenerates to the
        // old radius for a symmetric layout and stays honest for any other.
        val error = ScenePanel.wrapPi(headYawRadians - anchor)
        val margin = toRadians(followMarginDegrees.toDouble()).toFloat()
        val leftEdge = followers.maxOf { it.slotYawRadians + it.angularHalfWidthRadians }
        val rightEdge = followers.minOf { it.slotYawRadians - it.angularHalfWidthRadians }
        return maxOf(
            error - (leftEdge + margin),
            (rightEdge - margin) - error,
        )
    }

    /** A follower's destination once its neighbours have had their say. */
    private class ResolvedTarget(val target: Float, val crossing: Boolean)

    /**
     * Moves a follower's destination clear of anything it may not rest on, and
     * reports whether getting there means crossing over one.
     *
     * Adjusting the *destination* rather than clamping the position is what keeps
     * this smooth: the panel always travels by ordinary following, so a
     * neighbour's influence arrives as a drift and never as a jump.
     *
     * Which side of an obstacle a follower belongs on is decided by **where the
     * wearer is looking**, against that obstacle's own edges:
     *
     * - gaze past the obstacle's far edge, in the direction of travel: swap sides
     * - gaze anywhere on the obstacle: keep the side already committed to
     *
     * Two earlier references were both wrong, and worn they were wrong in
     * interestingly different ways. Deciding from the *destination* relative to
     * the obstacle's edges made the required head turn differ wildly by direction
     * — 57 degrees one way and 7 the other — because which edge counts as "far"
     * depends on the side the panel is parked while the slot offset shifts the
     * destination the same way regardless. Moving to the obstacle's centre fixed
     * the asymmetry but left every threshold displaced by the slot offset, since
     * a wearer judges this by gaze and not by where a panel wants to be.
     *
     * Using gaze against the obstacle's edges gets both: the band is symmetric
     * about the obstacle by construction, and it is the obstacle's own width, so
     * nothing swaps sides while the wearer is looking at the thing being avoided.
     */
    private fun resolveTarget(
        panel: P,
        wanted: Float,
        headYawRadians: Float,
        dt: Float,
    ): ResolvedTarget {
        if (collisionPolicy == CollisionPolicy.OVERLAP) return ResolvedTarget(wanted, false)

        var target = wanted
        var crossing = false

        for (other in mutable) {
            if (other === panel) continue
            // Elevation decides whether there is anything to resolve at all. A
            // panel lifted above the others is not in their way, and a model
            // that only knows headings insists that it is — worn, a panel parked
            // 25 degrees up went on shoving its neighbours aside, and into each
            // other, from somewhere nobody was looking.
            val pitchGap = kotlin.math.abs(panel.pose.pitchRadians - other.pose.pitchRadians)
            if (pitchGap > panel.angularHalfHeightRadians + other.angularHalfHeightRadians) continue

            // A pinned panel is always an obstacle; a follower is one only under
            // AVOID_ALL, and even then a panel the wearer placed by hand does
            // not give way to one the layout merely put somewhere.
            if (!yieldsTo(panel, other)) continue

            if (other.follow.enabled) {
                // Two followers never need to decide a *side*: the layout has
                // already decided it, and their slots hold that order however
                // far the arrangement has moved. Asking the crossing machinery
                // instead compares this panel's destination against the other's
                // live pose while the other is itself travelling — a moving
                // reference, which sent a perfectly ordered pair swapping past
                // each other at crossing speed on every return.
                //
                // The obstacle's *destination* is the honest thing to measure
                // against for the same reason.
                val separation =
                    panel.angularHalfWidthRadians + other.angularHalfWidthRadians + MIN_GAP
                val needed = separation + FOLLOWER_CLEARANCE
                // Where the neighbour actually ends up, which is only the same
                // as where its slot wants it when nothing has pushed it.
                val otherTarget =
                    if (resolvingInPlace) other.pose.yawRadians
                    else parkedTarget[other.id] ?: targetOf(other, headYawRadians)
                val order = ScenePanel.wrapPi(panel.slotYawRadians - other.slotYawRadians)
                val goPositive = if (order != 0f) order > 0f else panel.id > other.id
                val gap = ScenePanel.wrapPi(target - otherTarget)
                if (goPositive && gap < needed) {
                    target = ScenePanel.wrapPi(otherTarget + needed)
                } else if (!goPositive && gap > -needed) {
                    target = ScenePanel.wrapPi(otherTarget - needed)
                }
                continue
            }

            val separation = panel.angularHalfWidthRadians + other.angularHalfWidthRadians + MIN_GAP

            val here = ScenePanel.wrapPi(panel.pose.yawRadians - other.pose.yawRadians)
            val there = ScenePanel.wrapPi(target - other.pose.yawRadians)
            val gaze = ScenePanel.wrapPi(headYawRadians - other.pose.yawRadians)
            val edge = other.angularHalfWidthRadians

            val key = "${panel.id}\u0000${other.id}"
            // Which side this follower *starts out* claiming is the side its own
            // slot puts it on, not the side it happens to be sitting on.
            //
            // Worn: with both followers taking their side from where they were,
            // they always agreed, so they crowded onto one side of the pinned
            // panel and stayed glued together. "The 2 moving panels want to stay
            // together — they should be allowed to be separated." Their slots
            // already say one belongs left and one right; a pinned panel between
            // them is then something they straddle, which is the arrangement the
            // wearer liked: something is always already where they are looking.
            //
            // Gaze still decides *changes* of side, at the obstacle's own edges,
            // so a split happens after the wearer's gaze crosses the pinned
            // panel rather than while they are reading it.
            val committed = committedSide[key] ?: (there >= 0f)

            // A crossing, once begun, finishes. The panel counts as arrived only
            // when it is on its committed side *and clear of the zone*; until
            // then the decision is not revisited, so a head movement part way
            // through cannot turn it around and leave it dithering inside the
            // overlap.
            val clear = kotlin.math.abs(here) >= separation
            val arrived = clear && (here >= 0f) == committed

            // Gaze is the right reference only for an obstacle fixed in the
            // world. Another follower travels with the wearer's head, so the
            // gaze never passes its edges at all — for those, the destination's
            // position relative to the obstacle is what decides the side.
            val reference = if (other.follow.enabled) there else gaze

            val goPositive: Boolean = if (!arrived) {
                committed
            } else if (committed) {
                // Committed to this side: hold it until the reference has passed
                // the obstacle's near edge going the other way.
                reference >= -edge
            } else {
                reference > edge
            }
            committedSide[key] = goPositive

            // Fast until it is *out of the overlap*, not merely past the centre.
            // Reverting to the settling constant at the halfway point left the
            // panels overlapped for seconds while the panel crawled the second
            // half — and gave the wearer's head time to wander back and reverse
            // the decision.
            // Crossing speed is for passing *through* an obstacle, and nothing
            // else. Tying it to the committed side instead meant a follower
            // sixty degrees clear of a pinned panel, on the side its commitment
            // did not name, dashed across the whole field of view at the
            // crossing floor for no visible reason.
            val targetSide = ScenePanel.wrapPi(target - other.pose.yawRadians) >= 0f
            if (!clear || targetSide != (here >= 0f)) crossing = true

            // The side decision and this adjustment are independent: picking a
            // side never excuses coming to rest on the thing being avoided.
            //
            // The extra clearance matters more than it looks: without it the
            // resting place lands exactly on the edge of the overlap, so the
            // panel approaches the moment the overlap ends asymptotically and the
            // last couple of degrees crawl. Parking clear of the boundary is what
            // makes the crossing end crisply.
            // Clearance is what makes a crossing *end* crisply, and a crossing is
            // something a follower does to a panel fixed in the room. Applied
            // between two followers it is simply width the layout does not have:
            // 24.7 degrees of separation plus 6 of clearance is 30.7, wider than
            // the 30 degree slot spacing, so a correctly laid out pair counts as
            // colliding forever and the avoidance machinery fights the layout
            // that placed them.
            val parking = separation +
                if (other.follow.enabled) FOLLOWER_CLEARANCE else CROSSING_CLEARANCE
            if (goPositive && there < parking) {
                target = ScenePanel.wrapPi(other.pose.yawRadians + parking)
            } else if (!goPositive && there > -parking) {
                target = ScenePanel.wrapPi(other.pose.yawRadians - parking)
            }
        }
        // Every obstacle so far has had its say in turn, and the last one to
        // speak wins — which is fine until two of them disagree. Worn: the
        // pinned panel pushed a follower to its right, the follower beyond that
        // pushed it back, and it came to rest exactly on the pinned panel,
        // because the side it had been sent to had no room for it at all. The
        // per-obstacle rules decide *where a panel would like to be*; whether it
        // can actually be there is a question about all of them at once.
        val blocked = mutable.mapNotNull { other ->
            if (other === panel || other === grabbed) return@mapNotNull null
            if (!yieldsTo(panel, other)) return@mapNotNull null
            val pitchGap = kotlin.math.abs(panel.pose.pitchRadians - other.pose.pitchRadians)
            if (pitchGap > panel.angularHalfHeightRadians + other.angularHalfHeightRadians) {
                return@mapNotNull null
            }
            val separation =
                panel.angularHalfWidthRadians + other.angularHalfWidthRadians + MIN_GAP
            val centre = if (other.follow.enabled) {
                if (resolvingInPlace) other.pose.yawRadians
                else parkedTarget[other.id] ?: targetOf(other, headYawRadians)
            } else {
                other.pose.yawRadians
            }
            val half = separation +
                if (other.follow.enabled) FOLLOWER_CLEARANCE else CROSSING_CLEARANCE
            centre to half
        }

        fun feasible(at: Float) = blocked.none { (centre, half) ->
            kotlin.math.abs(ScenePanel.wrapPi(at - centre)) < half - FEASIBLE_EPSILON
        }

        if (!feasible(target)) {
            // The nearest place to the slot that every obstacle can live with.
            // Measured from the slot rather than from where the panel is, so the
            // answer does not depend on the direction it arrived from.
            val candidate = blocked
                .flatMap { (centre, half) ->
                    listOf(ScenePanel.wrapPi(centre + half), ScenePanel.wrapPi(centre - half))
                }
                .filter { feasible(it) }
                .minByOrNull { kotlin.math.abs(ScenePanel.wrapPi(it - wanted)) }
            if (candidate != null) {
                if (kotlin.math.abs(ScenePanel.wrapPi(candidate - target)) > FEASIBLE_EPSILON) {
                    crossing = true
                }
                target = candidate
            }
        }

        // Followers may overlap each other on their way somewhere, but one the
        // scene moved — pushed off a pinned panel, or being resolved clear of
        // one — must not come to rest on another follower, even one placed by
        // hand: worn, the parked panel landed exactly on its neighbour. Only a
        // neighbour already at rest counts, so the panel in motion is the one
        // that yields — worn the other way round, dropping one panel sent the
        // one it displaced across the room.
        val displaced = target != wanted || (resolvingInPlace && panel.id in resolvingIds)
        if (collisionPolicy != CollisionPolicy.OVERLAP && displaced) {
            val occupied = blocked + mutable.mapNotNull { other ->
                if (other === panel || other === grabbed || !other.follow.enabled) return@mapNotNull null
                val pitchGap = kotlin.math.abs(panel.pose.pitchRadians - other.pose.pitchRadians)
                if (pitchGap > panel.angularHalfHeightRadians + other.angularHalfHeightRadians) {
                    return@mapNotNull null
                }
                val centre = other.pose.yawRadians
                val previous = previousYaw[other.id] ?: centre
                if (kotlin.math.abs(ScenePanel.wrapPi(centre - previous)) > AT_REST) return@mapNotNull null
                centre to panel.angularHalfWidthRadians + other.angularHalfWidthRadians + MIN_GAP + FOLLOWER_CLEARANCE
            }
            fun free(at: Float) = occupied.none { (centre, half) ->
                kotlin.math.abs(ScenePanel.wrapPi(at - centre)) < half - FEASIBLE_EPSILON
            }
            if (!free(target)) {
                occupied
                    .flatMap { (centre, half) -> listOf(ScenePanel.wrapPi(centre + half), ScenePanel.wrapPi(centre - half)) }
                    .filter { free(it) }
                    .minByOrNull { kotlin.math.abs(ScenePanel.wrapPi(it - wanted)) }
                    ?.let {
                        crossing = true
                        target = it
                    }
            }
        }

        return ResolvedTarget(target, crossing)
    }

    /**
     * Where a cursor moved by relative steps may go: every window together with
     * the title bar under it, which a cursor kept to the windows alone could
     * never reach. Anything else that can be clicked belongs here too.
     */
    fun cursorReach(): List<AngularRect> =
        windowReach() + listOfNotNull(dock, launcher?.takeIf { launcherShown }, popup?.takeIf { popupShown }).map { it.angularRect() }

    private fun windowReach(): List<AngularRect> = mutable.map { panel ->
        val pose = panel.pose
        val metresPerPx = pose.widthMetres / panel.widthPx
        val distance = pose.distanceMetres
        val handles = ResizeHandles.REACH_OUTSIDE_PX
        val below = maxOf(TitleBar.GAP_PX + TitleBar.HEIGHT_PX, handles)
        AngularRect(
            yaw = pose.yawRadians,
            halfWidth = atan((panel.widthPx / 2f + handles) * metresPerPx / distance),
            bottom = pose.pitchRadians - atan((panel.heightPx / 2f + below) * metresPerPx / distance),
            top = pose.pitchRadians + atan((panel.heightPx / 2f + handles) * metresPerPx / distance),
        )
    }

    /** The resize the wearer is dragging, if any. */
    var resizing: ResizeDrag<P>? = null
        private set

    private class ResizeAnimation(
        val fromWidth: Float,
        val fromHeight: Float,
        val fromPitch: Float,
        val fromYaw: Float,
        val toWidth: Float,
        val toHeight: Float,
        val toPitch: Float,
        val toYaw: Float,
        val movesYaw: Boolean,
    ) {
        var progress = 0f
    }

    private val resizeAnimations = HashMap<P, ResizeAnimation>()

    fun limitsOf(panel: P): SizeLimits = panel.sizeLimits ?: SizeLimits.defaultFor(panel.widthPx, panel.heightPx)

    fun beginResize(panel: P, corner: Corner): Boolean {
        if (panel !in mutable || grabbed != null) return false
        resizing = ResizeDrag(panel, corner)
        return true
    }

    /** Takes the size the pointer now asks for, within the window's limits. Called once per frame. */
    fun updateResize(pointerDirection: Vec3) {
        val drag = resizing ?: return
        val panel = drag.panel
        val point = pointOnPanel(panel, pointerDirection) ?: return
        val fixed = drag.corner.opposite.pointPx(panel.widthPx.toFloat(), panel.heightPx.toFloat())
        val limits = limitsOf(panel)
        drag.widthPx = kotlin.math.round(limits.clampWidth((point.x - fixed.x) * drag.corner.sx)).toInt()
        drag.heightPx = kotlin.math.round(limits.clampHeight((fixed.y - point.y) * drag.corner.sy)).toInt()
    }

    fun cancelResize() {
        resizing = null
    }

    /** Ends the drag, for the platform to resize the engine and then call [resized]. */
    fun endResize(): ResizeDrag<P>? = resizing.also { resizing = null }

    /**
     * Takes up a window's new pixel size, which the platform has just given its
     * engine, keeping [fixed] — the corner opposite the one dragged — where it
     * was. The window eases from its old size to the new rather than jumping;
     * a follower gets there through its slot, as anything else it does.
     */
    fun resized(panel: P, oldWidthPx: Int, oldHeightPx: Int, fixed: Corner, keepCentreX: Boolean = false) {
        if (panel !in mutable) return
        val pose = panel.pose
        val metresPerPx = pose.widthMetres / oldWidthPx
        val width = panel.widthPx * metresPerPx
        val height = panel.heightPx * metresPerPx
        val distance = pose.distanceMetres
        // A window's edges are at its heading plus and minus half its angle, so
        // keeping one where it was is a matter of the change in that half-angle.
        fun halfAngle(metres: Float) = atan(metres / 2f / distance)
        // Or, for a change of shape rather than a drag, the horizontal centre.
        val yawShift = if (keepCentreX) 0f else -fixed.sx * (halfAngle(pose.widthMetres) - halfAngle(width))
        val pitchShift = fixed.sy * (halfAngle(pose.heightMetres) - halfAngle(height))
        resizeAnimations[panel] = ResizeAnimation(
            fromWidth = pose.widthMetres,
            fromHeight = pose.heightMetres,
            fromPitch = pose.pitchRadians,
            fromYaw = pose.yawRadians,
            toWidth = width,
            toHeight = height,
            toPitch = pose.pitchRadians + pitchShift,
            toYaw = ScenePanel.wrapPi(pose.yawRadians + yawShift),
            movesYaw = !panel.follow.enabled,
        )
        if (panel.follow.enabled) {
            panel.assignSlot(ScenePanel.wrapPi(panel.slotYawRadians + yawShift), byHand = panel.slotPlacedByHand)
            requestReturn()
        }
    }

    /** True while a window is easing to a new size; its old picture is what is shown stretched. */
    fun isAnimatingResize(panel: P): Boolean = panel in resizeAnimations

    private fun advanceResizeAnimations(dt: Float) {
        if (resizeAnimations.isEmpty()) return
        val done = mutableListOf<P>()
        for ((panel, animation) in resizeAnimations) {
            animation.progress = (animation.progress + dt / RESIZE_SECONDS).coerceAtMost(1f)
            val t = 1f - (1f - animation.progress).let { it * it * it }
            fun lerp(a: Float, b: Float) = a + (b - a) * t
            panel.pose = panel.pose.copy(
                widthMetres = lerp(animation.fromWidth, animation.toWidth),
                heightMetres = lerp(animation.fromHeight, animation.toHeight),
                pitchRadians = lerp(animation.fromPitch, animation.toPitch),
                yawRadians = if (animation.movesYaw) {
                    ScenePanel.wrapPi(animation.fromYaw + ScenePanel.wrapPi(animation.toYaw - animation.fromYaw) * t)
                } else {
                    panel.pose.yawRadians
                },
            )
            if (animation.progress >= 1f) done += panel
        }
        done.forEach { resizeAnimations.remove(it) }
    }

    /** Where a ray meets [panel]'s plane, in its pixels, even outside the window; null if it points away. */
    fun pointOnPanel(panel: P, worldDirection: Vec3): PanelHit? {
        val length = worldDirection.length
        if (length < 1e-6f) return null
        val pose = panel.pose
        val local = intoPanelFrame(worldDirection * (1f / length), pose.yawRadians, pose.pitchRadians)
        if (local.z >= -1e-4f) return null
        val t = -pose.distanceMetres / local.z
        val u = (local.x * t) / pose.widthMetres + 0.5f
        val v = 0.5f - (local.y * t) / pose.heightMetres
        return PanelHit(u * panel.widthPx, v * panel.heightPx)
    }

    private fun P.angularRect() = AngularRect(
        yaw = pose.yawRadians,
        halfWidth = angularHalfWidthRadians,
        bottom = pose.pitchRadians - angularHalfHeightRadians,
        top = pose.pitchRadians + angularHalfHeightRadians,
    )

    /** The dock: open windows, the time, the battery, and the way into the launcher. */
    var dock: P? = null
        private set

    /** Opened from the dock, and shown on its own while no window is open. */
    var launcher: P? = null
        private set

    var dockVisibility: DockVisibility = DockVisibility.ALWAYS

    /** Whether the launcher has been asked for. With no window open it shows regardless. */
    var launcherRequested = false

    val launcherShown: Boolean get() = launcher != null && (launcherRequested || mutable.isEmpty())

    /**
     * Where typing goes: a popup while it is up, then the launcher while it is up, since it sits over
     * everything and is what the wearer is using, and otherwise the focused
     * window. Focus itself never moves to an overlay.
     */
    val keyTarget: P? get() = popup?.takeIf { popupShown } ?: launcher?.takeIf { launcherShown } ?: focused

    var dockShown = true
        private set

    /** Fade between hidden and shown, for drawing; hit testing goes by the shown state. */
    var dockOpacity = 1f
        private set
    var launcherOpacity = 0f
        private set

    /**
     * Hides every window while the pointer is on none of them, once it has
     * been off them for [windowsHideDelaySeconds]; pointing where one is
     * brings them all back at once. The pointer alone decides, not the gaze,
     * and the overlays are not windows.
     */
    var windowsAutoHide = false

    /** Whether title bars carry the button that cycles a window through preset shapes. */
    var shapeButtons = false

    var windowsHideDelaySeconds = 1f

    var windowsShown = true
        private set

    /** As [dockOpacity], for every window; hidden windows are still hit tested, which is how they come back. */
    var windowsOpacity = 1f
        private set

    private var pointerOffWindowsSeconds = 0f

    /** The head's yaw, lightly smoothed, so the overlays sit in front without trembling with it. */
    private var overlayYaw: Float? = null

    /** The direction of the last hit test, which is where the pointer is. */
    private var lastAim: Vec3? = null

    /** Where the head is looking up or down; the dock answers the gaze as well as the pointer. */
    var gazePitchRadians: Float? = null

    /**
     * Gives the scene its overlays. They are not part of the arrangement — they
     * do not follow, collide or take part in layout — and are drawn and hit
     * tested above every window.
     */
    fun setOverlays(dock: P?, launcher: P?) {
        this.dock = dock
        this.launcher = launcher
    }

    /** A context menu, shown over a point on a window or an overlay, above everything else. */
    var popup: P? = null
        private set

    var popupShown = false
        private set

    /** The panel the popup was opened over, and where on it, in its pixels. */
    private var popupParent: P? = null
    private var popupAtX = 0f
    private var popupAtY = 0f

    fun setPopupPanel(panel: P?) {
        popup = panel
        if (panel == null) hidePopup()
    }

    /**
     * Shows the popup with its top left corner at ([xPx], [yPx]) on [parent],
     * moved left or up so it stays on the parent where there is room, and kept
     * there while the parent moves. It takes the parent's pixel size, so text
     * in it reads at the same size.
     */
    fun showPopup(parent: P, xPx: Float, yPx: Float) {
        popup ?: return
        popupParent = parent
        popupAtX = xPx
        popupAtY = yPx
        popupShown = true
        placePopup()
    }

    fun hidePopup() {
        popupShown = false
        popupParent = null
    }

    /** True while the popup is shown over [panel]. */
    fun popupOver(panel: P): Boolean = popupShown && popupParent === panel

    private fun placePopup() {
        val popup = popup ?: return
        val parent = popupParent ?: return
        if (parent !in mutable && parent !== dock && parent !== launcher) {
            hidePopup()
            return
        }
        val pose = parent.pose
        val metresPerPx = pose.widthMetres / parent.widthPx
        val width = popup.widthPx.toFloat()
        val height = popup.heightPx.toFloat()
        fun fit(at: Float, size: Float, room: Float) = when {
            at + size <= room -> at
            at - size >= 0f -> at - size
            else -> (room - size).coerceAtLeast(0f)
        }
        val left = fit(popupAtX, width, parent.widthPx.toFloat())
        val top = fit(popupAtY, height, parent.heightPx.toFloat())
        val distance = pose.distanceMetres
        val right = (left + width / 2f - parent.widthPx / 2f) * metresPerPx
        val down = (top + height / 2f - parent.heightPx / 2f) * metresPerPx
        popup.pose = pose.copy(
            yawRadians = ScenePanel.wrapPi(pose.yawRadians - atan(right / distance)),
            pitchRadians = pose.pitchRadians - atan(down / distance),
            widthMetres = width * metresPerPx,
            heightMetres = height * metresPerPx,
        )
    }

    /** Gives a window focus without the pointer going to it, as a click in the dock does. */
    fun focus(panel: P) {
        if (panel !in mutable) return
        focused = panel
        lastGazed = panel
    }

    /**
     * Brings the arrangement round so that [panel] is in front of the wearer: an
     * arrangement return, not a recentre, so everything keeps its place relative
     * to everything else. A pinned window stays where it was put.
     */
    fun centreOn(panel: P, headYawRadians: Float) {
        if (panel !in mutable || !panel.follow.enabled) return
        arrangementReturning = true
        arrangementCommanded = true
        resolvingInPlace = false
        returnTracksHead = false
        returningToOrigin = false
        anchorTarget = headYawRadians
        returnOffset = -panel.slotYawRadians
        centring = panel
        centringHeadYaw = headYawRadians
        centringPasses = 0
    }

    /**
     * The window a [centreOn] is bringing round, checked once the arrangement
     * settles. Its slot is only where it *wants* to be: a neighbour placed by
     * hand can keep it pushed aside, and that push moves with the arrangement,
     * while a pinned window's push stays behind. Which one applies depends on
     * where it ends up, so the honest answer is to look once it has arrived
     * and correct by what is left.
     */
    private var centring: P? = null
    private var centringHeadYaw = 0f
    private var centringPasses = 0

    /** True when a [centreOn] still needs another pass; the return then carries on. */
    private fun correctCentring(): Boolean {
        val panel = centring ?: return false
        if (panel !in mutable || centringPasses >= MAX_CENTRING_PASSES) {
            centring = null
            return false
        }
        val error = ScenePanel.wrapPi(centringHeadYaw - panel.pose.yawRadians)
        if (kotlin.math.abs(error) <= CENTRED_RADIANS) {
            centring = null
            return false
        }
        centringPasses++
        anchorTarget = ScenePanel.wrapPi(anchorTarget + returnOffset + error)
        returnOffset = 0f
        return true
    }

    /**
     * Places the dock below the lowest window, title bar included, centred on
     * the head's yaw, with the launcher above it; and decides whether the dock
     * shows. With no window open the launcher sits centred on the horizon.
     */
    private fun advanceOverlays(headYawRadians: Float, dt: Float) {
        val dock = dock ?: return
        val previous = overlayYaw
        val yaw = if (previous == null || dt <= 0f) headYawRadians
        else ScenePanel.wrapPi(previous + ScenePanel.wrapPi(headYawRadians - previous) * (dt / OVERLAY_FOLLOW_SECONDS).coerceAtMost(1f))
        overlayYaw = yaw

        val windowsBottom = windowReach().minOfOrNull { it.bottom }
        val dockHalf = dock.angularHalfHeightRadians
        val launcherHalf = launcher?.angularHalfHeightRadians ?: 0f
        val resting = -(launcherHalf + LAUNCHER_GAP + dockHalf)
        val dockPitch = if (windowsBottom == null) resting else minOf(resting, windowsBottom - DOCK_GAP - dockHalf)
        dock.pose = dock.pose.copy(yawRadians = yaw, pitchRadians = dockPitch)
        launcher?.let {
            it.pose = it.pose.copy(yawRadians = yaw, pitchRadians = dockPitch + dockHalf + LAUNCHER_GAP + launcherHalf)
        }

        dockShown = when {
            windowsBottom == null || launcherShown || dockVisibility == DockVisibility.ALWAYS -> true
            grabbed != null -> dockShown
            else -> {
                val aim = lastAim
                val pitches = listOfNotNull(aim?.let { aimAngles(it)?.pitch }, gazePitchRadians)
                when {
                    aim != null && intersect(dock, aim, chrome = false) != null -> true
                    pitches.isEmpty() -> dockShown
                    pitches.any { it < windowsBottom } -> true
                    pitches.all { it > windowsBottom + REVEAL_HYSTERESIS } -> false
                    else -> dockShown
                }
            }
        }
        val fade = if (dt > 0f) dt / OVERLAY_FADE_SECONDS else 1f
        dockOpacity = approachLinearly(dockOpacity, if (dockShown) 1f else 0f, fade)
        launcherOpacity = approachLinearly(launcherOpacity, if (launcherShown) 1f else 0f, fade)
        advanceWindowsVisibility(dt, fade)
    }

    private fun advanceWindowsVisibility(dt: Float, fade: Float) {
        val aim = lastAim
        val held = !windowsAutoHide || grabbed != null || resizing != null || launcherShown || popupShown
        val pointed = aim != null && mutable.any { intersect(it, aim) != null }
        pointerOffWindowsSeconds = if (held || pointed || aim == null) 0f else pointerOffWindowsSeconds + dt
        windowsShown = held || pointed || pointerOffWindowsSeconds < windowsHideDelaySeconds
        windowsOpacity = approachLinearly(windowsOpacity, if (windowsShown) 1f else 0f, fade)
    }

    private fun approachLinearly(value: Float, target: Float, step: Float) =
        if (value < target) minOf(target, value + step) else maxOf(target, value - step)

    /**
     * Back to front, the order panels are painted in.
     *
     * Every panel sits at the one distance the optics allow, so where two
     * overlap their planes cross, and a depth test would show each in front on
     * its own side of the crossing — worn, as clipping and z-fighting. Painting
     * in order instead puts a whole panel on top. Nearer is still nearer; at
     * the same distance the one being held goes on top, then the one with focus.
     */
    fun drawOrder(): List<P> {
        val focus = focused
        val held = grabbed
        return mutable.sortedWith(
            compareBy<P>({ -it.pose.distanceMetres }, { it === held }, { it === focus }),
        )
    }

    /**
     * Intersects a ray from the origin with every panel and returns the one
     * painted on top where it landed, per [drawOrder].
     *
     * Returns null when the ray hits nothing, so "the pointer is off every
     * panel" is a representable state rather than a clamped edge hit — and also
     * sets focus, since focus follows the pointer.
     */
    fun hitTest(worldDirection: Vec3): PanelSceneHit<P>? {
        val length = worldDirection.length
        if (length < 1e-6f) return null
        val direction = worldDirection * (1f / length)
        lastAim = direction

        // Overlays are above everything, and never take focus: focus is the
        // window input goes to, and the dock is how the wearer chooses it.
        listOfNotNull(popup?.takeIf { popupShown }, launcher?.takeIf { launcherShown }, dock?.takeIf { dockShown })
            .firstNotNullOfOrNull { intersect(it, direction, chrome = false) }
            ?.let { return it }

        // The panel painted on top wins, not the nearest plane: a click has to
        // land on what the wearer can see.
        val top = drawOrder().asReversed().firstNotNullOfOrNull { intersect(it, direction) }
        if (!focusHeld) {
            top?.let {
                focused = it.panel
                if (headDegreesPerSecond < LOOKING_DEGREES_PER_SECOND) lastGazed = it.panel
            }
        }
        return top
    }

    /**
     * Ray against one panel's plane, in the panel's own frame.
     *
     * The panel's transform is yaw, then pitch, then a translation out to its
     * distance, so undoing the two rotations puts its plane at `z = -distance`
     * and the intersection is a division rather than a general ray/triangle
     * test.
     */
    private fun intersect(panel: P, direction: Vec3, chrome: Boolean = true): PanelSceneHit<P>? {
        val pose = panel.pose
        val local = intoPanelFrame(direction, pose.yawRadians, pose.pitchRadians)
        if (local.z >= -1e-4f) return null

        val t = -pose.distanceMetres / local.z
        if (t <= 0f) return null

        val u = (local.x * t) / pose.widthMetres + 0.5f
        val v = 0.5f - (local.y * t) / pose.heightMetres
        val x = u * panel.widthPx
        val y = v * panel.heightPx
        val part = if (u in 0f..1f && v in 0f..1f) HitPart.CONTENT
        else if (chrome) {
            TitleBar.partAt(panel.widthPx, panel.heightPx, x, y, panel.backButton, shapeButtons)
                ?: ResizeHandles.cornerAt(panel.widthPx, panel.heightPx, x, y)?.let(HitPart::resizing)
                ?: return null
        } else return null

        return PanelSceneHit(panel = panel, hit = PanelHit(x, y), distanceMetres = t, part = part)
    }

    /** A pointing direction resolved into the angles a panel pose is authored in. */
    private class Aim(val yaw: Float, val pitch: Float)

    /**
     * Where a direction points, in the same yaw and pitch a [PanelPose] uses.
     *
     * A panel's transform is yaw about +Y then pitch about +X then a
     * translation out along -Z, so a panel at (yaw, pitch) sits in the direction
     * `(-cos p sin y, sin p, -cos p cos y)`. Inverting that is what makes
     * "put the panel where I am pointing" a matter of arithmetic rather than of
     * a ray test against a sphere.
     */
    private fun aimAngles(direction: Vec3): Aim? {
        val length = direction.length
        if (length < 1e-6f) return null
        val d = direction * (1f / length)
        return Aim(
            yaw = kotlin.math.atan2(-d.x, -d.z),
            pitch = kotlin.math.asin(d.y.coerceIn(-1f, 1f)),
        )
    }

    companion object {
        /** How close to straight ahead a centred window has to end up, and how many tries it gets. */
        private val CENTRED_RADIANS = toRadians(1.0).toFloat()
        private const val MAX_CENTRING_PASSES = 2

        /** How long a window takes to ease into a new size. */
        private const val RESIZE_SECONDS = 0.22f

        /** How quickly the overlays turn to face the head. */
        private const val OVERLAY_FOLLOW_SECONDS = 0.15f

        /** How long the dock and launcher take to fade in or out. */
        private const val OVERLAY_FADE_SECONDS = 0.15f

        /** Clear space between the lowest window's title bar and the dock, and the dock and the launcher. */
        private val DOCK_GAP = toRadians(1.6).toFloat()
        private val LAUNCHER_GAP = toRadians(0.8).toFloat()

        /** How far back above the windows the pointer has to go before an auto-hidden dock hides again. */
        private val REVEAL_HYSTERESIS = toRadians(1.5).toFloat()

        /** Slop when asking whether a place is clear, in radians. */
        private val FEASIBLE_EPSILON = toRadians(0.01).toFloat()

        /** Close enough for the arrangement to stop moving, in radians. */
        private val SETTLED_RADIANS = toRadians(0.5).toFloat()

        /** Keeps avoided panels from sitting edge to edge, in radians. */
        private val MIN_GAP = toRadians(2.0).toFloat()

        /**
         * Below this the head is resting on what it sees. Stopping on a panel
         * gets under it within a frame or two; sweeping across one on the way
         * somewhere else does not.
         */
        private const val LOOKING_DEGREES_PER_SECOND = 30f

        /**
         * How far past the band a drift needs, on top of the band's own margin.
         * Overshooting the last panel by a few degrees is aiming at it.
         */
        private val DRIFT_MIN_EXCESS = toRadians(6.0).toFloat()

        /** Long enough to correct an overshoot, short enough not to read as a delay. */
        private const val DRIFT_SETTLE_SECONDS = 0.3f

        /** Short against the anchor's 2.5 s, so the panel is where the anchor is. */
        private const val TIGHT_TRACKING_SECONDS = 0.1f

        /** How far back inside the original band a glance has to come to undo a return. */
        private val BACK_TO_ORIGIN_HYSTERESIS = toRadians(3.0).toFloat()

        /** Keeps a parked follower's resting place off the overlap boundary. */
        private val CROSSING_CLEARANCE = toRadians(6.0).toFloat()

        /**
         * The same, between two followers — enough to be off the boundary and no
         * more, because both of them are also being placed by the layout.
         */
        private val FOLLOWER_CLEARANCE = toRadians(2.0).toFloat()

        /** Less than this in one step and a follower counts as resting. */
        private val AT_REST = toRadians(0.05).toFloat()

        /**
         * How far above or below eye level a panel may be dragged.
         *
         * There are only 19 degrees of vertical field, most of it spent by the
         * panel itself, so a drag that ran to the limits of the wearer's neck
         * would put the panel somewhere they cannot look at without losing it
         * off the top of the display.
         */
        private val MAX_PLACEMENT_PITCH = toRadians(25.0).toFloat()


        /** Undoes a panel's heading and elevation, so its plane is `z = -d`. */
        fun intoPanelFrame(direction: Vec3, yawRadians: Float, pitchRadians: Float): Vec3 {
            val cy = kotlin.math.cos(yawRadians)
            val sy = kotlin.math.sin(yawRadians)
            val x = cy * direction.x - sy * direction.z
            val zYaw = sy * direction.x + cy * direction.z

            if (pitchRadians == 0f) return Vec3(x, direction.y, zYaw)

            val cp = kotlin.math.cos(pitchRadians)
            val sp = kotlin.math.sin(pitchRadians)
            return Vec3(
                x,
                cp * direction.y + sp * zYaw,
                -sp * direction.y + cp * zYaw,
            )
        }
    }
}
