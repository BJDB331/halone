package dev.halone.compositor

import dev.halone.glasses.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneTest {

    private fun forward() = Vec3(0f, 0f, -1f)

    /** Straight ahead lands in the middle of a panel straight ahead. */
    @Test
    fun `gaze forward hits the centre of a forward panel`() {
        val scene = Scene().apply { add(panel("a")) }

        val hit = scene.hitTest(forward())

        assertNotNull(hit)
        assertEquals(640f, hit!!.hit.x, 0.5f)
        assertEquals(400f, hit.hit.y, 0.5f)
        assertEquals(4f, hit.distanceMetres, 1e-3f)
    }

    @Test
    fun `a ray past the panel edge misses rather than clamping`() {
        val scene = Scene().apply { add(panel("a")) }

        // The panel is 1.28 m wide at 4 m, so its edge is at ~9.1 degrees.
        assertNull(scene.hitTest(rayAtYaw(15f)))
        assertNotNull(scene.hitTest(rayAtYaw(8f)))
    }

    @Test
    fun `a ray behind the viewer misses`() {
        val scene = Scene().apply { add(panel("a")) }

        assertNull(scene.hitTest(Vec3(0f, 0f, 1f)))
    }

    /** A panel rotated to a slot is hit by a gaze turned to that slot. */
    @Test
    fun `hit testing follows a panel to its slot`() {
        val scene = Scene().apply { add(panel("a", yawDegrees = 30f)) }

        assertNull(scene.hitTest(forward()))

        val hit = scene.hitTest(rayAtYaw(30f))
        assertNotNull(hit)
        assertEquals(640f, hit!!.hit.x, 2f)
        assertEquals(400f, hit.hit.y, 2f)
    }

    @Test
    fun `elevation is undone the same way as heading`() {
        val scene = Scene().apply { add(panel("a", pitchDegrees = -20f)) }

        assertNull(scene.hitTest(forward()))

        val hit = scene.hitTest(rayAtPitch(-20f))
        assertNotNull(hit)
        assertEquals(400f, hit!!.hit.y, 2f)
    }

    /**
     * The cursor must never be drawn on a panel that something else is in front
     * of, which is the whole reason the hit test reports a distance.
     */
    @Test
    fun `overlapping panels resolve to the nearer one`() {
        val near = panel("near", distanceMetres = 2f, widthMetres = 0.64f, heightMetres = 0.4f)
        val far = panel("far", distanceMetres = 4f)
        val scene = Scene().apply { add(far); add(near) }

        val hit = scene.hitTest(forward())

        assertSame(near, hit!!.panel)
        assertEquals(2f, hit.distanceMetres, 1e-3f)
    }

    @Test
    fun `focus follows the pointer`() {
        val left = panel("left", yawDegrees = 30f)
        val right = panel("right", yawDegrees = -30f)
        val scene = Scene().apply { add(left); add(right) }

        scene.hitTest(rayAtYaw(30f))
        assertSame(left, scene.focused)

        scene.hitTest(rayAtYaw(-30f))
        assertSame(right, scene.focused)

        // A miss leaves focus where it was, so looking away does not orphan
        // whatever the wearer was working in.
        scene.hitTest(rayAtYaw(90f))
        assertSame(right, scene.focused)
    }

    @Test
    fun `a cylindrical layout centres the panels on forward`() {
        val scene = Scene()
        val a = scene.add(panel("a"))
        val b = scene.add(panel("b"))
        val c = scene.add(panel("c"))

        scene.layOutCylindrical(spacingDegrees = 30f)

        assertEquals(-30f, degrees(a.pose.yawRadians), 1e-3f)
        assertEquals(0f, degrees(b.pose.yawRadians), 1e-3f)
        assertEquals(30f, degrees(c.pose.yawRadians), 1e-3f)
        // Each panel is hit by a gaze turned to its own slot.
        assertSame(a, scene.hitTest(rayAtYaw(-30f))!!.panel)
        assertSame(c, scene.hitTest(rayAtYaw(30f))!!.panel)
    }

    @Test
    fun `removing the focused panel moves focus rather than leaving it dangling`() {
        val scene = Scene()
        val a = scene.add(panel("a"))
        val b = scene.add(panel("b", yawDegrees = 30f))

        scene.hitTest(rayAtYaw(30f))
        assertSame(b, scene.focused)

        scene.remove(b)
        assertSame(a, scene.focused)

        scene.remove(a)
        assertNull(scene.focused)
    }

    @Test
    fun `recentring returns every panel to its slot`() {
        val scene = Scene()
        val followed = scene.add(panel("followed"))
        followed.moveToSlot(Math.toRadians(20.0).toFloat())
        // Simulate follow having dragged it away from its slot.
        repeat(60) { followed.advanceFollow(Math.toRadians(90.0).toFloat(), dt = 0.1f) }
        assertTrue(degrees(followed.pose.yawRadians) > 25f)

        scene.recentre()

        assertEquals(20f, degrees(followed.pose.yawRadians), 1e-3f)
    }

    /**
     * A ray turned by [degrees] of yaw. Positive is a left turn: yaw is about
     * canonical +Y in a right-handed frame, so a panel at +30 degrees sits to
     * the wearer's left.
     */
    private fun rayAtYaw(degrees: Float): Vec3 {
        val a = Math.toRadians(degrees.toDouble())
        return Vec3(-kotlin.math.sin(a).toFloat(), 0f, -kotlin.math.cos(a).toFloat())
    }

    private fun rayAtPitch(degrees: Float): Vec3 {
        val a = Math.toRadians(degrees.toDouble())
        return Vec3(0f, kotlin.math.sin(a).toFloat(), -kotlin.math.cos(a).toFloat())
    }

    private fun degrees(radians: Float) = Math.toDegrees(radians.toDouble()).toFloat()

    private fun rad(degrees: Float) = Math.toRadians(degrees.toDouble()).toFloat()

    private fun deg(radians: Float) = Math.toDegrees(radians.toDouble()).toFloat()

    /**
     * The complaint that prompted the policy: a follower rode straight over the
     * panel the wearer had deliberately pinned.
     */
    @Test
    fun `a follower clamps against a pinned panel instead of covering it`() {
        val scene = Scene()
        val follower = scene.add(panel("follower")).apply { moveToSlot(rad(-15f)) }
        val pinned = scene.add(panel("pinned", follow = SmoothFollow.PINNED))
            .apply { moveToSlot(rad(15f)) }
        val pinnedYaw = pinned.pose.yawRadians

        // 42 degrees is the turn that puts the follower's resting place exactly
        // on the pinned panel: slot -15, deadzone 12, so it settles at +15.
        repeat(60 * 20) { scene.advanceFollow(rad(42f), dt = 1f / 60f) }

        assertEquals("the pinned panel must not have moved", pinnedYaw, pinned.pose.yawRadians, 1e-6f)
        val separation = kotlin.math.abs(
            deg(Panel.wrapPi(follower.pose.yawRadians - pinned.pose.yawRadians))
        )
        val minimum = deg(follower.angularHalfWidthRadians + pinned.angularHalfWidthRadians)
        assertTrue(
            "follower at %.1f° from the pinned panel, needs at least %.1f°".format(separation, minimum),
            separation >= minimum,
        )
    }

    /**
     * Avoidance must not strand the follower. In angle there is no way around an
     * obstacle, so a follower that merely clamped would stick to the pinned
     * panel's edge for as long as the wearer kept turning — breaking the one
     * promise following makes. It crosses over instead, and is pushed clear only
     * once it arrives.
     */
    @Test
    fun `a follower crosses a pinned panel rather than sticking to it`() {
        val scene = Scene()
        val follower = scene.add(panel("follower")).apply { moveToSlot(rad(-15f)) }
        scene.add(panel("pinned", follow = SmoothFollow.PINNED)).apply { moveToSlot(rad(15f)) }

        // A big turn, whose destination is well past the pinned panel.
        val headYaw = 120f
        repeat(60 * 30) { scene.advanceFollow(rad(headYaw), dt = 1f / 60f) }

        val target = Panel.wrapPi(rad(headYaw) + follower.slotYawRadians)
        val error = kotlin.math.abs(deg(Panel.wrapPi(target - follower.pose.yawRadians)))
        assertTrue("the follower should have come along, %.1f° short".format(error), error <= 13f)
    }

    @Test
    fun `the overlap policy is the old behaviour, kept switchable`() {
        val scene = Scene().apply { collisionPolicy = CollisionPolicy.OVERLAP }
        val follower = scene.add(panel("follower")).apply { moveToSlot(rad(-15f)) }
        val pinned = scene.add(panel("pinned", follow = SmoothFollow.PINNED))
            .apply { moveToSlot(rad(15f)) }

        repeat(60 * 20) { scene.advanceFollow(rad(42f), dt = 1f / 60f) }

        val separation = kotlin.math.abs(
            deg(Panel.wrapPi(follower.pose.yawRadians - pinned.pose.yawRadians))
        )
        val needed = deg(follower.angularHalfWidthRadians + pinned.angularHalfWidthRadians)
        assertTrue(
            "without a policy it comes to rest overlapping: %.1f° apart, needs %.1f°"
                .format(separation, needed),
            separation < needed,
        )
    }

    /** With AVOID_PINNED, followers may still pass each other freely. */
    @Test
    fun `followers ignore each other only under the overlap policy`() {
        val scene = Scene().apply { collisionPolicy = CollisionPolicy.OVERLAP }
        val a = scene.add(panel("a"))
        val b = scene.add(panel("b"))
        scene.layOutCylindrical(spacingDegrees = 30f)
        // Drag one onto the other's slot, as a grab-and-move would.
        b.moveToSlot(a.slotYawRadians)

        repeat(60 * 5) { scene.advanceFollow(0f, dt = 1f / 60f) }
        assertEquals(a.pose.yawRadians, b.pose.yawRadians, 1e-6f)

        scene.collisionPolicy = CollisionPolicy.AVOID_ALL
        b.moveToSlot(Math.toRadians(40.0).toFloat())
        repeat(60 * 20) { scene.advanceFollow(rad(-40f), dt = 1f / 60f) }

        val separation = kotlin.math.abs(deg(Panel.wrapPi(b.pose.yawRadians - a.pose.yawRadians)))
        assertTrue("followers should keep clear of each other, %.1f°".format(separation), separation > 20f)
    }

    /**
     * Worn, a crossing at settling speed read as the panel resisting, and no
     * time constant fixed the tail — the last degrees of a crossing are the ones
     * still overlapping, and an exponential approach always crawls there. The
     * rate floor is what does the work, so this pins it rather than the constant.
     */
    @Test
    fun `the rate floor is what makes a crossing brisk`() {
        fun secondsToClear(follow: SmoothFollow): Float {
            val scene = Scene()
            val follower = scene.add(panel("follower", follow = follow))
                .apply { moveToSlot(rad(-15f)) }
            val pinned = scene.add(panel("pinned", follow = SmoothFollow.PINNED))
                .apply { moveToSlot(rad(15f)) }

            val clearance = follower.angularHalfWidthRadians + pinned.angularHalfWidthRadians
            val dt = 1f / 60f
            var elapsed = 0f
            repeat(60 * 30) {
                scene.advanceFollow(rad(120f), dt)
                elapsed += dt
                if (Panel.wrapPi(follower.pose.yawRadians - pinned.pose.yawRadians) >= clearance) {
                    return elapsed
                }
            }
            return Float.MAX_VALUE
        }

        val withFloor = secondsToClear(SmoothFollow())
        // As it behaved before: easing all the way, at the settling constant.
        val easingOnly = secondsToClear(
            SmoothFollow(crossingDegreesPerSecond = 0f, crossingTimeConstantSeconds = 2.5f)
        )

        // Under a second, and bounded below by the arrangement's own pace: a
        // panel's destination is its slot relative to the anchor, so a crossing
        // cannot outrun the anchor that carries it. What the floor buys is the
        // difference between this and easing all the way, which is the tail that
        // used to leave two panels overlapped for seconds.
        assertTrue("crossing should be brisk, was %.2fs".format(withFloor), withFloor < 0.9f)
        assertTrue(
            "easing alone should be markedly slower, %.2fs vs %.2fs".format(easingOnly, withFloor),
            easingOnly > 2f * withFloor,
        )
    }

    /**
     * Where the swap happens, in the terms the wearer judges it by: gaze against
     * the pinned panel's own edges.
     *
     * Scanning one way it should swap as the gaze leaves the panel's far corner,
     * and the other way at the opposite corner — symmetric about the panel, with
     * a band exactly as wide as the panel, so nothing swaps while the wearer is
     * looking at it. Two earlier references failed here: the obstacle's far edge
     * measured against the destination (57 degrees one way, 7 the other), and the
     * obstacle's centre (symmetric, but displaced by the follower's slot offset
     * and distracting when read).
     */
    @Test
    fun `the swap happens as the gaze passes the pinned panel's corners`() {
        val pinnedYaw = 18f

        fun swapHeadYaw(from: Float, to: Float): Float {
            val scene = Scene()
            val follower = scene.add(panel("follower")).apply { moveToSlot(rad(-15f)) }
            val pinned = scene.add(panel("pinned", follow = SmoothFollow.PINNED))
                .apply { moveToSlot(rad(pinnedYaw)) }

            repeat(60 * 20) { scene.advanceFollow(rad(from), 1f / 60f) }
            val startedPositive =
                Panel.wrapPi(follower.pose.yawRadians - pinned.pose.yawRadians) >= 0f

            // Sweep slowly, so travel time contributes little to the reading.
            val degreesPerSecond = 10f
            val steps = (kotlin.math.abs(to - from) / degreesPerSecond * 60f).toInt()
            repeat(steps) { i ->
                val headYaw = from + (to - from) * i / steps
                scene.advanceFollow(rad(headYaw), 1f / 60f)
                val nowPositive =
                    Panel.wrapPi(follower.pose.yawRadians - pinned.pose.yawRadians) >= 0f
                if (nowPositive != startedPositive) return headYaw
            }
            return Float.MAX_VALUE
        }

        val edge = deg(panel("probe").angularHalfWidthRadians)
        val swappingLeft = swapHeadYaw(from = -20f, to = 80f)
        val swappingRight = swapHeadYaw(from = 80f, to = -20f)

        // Leaving the panel's left corner going left, its right corner going right.
        assertEquals("swap going left", pinnedYaw + edge, swappingLeft, 6f)
        assertEquals("swap going right", pinnedYaw - edge, swappingRight, 6f)
        // Symmetric about the pinned panel, by construction.
        assertEquals(
            "band should be centred on the pinned panel",
            pinnedYaw,
            (swappingLeft + swappingRight) / 2f,
            3f,
        )
    }

    /**
     * Worn: while the panels were overlapped mid-crossing, a small look back the
     * other way sent the follower dashing to the side it came from.
     *
     * The side decision used to be read back from the follower's own position,
     * which flips halfway through a crossing — so the decision flipped with it.
     * Committing to a side and remembering that commitment is what makes the
     * journey survive a wobble.
     */
    @Test
    fun `a small head movement mid-crossing does not turn the follower around`() {
        val scene = Scene()
        val follower = scene.add(panel("follower")).apply { moveToSlot(rad(-15f)) }
        val pinned = scene.add(panel("pinned", follow = SmoothFollow.PINNED))
            .apply { moveToSlot(rad(18f)) }

        // Settle to the right of the pinned panel.
        repeat(60 * 20) { scene.advanceFollow(rad(-10f), 1f / 60f) }
        assertTrue(Panel.wrapPi(follower.pose.yawRadians - pinned.pose.yawRadians) < 0f)

        // Turn left far enough to commit to a crossing, then stop as soon as the
        // follower is inside the obstacle's zone — mid-journey.
        val zone = follower.angularHalfWidthRadians + pinned.angularHalfWidthRadians
        var enteredZone = false
        repeat(60 * 10) {
            scene.advanceFollow(rad(45f), 1f / 60f)
            if (kotlin.math.abs(Panel.wrapPi(follower.pose.yawRadians - pinned.pose.yawRadians)) < zone) {
                enteredZone = true
                return@repeat
            }
        }
        assertTrue("should have entered the obstacle's zone", enteredZone)

        // Now look slightly back the other way and hold.
        repeat(60 * 20) { scene.advanceFollow(rad(34f), 1f / 60f) }

        assertTrue(
            "the follower should have completed the crossing, at %.1f°".format(
                deg(follower.pose.yawRadians)
            ),
            Panel.wrapPi(follower.pose.yawRadians - pinned.pose.yawRadians) >= zone,
        )
    }

    /**
     * Worn, the overlap lasted about eight seconds. The crossing constant only
     * applied while the panel was on the wrong side of the obstacle, so the
     * second half of every crossing crawled at the settling constant while still
     * fully overlapped.
     */
    @Test
    fun `the overlap is brief from end to end, not just to the halfway point`() {
        val scene = Scene()
        val follower = scene.add(panel("follower")).apply { moveToSlot(rad(-15f)) }
        val pinned = scene.add(panel("pinned", follow = SmoothFollow.PINNED))
            .apply { moveToSlot(rad(18f)) }
        val zone = follower.angularHalfWidthRadians + pinned.angularHalfWidthRadians

        repeat(60 * 20) { scene.advanceFollow(rad(-10f), 1f / 60f) }

        val dt = 1f / 60f
        var overlapSeconds = 0f
        var entered = false
        repeat(60 * 30) {
            scene.advanceFollow(rad(45f), dt)
            val separation = kotlin.math.abs(
                Panel.wrapPi(follower.pose.yawRadians - pinned.pose.yawRadians)
            )
            if (separation < zone) {
                entered = true
                overlapSeconds += dt
            } else if (entered) {
                return@repeat
            }
        }

        assertTrue("should have crossed at all", entered)
        assertTrue(
            "overlap should be brief, was %.2fs".format(overlapSeconds),
            overlapSeconds < 0.6f,
        )
    }

    /**
     * Worn: with one panel pinned, the remaining follower kept the slot it was
     * given when both were moving, so it came to rest off to one side and read as
     * refusing to centre. Pinning frees up the layout; whatever still follows
     * should share it.
     */
    @Test
    fun `pinning a panel re-centres the follower that is left`() {
        val scene = Scene()
        val follower = scene.add(panel("follower"))
        val other = scene.add(panel("other"))
        scene.layOutCylindrical(spacingDegrees = 30f)
        assertEquals(-15f, deg(follower.slotYawRadians), 1e-3f)

        // Pinned well off to one side, so a centred follower has room. A panel
        // pinned near the wearer's forward would legitimately block centring.
        other.moveToSlot(rad(45f))
        scene.setFollow(other, SmoothFollow.PINNED)

        // The only follower left belongs straight ahead of the wearer.
        assertEquals(0f, deg(follower.slotYawRadians), 1e-3f)
        // And it drifts there rather than jumping.
        assertEquals(-15f, deg(follower.pose.yawRadians), 1e-3f)
        repeat(60 * 20) { scene.advanceFollow(0f, 1f / 60f) }
        assertEquals(0f, deg(follower.pose.yawRadians), 1f)
    }

    @Test
    fun `unpinning gives the panel a slot again`() {
        val scene = Scene()
        val a = scene.add(panel("a"))
        val b = scene.add(panel("b"))
        scene.layOutCylindrical(spacingDegrees = 30f)

        scene.setFollow(b, SmoothFollow.PINNED)
        assertEquals(0f, deg(a.slotYawRadians), 1e-3f)

        scene.setFollow(b, SmoothFollow())

        assertEquals(-15f, deg(a.slotYawRadians), 1e-3f)
        assertEquals(15f, deg(b.slotYawRadians), 1e-3f)
    }

    /**
     * Worn: with two followers, looking at either panel dragged the arrangement
     * over so its *gap* sat in front of the wearer — the one place they were not
     * looking. Following is a property of the arrangement, so the band has to be
     * measured from the arrangement's extent, not from each panel's own slot.
     */
    @Test
    fun `looking at either panel does not move the arrangement`() {
        val scene = Scene()
        val left = scene.add(panel("left"))
        val right = scene.add(panel("right"))
        scene.layOutCylindrical(spacingDegrees = 30f)
        val leftYaw = left.pose.yawRadians
        val rightYaw = right.pose.yawRadians

        // Straight at each panel's centre, and out to its outer edge.
        val outerEdge = deg(left.angularHalfWidthRadians) + 15f
        for (headYaw in listOf(0f, 15f, -15f, outerEdge, -outerEdge)) {
            repeat(60 * 5) {
                assertFalse(
                    "nothing should move while looking at %.0f°".format(headYaw),
                    scene.advanceFollow(rad(headYaw), 1f / 60f),
                )
            }
        }
        assertEquals(deg(leftYaw), deg(left.pose.yawRadians), 1e-4f)
        assertEquals(deg(rightYaw), deg(right.pose.yawRadians), 1e-4f)
    }

    /** But looking past the whole arrangement still brings it along. */
    @Test
    fun `looking beyond the arrangement brings it along`() {
        val scene = Scene()
        val left = scene.add(panel("left"))
        scene.add(panel("right"))
        scene.layOutCylindrical(spacingDegrees = 30f)

        repeat(60 * 30) { scene.advanceFollow(rad(60f), 1f / 60f) }

        // Slot -15 or +15 relative to a heading of 60.
        assertEquals(45f, deg(left.pose.yawRadians), 2f)
    }

    private fun dragAndDrop(scene: Scene, panel: Panel, toDegrees: Float, headDegrees: Float = 0f) {
        var at = deg(panel.pose.yawRadians)
        scene.beginGrab(panel, rayAtYaw(at))
        val step = if (toDegrees > at) 1f else -1f
        while ((toDegrees - at) * step > 0f) {
            at += step
            scene.updateGrab(rayAtYaw(at))
            scene.advanceFollow(rad(headDegrees), 1f / 60f)
        }
        scene.endGrab(rad(headDegrees))
    }

    private fun Scene.settle(headDegrees: Float = 0f, seconds: Float = 4f) =
        repeat((seconds * 60).toInt()) { advanceFollow(rad(headDegrees), 1f / 60f) }

    /** Three followers, the third pinned at +30 with the second parked beside it. */
    private fun sceneWithPinned(policy: CollisionPolicy): Triple<Scene, List<Panel>, Panel> {
        val scene = Scene().apply { collisionPolicy = policy }
        val a = scene.add(panel("a"))
        val b = scene.add(panel("b"))
        val c = scene.add(panel("c"))
        scene.layOutCylindrical(spacingDegrees = 30f)
        scene.settle()
        scene.setFollow(c, SmoothFollow.PINNED)
        scene.settle()
        return Triple(scene, listOf(a, b), c)
    }

    private fun assertApart(first: Panel, second: Panel) {
        val gap = kotlin.math.abs(deg(Panel.wrapPi(first.pose.yawRadians - second.pose.yawRadians)))
        val needed = deg(first.angularHalfWidthRadians + second.angularHalfWidthRadians)
        assertTrue("${first.id} and ${second.id} overlap: %.1f° apart, need %.1f°".format(gap, needed), gap >= needed)
    }

    /**
     * Worn with avoid-pinned: dropping one follower on a pinned panel sent the
     * other follower across the room, because resolving the overlap had every
     * parked follower re-decide its side from the gaze.
     */
    @Test
    fun `dropping a follower on a pinned panel leaves the other followers alone`() {
        val (scene, followers, pinned) = sceneWithPinned(CollisionPolicy.AVOID_PINNED)
        val (a, b) = followers
        val bBefore = deg(b.pose.yawRadians)

        dragAndDrop(scene, a, deg(pinned.pose.yawRadians) + 10f)
        scene.settle()

        assertEquals(bBefore, deg(b.pose.yawRadians), 0.5f)
        assertApart(a, pinned)
        assertApart(a, b)
    }

    @Test
    fun `a follower pushed off a pinned panel does not come to rest on another`() {
        for (policy in listOf(CollisionPolicy.AVOID_PINNED, CollisionPolicy.AVOID_ALL)) {
            val (scene, followers, pinned) = sceneWithPinned(policy)
            val (a, b) = followers
            dragAndDrop(scene, a, deg(pinned.pose.yawRadians) + 10f)
            scene.settle()

            dragAndDrop(scene, b, deg(pinned.pose.yawRadians) - 12f)
            scene.settle()
            scene.settle()

            assertApart(a, b)
            assertApart(b, pinned)
            assertApart(a, pinned)
        }
    }

    @Test
    fun `a panel dropped on a pinned one parks on the side it was dropped`() {
        val scene = Scene().apply { collisionPolicy = CollisionPolicy.AVOID_PINNED }
        val follower = scene.add(panel("follower", yawDegrees = -40f))
        val pinned = scene.add(panel("pinned", yawDegrees = 20f))
        scene.setFollow(pinned, SmoothFollow.PINNED)
        scene.settle()

        // Looking where it is put, as a wearer dragging it does.
        dragAndDrop(scene, follower, 28f, headDegrees = 28f)
        scene.settle(headDegrees = 28f)

        assertTrue(deg(follower.pose.yawRadians) > deg(pinned.pose.yawRadians))
        assertApart(follower, pinned)
    }

    @Test
    fun `overlapping panels at one distance are hit where they are painted`() {
        val scene = Scene()
        val a = scene.add(panel("a", yawDegrees = 0f))
        val b = scene.add(panel("b", yawDegrees = 8f))

        // Aimed into the overlap, on the side where b's plane is nearer.
        assertSame(a, scene.hitTest(rayAtYaw(6f))!!.panel)
        assertEquals(listOf(b, a), scene.drawOrder())

        scene.hitTest(rayAtYaw(14f))
        assertSame(b, scene.hitTest(rayAtYaw(2f))!!.panel)
        assertEquals(listOf(a, b), scene.drawOrder())
    }

    @Test
    fun `a nearer panel is still painted over a farther one with focus`() {
        val scene = Scene()
        val far = scene.add(panel("far", distanceMetres = 6f))
        val near = scene.add(panel("near", distanceMetres = 3f))

        assertSame(far, scene.focused)
        assertEquals(listOf(far, near), scene.drawOrder())
        assertSame(near, scene.hitTest(forward())!!.panel)
    }

    @Test
    fun `with head following off the arrangement stays in the room`() {
        val scene = Scene().apply { followsHead = false }
        val left = scene.add(panel("left"))
        scene.add(panel("right"))
        scene.layOutCylindrical(spacingDegrees = 30f)

        repeat(60 * 30) { scene.advanceFollow(rad(60f), 1f / 60f) }

        assertEquals(-15f, deg(left.pose.yawRadians), 0.5f)
    }

    @Test
    fun `a wider margin lets the wearer look further past before it follows`() {
        val tight = Scene()
        val wide = Scene().apply { followMarginDegrees = 20f }
        val inTight = tight.add(panel("a"))
        val inWide = wide.add(panel("a"))

        // The panel's edge is at ~9.1 degrees; 25 is past 9.1 + 6 but not 9.1 + 20.
        repeat(60 * 30) {
            tight.advanceFollow(rad(25f), 1f / 60f)
            wide.advanceFollow(rad(25f), 1f / 60f)
        }

        assertEquals(25f, deg(inTight.pose.yawRadians), 2f)
        assertEquals(0f, deg(inWide.pose.yawRadians), 0.5f)
    }

    @Test
    fun `bringing a panel into view swaps it into the slot nearest the head`() {
        val scene = Scene()
        val a = scene.add(panel("a"))
        val b = scene.add(panel("b"))
        val c = scene.add(panel("c"))
        scene.layOutCylindrical(spacingDegrees = 30f)
        assertEquals(30f, deg(c.slotYawRadians), 1e-3f)

        assertTrue(scene.bringIntoView(c, rad(-30f)))

        assertEquals(-30f, deg(c.slotYawRadians), 1e-3f)
        assertEquals(30f, deg(a.slotYawRadians), 1e-3f)
        assertEquals(0f, deg(b.slotYawRadians), 1e-3f)
        repeat(60 * 10) { scene.advanceFollow(rad(-30f), 1f / 60f) }
        assertEquals(-30f, deg(c.pose.yawRadians), 1f)
    }

    @Test
    fun `a pinned panel is not brought into view`() {
        val scene = Scene()
        scene.add(panel("a"))
        val pinned = scene.add(panel("b", yawDegrees = 60f))
        scene.setFollow(pinned, SmoothFollow.PINNED)

        assertFalse(scene.bringIntoView(pinned, 0f))
        assertEquals(60f, deg(pinned.pose.yawRadians), 1e-3f)
    }

    /**
     * A wide panel earns a wide band on its own: the band comes from the panels'
     * angular extent, which a fixed number of degrees could never track.
     */
    @Test
    fun `a wider panel widens its own band`() {
        fun movesAt(headYawDegrees: Float, widthMetres: Float): Boolean {
            val scene = Scene()
            scene.add(panel("wide", widthMetres = widthMetres, heightMetres = 1f))
            scene.layOutCylindrical()
            var moved = false
            repeat(60) { if (scene.advanceFollow(rad(headYawDegrees), 1f / 60f)) moved = true }
            return moved
        }

        // At 4 m, 1.6 m subtends ~23 degrees and 5 m subtends ~64.
        assertTrue("a narrow panel should follow", movesAt(25f, widthMetres = 1.6f))
        assertFalse("a wide one should not, at the same angle", movesAt(25f, widthMetres = 5f))
    }

    /**
     * Bring back what the wearer was looking at, not the midpoint of the
     * arrangement — which with two panels is the gap between them.
     */
    @Test
    fun `the arrangement returns around the panel that was last focused`() {
        val scene = Scene()
        // Positive yaw is a left turn, so the first slot is the right-hand one.
        val right = scene.add(panel("right"))
        val left = scene.add(panel("left"))
        scene.layOutCylindrical(spacingDegrees = 30f)

        // Look at the left-hand panel, then look well away from everything.
        scene.hitTest(rayAtYaw(15f))
        assertSame(left, scene.focused)

        val headYaw = 90f
        repeat(60 * 30) { scene.advanceFollow(rad(headYaw), 1f / 60f) }

        assertEquals("the focused panel should end up ahead", headYaw, deg(left.pose.yawRadians), 2f)
        // The other keeps its offset from it, so the arrangement holds together.
        assertEquals(headYaw - 30f, deg(right.pose.yawRadians), 2f)
    }

    /**
     * Worn: "the panel starts following, but I look back to the right. The
     * panel doesn't move back, instead it goes all the way to the left, then
     * back to the right."
     */
    @Test
    fun `looking back before a return finishes sends the arrangement back`() {
        val scene = Scene()
        val panel = scene.add(panel("a"))
        scene.layOutCylindrical()

        // Past the ~17 degree band but not a panel's width past it, so this is
        // the slow drift pace, which is where the overshoot was long enough to see.
        repeat(30) { scene.advanceFollow(rad(30f), 1f / 60f) }
        val reached = deg(panel.pose.yawRadians)
        assertTrue("it should have started following, at %.1f°".format(reached), reached > 0.5f)

        var furthest = reached
        repeat(60 * 30) {
            scene.advanceFollow(0f, 1f / 60f)
            furthest = maxOf(furthest, deg(panel.pose.yawRadians))
        }

        assertEquals("never carried on past where it had got to", reached, furthest, 0.3f)
        assertEquals("back where it started", 0f, deg(panel.pose.yawRadians), 1f)
    }

    @Test
    fun `a panel easing in behind the anchor runs on when it turns round`() {
        val scene = Scene().apply { tightPanelTracking = false }
        val panel = scene.add(panel("a"))
        scene.layOutCylindrical()

        repeat(30) { scene.advanceFollow(rad(30f), 1f / 60f) }
        val reached = deg(panel.pose.yawRadians)

        var furthest = reached
        repeat(60 * 30) {
            scene.advanceFollow(0f, 1f / 60f)
            furthest = maxOf(furthest, deg(panel.pose.yawRadians))
        }

        assertTrue("the trailing lag is what tight tracking removes", furthest - reached > 0.3f)
        assertEquals(0f, deg(panel.pose.yawRadians), 1f)
    }

    @Test
    fun `looking back past the start sends it straight to the new side`() {
        val scene = Scene()
        val panel = scene.add(panel("a"))
        scene.layOutCylindrical()

        repeat(30) { scene.advanceFollow(rad(30f), 1f / 60f) }
        val reached = deg(panel.pose.yawRadians)

        var furthest = reached
        repeat(60 * 30) {
            scene.advanceFollow(rad(-30f), 1f / 60f)
            furthest = maxOf(furthest, deg(panel.pose.yawRadians))
        }

        assertEquals("never carried on to the left first", reached, furthest, 0.3f)
        assertEquals(-30f, deg(panel.pose.yawRadians), 1f)
    }

    /**
     * The pace used to be decided when the band was crossed, which is the one
     * moment the wearer is barely past it — so a turn that kept going was
     * answered at the drift pace. Worn: a second or two of nothing happening.
     */
    @Test
    fun `a turn that keeps going is caught up with, not drifted after`() {
        val scene = Scene()
        val panel = scene.add(panel("a", widthMetres = 1.61f, heightMetres = 1f))
        scene.layOutCylindrical()

        var frames = 0
        while (frames < 60 * 30) {
            val head = minOf(90f, frames * 90f / 60f)
            scene.advanceFollow(rad(head), 1f / 60f)
            frames++
            if (head == 90f && kotlin.math.abs(90f - deg(panel.pose.yawRadians)) < 2f) break
        }

        assertTrue("a 90° turn over a second took %.1fs to catch".format(frames / 60f), frames / 60f < 2f)
    }

    @Test
    fun `a panel sweeping under a still gaze does not take focus`() {
        val scene = Scene()
        val right = scene.add(panel("right"))
        val left = scene.add(panel("left"))
        scene.layOutCylindrical(spacingDegrees = 30f)

        scene.hitTest(rayAtYaw(-15f))
        assertSame(right, scene.focused)

        // A deliberate turn: the right-hand panel comes to 90, and the left-hand
        // one passes under the gaze on its way to 120.
        val focusSeen = mutableSetOf<String>()
        repeat(60 * 5) {
            scene.advanceFollow(rad(90f), 1f / 60f)
            scene.hitTest(rayAtYaw(90f))
            scene.focused?.let { focusSeen += it.id }
        }

        assertEquals("focus should never have left the panel being brought back", setOf("right"), focusSeen)
        assertEquals(90f, deg(right.pose.yawRadians), 2f)
        assertEquals(120f, deg(left.pose.yawRadians), 2f)
    }

    /**
     * Worn: "when the focused panel is the one in the direction I'm looking,
     * always, but looking past the opposite side focuses the other panel on
     * the way most of the time." The gaze crosses the neighbour on the way out,
     * and that used to count as having looked at it.
     */
    @Test
    fun `a panel the gaze only passes over on a turn is not the one brought back`() {
        val scene = Scene()
        val right = scene.add(panel("right"))
        val left = scene.add(panel("left"))
        scene.layOutCylindrical(spacingDegrees = 30f)

        repeat(30) {
            scene.advanceFollow(rad(-15f), 1f / 60f)
            scene.hitTest(rayAtYaw(-15f))
        }
        assertSame(right, scene.focused)

        // A deliberate turn to the left at 100°/s, straight across the other
        // panel, to 90 degrees, and hold.
        var head = -15f
        repeat(60 * 6) {
            head = minOf(90f, head + 100f / 60f)
            scene.advanceFollow(rad(head), 1f / 60f)
            scene.hitTest(rayAtYaw(head))
        }

        assertEquals("the panel looked at should be the one in front", 90f, deg(right.pose.yawRadians), 2f)
        assertSame(right, scene.focused)
    }

    /**
     * Worn: "looking past the second panel just a little triggers a slow
     * re-center, while the focus remains on the first panel ... it starts then
     * stops." Overshooting a glance is not asking for anything.
     */
    @Test
    fun `overshooting a glance at the next panel moves nothing`() {
        val scene = Scene()
        val right = scene.add(panel("right"))
        val left = scene.add(panel("left"))
        scene.layOutCylindrical(spacingDegrees = 30f)
        repeat(30) {
            scene.advanceFollow(rad(-15f), 1f / 60f)
            scene.hitTest(rayAtYaw(-15f))
        }

        // Across to the other panel at 100°/s, a few degrees past the ~32° edge
        // of the band, and back onto it, all within a quarter of a second.
        val path = (0 until 30).map { -15f + it * 50f / 30f } + (0 until 12).map { 35f - it * 20f / 12f }
        var furthest = 0f
        for (head in path + List(60 * 5) { 15f }) {
            scene.advanceFollow(rad(head), 1f / 60f)
            scene.hitTest(rayAtYaw(head))
            furthest = maxOf(furthest, kotlin.math.abs(deg(right.pose.yawRadians) + 15f))
        }

        assertEquals("nothing should have moved", 0f, furthest, 0.5f)
        assertSame(left, scene.focused)
    }

    private fun glanceLeftAndHold(scene: Scene, holdDegrees: Float) {
        repeat(30) {
            scene.advanceFollow(rad(-15f), 1f / 60f)
            scene.hitTest(rayAtYaw(-15f))
        }
        var head = -15f
        repeat(60 * 6) {
            head = minOf(holdDegrees, head + 100f / 60f)
            scene.advanceFollow(rad(head), 1f / 60f)
            scene.hitTest(rayAtYaw(head))
        }
    }

    @Test
    fun `holding a small overshoot past the next panel moves nothing`() {
        val scene = Scene()
        val right = scene.add(panel("right"))
        val left = scene.add(panel("left"))
        scene.layOutCylindrical(spacingDegrees = 30f)

        // The band's left edge is at ~32 degrees; this is a few past it.
        glanceLeftAndHold(scene, holdDegrees = 35f)

        assertEquals(-15f, deg(right.pose.yawRadians), 0.5f)
        assertEquals(15f, deg(left.pose.yawRadians), 0.5f)
    }

    @Test
    fun `a drift centres the panel the wearer is looking past, not the one they came from`() {
        val scene = Scene()
        val right = scene.add(panel("right"))
        val left = scene.add(panel("left"))
        scene.layOutCylindrical(spacingDegrees = 30f)

        // Well past the minimum, still short of a catch-up.
        glanceLeftAndHold(scene, holdDegrees = 46f)

        assertEquals("the nearer panel comes in front", 46f, deg(left.pose.yawRadians), 2f)
        assertEquals(16f, deg(right.pose.yawRadians), 2f)
    }

    @Test
    fun `stopping on a panel still counts as looking at it`() {
        val scene = Scene()
        scene.add(panel("right"))
        val left = scene.add(panel("left"))
        scene.layOutCylindrical(spacingDegrees = 30f)

        var head = -15f
        repeat(60) {
            head = minOf(15f, head + 100f / 60f)
            scene.advanceFollow(rad(head), 1f / 60f)
            scene.hitTest(rayAtYaw(head))
        }
        repeat(60 * 6) { scene.advanceFollow(rad(90f), 1f / 60f) }

        assertEquals(90f, deg(left.pose.yawRadians), 2f)
    }

    @Test
    fun `focus follows the gaze again once the arrangement has settled`() {
        val scene = Scene()
        val right = scene.add(panel("right"))
        val left = scene.add(panel("left"))
        scene.layOutCylindrical(spacingDegrees = 30f)
        scene.hitTest(rayAtYaw(-15f))

        repeat(60 * 5) { scene.advanceFollow(rad(90f), 1f / 60f) }
        scene.hitTest(rayAtYaw(120f))

        assertSame(left, scene.focused)
    }

    @Test
    fun `focus changing mid-return does not drag the destination`() {
        val scene = Scene()
        val right = scene.add(panel("right"))
        val left = scene.add(panel("left"))
        scene.layOutCylindrical(spacingDegrees = 30f)

        scene.hitTest(rayAtYaw(15f))
        assertSame(left, scene.focused)

        // Part way through the return, the panels sweep past the wearer's gaze
        // and focus lands on the other one.
        repeat(30) { scene.advanceFollow(rad(90f), 1f / 60f) }
        scene.hitTest(rayAtYaw(90f))
        repeat(60 * 30) { scene.advanceFollow(rad(90f), 1f / 60f) }

        assertEquals("still the originally focused panel", 90f, deg(left.pose.yawRadians), 2f)
        assertEquals(60f, deg(right.pose.yawRadians), 2f)
    }

    // --- Grab and move ---------------------------------------------------
    //
    // Placing a panel by hand is where the two frames the window manager lives
    // in meet: the wearer aims in world angles, a follower is stored as an
    // offset from their heading, and the translation happens exactly once, at
    // the moment the panel is let go of.

    /** Two panels are clear when their edges do not meet. */
    private fun assertClearOfEachOther(a: Panel, b: Panel) {
        val gap = kotlin.math.abs(deg(Panel.wrapPi(a.pose.yawRadians - b.pose.yawRadians)))
        val minimum = deg(a.angularHalfWidthRadians + b.angularHalfWidthRadians)
        assertTrue(
            "%.1f° apart, they overlap below %.1f°".format(gap, minimum),
            gap > minimum,
        )
    }

    /** A ray turned by [yawDegrees] and raised by [pitchDegrees]. */
    private fun rayAt(yawDegrees: Float, pitchDegrees: Float): Vec3 {
        val y = Math.toRadians(yawDegrees.toDouble())
        val p = Math.toRadians(pitchDegrees.toDouble())
        return Vec3(
            (-kotlin.math.sin(y) * kotlin.math.cos(p)).toFloat(),
            kotlin.math.sin(p).toFloat(),
            (-kotlin.math.cos(y) * kotlin.math.cos(p)).toFloat(),
        )
    }

    /**
     * A window is dragged by the point you took hold of it. Snapping the panel's
     * centre to the pointer would read as the panel having been thrown rather
     * than picked up, and it would move the panel before the wearer had asked
     * for it to move at all.
     */
    @Test
    fun `a grab holds the panel where it was taken hold of`() {
        val scene = Scene()
        val held = scene.add(panel("held"))

        scene.beginGrab(held, rayAtYaw(6f))
        scene.updateGrab(rayAtYaw(36f))

        assertEquals(30f, deg(held.pose.yawRadians), 1e-3f)
    }

    /**
     * The whole point of the feature: a follower's position is derived from its
     * slot every frame, so unless the placement is written down as a slot,
     * following drags the panel straight back out of the wearer's hands. The
     * slot is the panel measured against the *heading at release*, which is the
     * one moment the two frames can be reconciled.
     */
    @Test
    fun `dropping a follower records where it was put as a slot`() {
        val scene = Scene()
        val held = scene.add(panel("held"))

        scene.beginGrab(held, rayAtYaw(0f))
        scene.updateGrab(rayAtYaw(40f))
        scene.endGrab(rad(10f))

        // Measured against the arrangement's anchor — which is what makes the
        // placement exact. Measured against the wearer's heading instead, the
        // panel is recorded 10 degrees from where it was actually left, and
        // drifts by that much the moment following next runs.
        assertEquals(40f, deg(held.slotYawRadians), 1e-3f)
        assertTrue(held.slotPlacedByHand)
        // Checked with the wearer still looking at where they put it: a lone
        // panel left far off to one side is out of reach, and the band bringing
        // it along then is the band doing its job.
        repeat(60 * 5) { scene.advanceFollow(rad(40f), 1f / 60f) }
        assertEquals("letting go must not move it", 40f, deg(held.pose.yawRadians), 0.5f)
    }

    /** And having been put there, it stays there rather than drifting back. */
    @Test
    fun `a dropped follower stays where it was put`() {
        val scene = Scene()
        val held = scene.add(panel("held"))

        scene.beginGrab(held, rayAtYaw(0f))
        scene.updateGrab(rayAtYaw(25f))
        scene.endGrab(rad(0f))
        repeat(60 * 10) { scene.advanceFollow(rad(0f), 1f / 60f) }

        assertEquals(25f, deg(held.pose.yawRadians), 0.5f)
    }

    /** A pinned panel is already in world angles: there is nothing to record. */
    @Test
    fun `dropping a pinned panel leaves it in world angles`() {
        val scene = Scene()
        val held = scene.add(panel("held", follow = SmoothFollow.PINNED))

        scene.beginGrab(held, rayAtYaw(0f))
        scene.updateGrab(rayAtYaw(40f))
        scene.endGrab(rad(10f))

        assertEquals(40f, deg(held.pose.yawRadians), 1e-3f)
        assertEquals("a pinned panel has no slot to claim", 0f, deg(held.slotYawRadians), 1e-3f)
        assertFalse(held.slotPlacedByHand)
    }

    /** Two things writing one pose is one too many. */
    @Test
    fun `following does not move the panel being held`() {
        val scene = Scene()
        val held = scene.add(panel("held"))

        scene.beginGrab(held, rayAtYaw(0f))
        scene.updateGrab(rayAtYaw(20f))
        repeat(60 * 10) { scene.advanceFollow(rad(90f), 1f / 60f) }

        assertEquals(20f, deg(held.pose.yawRadians), 1e-3f)
    }

    /**
     * The arrangement is anchored on where its followers sit relative to their
     * slots — and a held panel is neither in its slot nor trying to be. Left in
     * that average, the wearer's own hand drags the anchor and the rest of the
     * arrangement sets off after the panel being carried.
     */
    @Test
    fun `carrying a panel does not send the others chasing it`() {
        val scene = Scene()
        val held = scene.add(panel("held"))
        val other = scene.add(panel("other"))
        scene.layOutCylindrical(spacingDegrees = 30f)
        val settled = other.pose.yawRadians

        // Carried well past the band, while the wearer's own heading stays
        // comfortably inside it.
        scene.beginGrab(held, rayAtYaw(deg(held.pose.yawRadians)))
        repeat(60 * 5) {
            scene.updateGrab(rayAtYaw(90f))
            scene.advanceFollow(rad(10f), 1f / 60f)
        }

        assertEquals(deg(settled), deg(other.pose.yawRadians), 1e-3f)
    }

    /**
     * A hand-placed slot is a follower's version of pinning: a claim on a place.
     * Opening or closing another panel re-lays out the arrangement, and the
     * pinned-panel precedent applies — a layout triggered by something unrelated
     * has no business overruling it.
     */
    @Test
    fun `a layout leaves a hand-placed panel where the wearer put it`() {
        val scene = Scene()
        val placed = scene.add(panel("placed"))
        val other = scene.add(panel("other"))
        scene.layOutCylindrical(spacingDegrees = 30f)

        scene.beginGrab(placed, rayAtYaw(deg(placed.pose.yawRadians)))
        scene.updateGrab(rayAtYaw(45f))
        scene.endGrab(rad(0f))
        scene.layOutCylindrical(spacingDegrees = 30f)

        assertEquals(45f, deg(placed.slotYawRadians), 1e-3f)
        assertEquals("the rest of the layout still centres on forward",
            0f, deg(other.slotYawRadians), 1e-3f)
    }

    /** Asking for a new spacing is asking about the whole arrangement. */
    @Test
    fun `re-spacing overrules a hand placement`() {
        val scene = Scene()
        val placed = scene.add(panel("placed"))
        scene.add(panel("other"))
        scene.layOutCylindrical(spacingDegrees = 30f)

        scene.beginGrab(placed, rayAtYaw(deg(placed.pose.yawRadians)))
        scene.updateGrab(rayAtYaw(45f))
        scene.endGrab(rad(0f))
        scene.layOutCylindrical(spacingDegrees = 40f, respaceHandPlaced = true)

        assertEquals(-20f, deg(placed.slotYawRadians), 1e-3f)
        assertFalse(placed.slotPlacedByHand)
    }

    /**
     * Worn, and the first thing a head found: a follower chased the wearer's
     * heading *and* re-parked clear of the panel being carried, at crossing
     * speed, so the panel the wearer was placing against moved in response to
     * where they were placing it. Reported as "I can't put it to the right of
     * the second panel — it jumps way ahead". Placement needs a stationary
     * background; the layout resolves when the grab ends.
     */
    @Test
    fun `the arrangement holds still while a panel is being placed`() {
        val scene = Scene()
        val other = scene.add(panel("other")).apply { moveToSlot(rad(-15f)) }
        val held = scene.add(panel("held")).apply { moveToSlot(rad(15f)) }
        val settled = other.pose.yawRadians

        scene.beginGrab(held, rayAtYaw(15f))
        repeat(60 * 20) {
            scene.updateGrab(rayAtYaw(-20f))
            assertFalse(
                "nothing else may move while a panel is held",
                scene.advanceFollow(rad(60f), 1f / 60f),
            )
        }

        assertEquals(deg(settled), deg(other.pose.yawRadians), 1e-4f)
    }

    /**
     * Worn, and the reason the release reference had to change: a panel put down
     * to the right of its neighbour came back on its left, overlapping it, as
     * soon as the pair followed. The wearer places against the panels they can
     * see, and during a grab those are exactly where the arrangement froze them
     * — not where the wearer's heading has since gone.
     */
    @Test
    fun `a placement keeps the order the wearer saw`() {
        val scene = Scene()
        val other = scene.add(panel("other")).apply { moveToSlot(rad(-15f)) }
        val held = scene.add(panel("held")).apply { moveToSlot(rad(15f)) }

        // Carried to the far side of its neighbour while the wearer turns with
        // it: the head ends up 41 degrees from where the arrangement is anchored.
        scene.beginGrab(held, rayAtYaw(15f))
        scene.updateGrab(rayAtYaw(-43f))
        scene.endGrab(rad(-41f))

        assertTrue(
            "placed at %.0f°, slot %.0f°: the slot must stay right of the neighbour's %.0f°"
                .format(deg(held.pose.yawRadians), deg(held.slotYawRadians), deg(other.slotYawRadians)),
            held.slotYawRadians < other.slotYawRadians,
        )

        // And the order survives the arrangement catching up.
        repeat(60 * 30) { scene.advanceFollow(rad(-41f), 1f / 60f) }
        assertTrue(held.pose.yawRadians < other.pose.yawRadians)
        assertClearOfEachOther(held, other)
    }

    /**
     * Worn: a panel parked 40 degrees to the right came back in front of the
     * wearer — with the whole arrangement dragged across after it, overlapping
     * its neighbour — because the return anchors on the panel last looked at.
     * That anchor is a repair for a layout the wearer did not choose; a
     * hand-placed slot *is* the choice, so it is left alone.
     */
    @Test
    fun `a return does not re-centre on a panel the wearer placed`() {
        val scene = Scene()
        val other = scene.add(panel("other"))
        val placed = scene.add(panel("placed"))
        scene.layOutCylindrical(spacingDegrees = 30f)

        scene.beginGrab(placed, rayAtYaw(deg(placed.pose.yawRadians)))
        scene.updateGrab(rayAtYaw(-40f))
        scene.endGrab(rad(0f))
        // Looked at where it was put, then turned well away and back.
        assertNotNull(scene.hitTest(rayAtYaw(-40f)))
        repeat(60 * 30) { scene.advanceFollow(rad(0f), 1f / 60f) }

        assertEquals("the placement is where the wearer put it, not re-centred",
            -40f, deg(placed.pose.yawRadians), 1f)
        assertEquals(-15f, deg(other.pose.yawRadians), 1f)
    }

    /**
     * And picks up again from wherever the wearer left everything — including
     * the case where the wearer put a panel down on top of a neighbour. The
     * placement is honoured and the *other* panel gives way, which is the right
     * way round: one of the two positions was chosen and the other was not.
     */
    @Test
    fun `the arrangement follows again once the panel is dropped`() {
        val scene = Scene()
        val other = scene.add(panel("other")).apply { moveToSlot(rad(-15f)) }
        val held = scene.add(panel("held")).apply { moveToSlot(rad(15f)) }

        scene.beginGrab(held, rayAtYaw(15f))
        scene.updateGrab(rayAtYaw(-20f))
        scene.endGrab(rad(60f))
        repeat(60 * 30) { scene.advanceFollow(rad(60f), 1f / 60f) }

        assertEquals("the placement is where the wearer left it",
            40f, deg(held.pose.yawRadians), 1f)
        assertClearOfEachOther(other, held)
    }

    /**
     * Elevation is the one axis a drag can leave the wearer unable to recover
     * from: there are 19 degrees of vertical field and a neck with far more
     * travel than that, so a panel dragged to where it was looked at can end up
     * where it can never be looked at again.
     */
    @Test
    fun `a drag cannot lift a panel out of the field of view`() {
        val scene = Scene()
        val held = scene.add(panel("held"))

        scene.beginGrab(held, rayAt(0f, 0f))
        scene.updateGrab(rayAt(0f, 70f))

        assertEquals(25f, deg(held.pose.pitchRadians), 1e-3f)
    }

    /** Elevation needs no slot: gravity is an absolute reference, unlike yaw. */
    @Test
    fun `a dropped panel keeps its elevation`() {
        val scene = Scene()
        val held = scene.add(panel("held"))

        scene.beginGrab(held, rayAt(0f, 0f))
        scene.updateGrab(rayAt(0f, 10f))
        scene.endGrab(rad(0f))
        repeat(60 * 10) { scene.advanceFollow(rad(0f), 1f / 60f) }

        assertEquals(10f, deg(held.pose.pitchRadians), 1e-3f)
    }

    /** Closing a panel while it is held must not leave the grab pointing at it. */
    @Test
    fun `removing the held panel ends the grab`() {
        val scene = Scene()
        val held = scene.add(panel("held"))

        scene.beginGrab(held, rayAtYaw(0f))
        scene.remove(held)

        assertNull(scene.grabbed)
        assertNull(scene.endGrab(rad(0f)))
    }

    // --- Elevation, follower clearance and layout speed -------------------

    /**
     * Worn: a panel parked 25 degrees up went on shoving its neighbours aside
     * from somewhere nobody was looking, and shoved them into each other. In
     * yaw it was in the way; in the field of view it was nowhere near.
     */
    @Test
    fun `a panel lifted clear of the others is not in their way`() {
        fun settle(pinnedPitch: Float): Panel {
            val scene = Scene()
            val follower = scene.add(panel("follower")).apply { moveToSlot(rad(-15f)) }
            scene.add(panel("pinned", pitchDegrees = pinnedPitch, follow = SmoothFollow.PINNED))
                .apply { moveToSlot(rad(15f)) }
            repeat(60 * 20) { scene.advanceFollow(rad(42f), 1f / 60f) }
            return follower
        }

        // Level with it, the follower is pushed off its slot; lifted clear, it
        // parks exactly where the layout wanted it.
        assertTrue(kotlin.math.abs(deg(settle(0f).pose.yawRadians) - 27f) > 3f)
        assertEquals(27f, deg(settle(25f).pose.yawRadians), 1f)
    }

    /**
     * Two followers whose slots are far enough apart never interact — the
     * clearance between them has to be narrower than the slot spacing, or a
     * correctly laid out pair counts as colliding forever and the avoidance
     * fights the layout that placed them.
     */
    @Test
    fun `a laid out pair is not treated as a collision`() {
        val scene = Scene()
        val left = scene.add(panel("left"))
        val right = scene.add(panel("right"))
        scene.layOutCylindrical(spacingDegrees = 30f)
        repeat(60 * 30) { scene.advanceFollow(rad(60f), 1f / 60f) }

        assertEquals(45f, deg(left.pose.yawRadians), 1f)
        assertEquals(75f, deg(right.pose.yawRadians), 1f)
    }

    /**
     * But two followers that *would* overlap do not. Worn, a follower pushed
     * clear of a pinned panel landed on another follower and the pair clipped
     * through each other until the wearer turned far enough to reshuffle them.
     */
    @Test
    fun `followers give way to each other rather than overlapping`() {
        val scene = Scene()
        val a = scene.add(panel("a")).apply { assignSlot(rad(0f)) }
        val b = scene.add(panel("b")).apply { assignSlot(rad(4f)) }

        repeat(60 * 20) { scene.advanceFollow(rad(0f), 1f / 60f) }

        assertClearOfEachOther(a, b)
    }

    /** And the one the wearer placed is not the one that gives way. */
    @Test
    fun `a hand-placed panel does not yield to an ordinary follower`() {
        val scene = Scene()
        val ordinary = scene.add(panel("ordinary")).apply { assignSlot(rad(0f)) }
        val placed = scene.add(panel("placed", yawDegrees = 4f))
            .apply { assignSlot(rad(4f), byHand = true) }

        repeat(60 * 20) { scene.advanceFollow(rad(0f), 1f / 60f) }

        assertEquals("the placement holds", 4f, deg(placed.pose.yawRadians), 1f)
        assertClearOfEachOther(ordinary, placed)
    }

    /**
     * A rearrangement is something the wearer asked for and is waiting on, so it
     * runs at its own speed. A return they did not ask for stays slow, because a
     * correction you can watch is a correction that should not be visible.
     */
    @Test
    fun `a rearrangement is brisk where a return is not`() {
        fun secondsToSettle(command: Boolean): Float {
            val scene = Scene()
            val a = scene.add(panel("a"))
            scene.add(panel("b"))
            scene.layOutCylindrical(spacingDegrees = 30f)
            if (command) {
                scene.layOutCylindrical(spacingDegrees = 60f, immediate = false)
            }
            val target = if (command) rad(-30f) else rad(45f)
            var frames = 0
            while (frames < 60 * 30) {
                scene.advanceFollow(if (command) rad(0f) else rad(60f), 1f / 60f)
                frames++
                if (kotlin.math.abs(deg(Panel.wrapPi(a.pose.yawRadians - target))) < 1f) break
            }
            return frames / 60f
        }

        val commanded = secondsToSettle(command = true)
        val returned = secondsToSettle(command = false)
        assertTrue("a rearrangement took %.1fs".format(commanded), commanded < 1.5f)
        assertTrue(
            "a return took %.1fs, a rearrangement %.1fs".format(returned, commanded),
            returned > commanded * 2f,
        )
    }

    /**
     * A new panel is the only one with nowhere to drift from, so it is the only
     * one placed outright — and at the arrangement's anchor, not at a world
     * angle, or it lands wherever the wearer happened to be facing when the
     * scene was created.
     */
    @Test
    fun `opening a panel places the newcomer and drifts the rest`() {
        val scene = Scene()
        val first = scene.add(panel("first"))
        scene.layOutCylindrical()
        // The wearer turns and the arrangement comes along, so the anchor moves.
        repeat(60 * 30) { scene.advanceFollow(rad(50f), 1f / 60f) }
        assertEquals(50f, deg(first.pose.yawRadians), 2f)

        val second = scene.open(panel("second"))

        assertTrue("the panel already in the scene must not jump",
            kotlin.math.abs(deg(first.pose.yawRadians) - 50f) < 3f)
        assertEquals("the newcomer takes its slot at the arrangement's anchor",
            65f, deg(second.pose.yawRadians), 3f)

        repeat(60 * 5) { scene.advanceFollow(rad(50f), 1f / 60f) }
        assertEquals(35f, deg(first.pose.yawRadians), 2f)
        assertEquals(65f, deg(second.pose.yawRadians), 2f)
    }

    /**
     * Worn, and the last thing a head found. With the wearer looking at a pinned
     * panel, a follower crept toward its neighbour, snapped back just before
     * they overlapped, and did it again for as long as the wearer kept looking
     * there.
     *
     * The cause is that each panel resolved against where its neighbours' slots
     * *wanted* them, never against where those neighbours had actually been
     * parked. A follower pushed clear of the pinned panel therefore aimed at a
     * place its neighbour had already been pushed into, closed on it, and was
     * shoved back out by follower avoidance — a constraint consulting a quantity
     * it changes, which is the same shape as every oscillation in this file.
     *
     * Reproduced from the scene the wearer had in front of them, sway included:
     * a still head settles even without the fix, so the head has to move.
     */
    @Test
    fun `a follower parked beside a pinned panel does not hunt its neighbour`() {
        val scene = Scene()
        val pinned = scene.add(
            panel("pinned", widthMetres = 1.61f, heightMetres = 1f, follow = SmoothFollow.PINNED)
        ).apply { moveToSlot(rad(-57f)); assignSlot(rad(-25f)) }
        val placed = scene.add(panel("placed", yawDegrees = -27f, widthMetres = 1.61f, heightMetres = 1f))
            .apply { assignSlot(rad(-45f), byHand = true) }
        val follower = scene.add(panel("follower", yawDegrees = -1f, widthMetres = 1.61f, heightMetres = 1f))
            .apply { assignSlot(rad(0f)) }

        fun sway(t: Float) = rad(-57f + 3f * kotlin.math.sin(2f * Math.PI.toFloat() * 0.3f * t))

        repeat(60 * 10) { scene.advanceFollow(sway(it / 60f), 1f / 60f) }
        val settled = follower.pose.yawRadians

        var worst = 0f
        repeat(60 * 30) {
            scene.advanceFollow(sway((600 + it) / 60f), 1f / 60f)
            val wander = kotlin.math.abs(deg(Panel.wrapPi(follower.pose.yawRadians - settled)))
            if (wander > worst) worst = wander
        }

        assertTrue("the follower wandered %.1f° after settling".format(worst), worst < 2f)
        assertClearOfEachOther(follower, placed)
        assertClearOfEachOther(follower, pinned)
    }

    /**
     * Worn: sweeping the gaze past a pinned panel and back left both followers
     * charging at it and snapping away, repeatedly. A sweep is what makes it
     * happen, because it leaves the panels parked well away from their slots —
     * which is exactly the state in which an anchor inferred from panel
     * positions disagrees with the one the slots are measured from.
     */
    @Test
    fun `a sweep past a pinned panel settles instead of hunting`() {
        fun settleAfterSweep(sweepTo: Float): List<Float> {
            val scene = Scene()
            scene.add(panel("pinned", widthMetres = 1.61f, heightMetres = 1f,
                follow = SmoothFollow.PINNED))
            val a = scene.add(panel("a", widthMetres = 1.61f, heightMetres = 1f))
            val b = scene.add(panel("b", widthMetres = 1.61f, heightMetres = 1f))
            scene.layOutCylindrical(spacingDegrees = 30f)

            // Look away, come back, and hold on the pinned panel.
            repeat(60 * 3) { scene.advanceFollow(rad(sweepTo), 1f / 60f) }
            repeat(60 * 3) { scene.advanceFollow(rad(-sweepTo), 1f / 60f) }
            repeat(60 * 10) { scene.advanceFollow(rad(0f), 1f / 60f) }

            val rest = listOf(a.pose.yawRadians, b.pose.yawRadians)
            var worst = 0f
            repeat(60 * 20) {
                scene.advanceFollow(rad(0f), 1f / 60f)
                listOf(a, b).forEachIndexed { i, panel ->
                    val wander =
                        kotlin.math.abs(deg(Panel.wrapPi(panel.pose.yawRadians - rest[i])))
                    if (wander > worst) worst = wander
                }
            }
            assertTrue("panels wandered %.1f° after settling".format(worst), worst < 2f)
            assertClearOfEachOther(a, b)
            return rest
        }

        settleAfterSweep(60f)
        settleAfterSweep(-60f)
    }

    /**
     * Recorded from the glasses: a follower pushed clear of a pinned panel came
     * to rest eight degrees inside its neighbour and stayed there. Panels were
     * resolved in slot order, so the first one asked where the second *wanted*
     * to be — its slot — while the second was about to be parked a long way from
     * it, and neither ever saw the collision.
     */
    @Test
    fun `a panel parked off a pinned obstacle is visible to its neighbours`() {
        val scene = Scene()
        val left = scene.add(panel("left", widthMetres = 1.61f, heightMetres = 1f))
        val pinned = scene.add(
            panel("pinned", widthMetres = 1.61f, heightMetres = 1f, follow = SmoothFollow.PINNED)
        )
        val right = scene.add(panel("right", widthMetres = 1.61f, heightMetres = 1f))
        scene.layOutCylindrical(spacingDegrees = 30f)
        scene.setFollow(pinned, SmoothFollow.PINNED)

        // The wearer turns away from the pinned panel and holds there, which is
        // what puts one follower's parking place next to the other's slot.
        repeat(60 * 40) { scene.advanceFollow(rad(-30f), 1f / 60f) }

        assertClearOfEachOther(left, right)
        assertClearOfEachOther(left, pinned)
        assertClearOfEachOther(right, pinned)
    }

    /**
     * Recorded from the glasses, and invisible from positions alone. A follower
     * that had committed to the far side of a pinned panel during one sweep was
     * still being ordered there afterwards, when its own slot had long since
     * carried it 65 degrees away on the other side — because a side is only
     * re-decided once the panel *arrives* on the side it claimed, and it never
     * could. Every frame it was told to cross the whole field of view, and it
     * shoved its neighbour aside on the way.
     */
    @Test
    fun `a side commitment is forgotten once the panel has no business there`() {
        val scene = Scene()
        val follower = scene.add(panel("follower", widthMetres = 1.61f, heightMetres = 1f))
        val pinned = scene.add(
            panel("pinned", widthMetres = 1.61f, heightMetres = 1f, follow = SmoothFollow.PINNED)
        )
        scene.layOutCylindrical(spacingDegrees = 30f)
        scene.setFollow(pinned, SmoothFollow.PINNED)

        // A *sweep*, not a pair of long looks: the follower has to still be
        // lagging on the wrong side of the pinned panel as the gaze crosses it,
        // or it arrives, re-decides, and there is nothing to get stuck on. This
        // is why the bug needed a head to find — a scripted turn that waits for
        // the panels to settle never reproduces it.
        repeat(60 * 2) { scene.advanceFollow(rad(70f), 1f / 60f) }
        repeat(60 * 20) { scene.advanceFollow(rad(-60f), 1f / 60f) }

        assertEquals("the follower belongs at its slot, not across the view",
            -60f, deg(follower.pose.yawRadians), 8f)

        // And it stays there rather than being ordered back across every frame.
        val rest = follower.pose.yawRadians
        repeat(60 * 10) { scene.advanceFollow(rad(-60f), 1f / 60f) }
        assertEquals(deg(rest), deg(follower.pose.yawRadians), 1f)
    }

    /**
     * Recorded from the glasses. Each obstacle had its say in turn and the last
     * one to speak won: the pinned panel pushed a follower to its right, the
     * follower beyond that pushed it back, and it came to rest exactly on the
     * pinned panel — because the side it had been sent to had no room for it at
     * all. Where a panel would *like* to be is a question for each obstacle in
     * turn; whether it can actually be there is a question about all of them at
     * once.
     */
    @Test
    fun `a panel with nowhere to go on one side goes to the other`() {
        val scene = Scene()
        val follower = scene.add(panel("follower", widthMetres = 1.61f, heightMetres = 1f))
        val pinned = scene.add(
            panel("pinned", widthMetres = 1.61f, heightMetres = 1f, follow = SmoothFollow.PINNED)
        )
        val other = scene.add(panel("other", widthMetres = 1.61f, heightMetres = 1f))
        scene.layOutCylindrical(spacingDegrees = 30f)
        scene.setFollow(pinned, SmoothFollow.PINNED)

        // Sweep well past the pinned panel, then come back and read it. The
        // sweep is what leaves the follower committed to a side of the pinned
        // panel that the other follower has already filled.
        repeat(60 * 2) { scene.advanceFollow(rad(70f), 1f / 60f) }
        repeat(60 * 10) { scene.advanceFollow(rad(0f), 1f / 60f) }

        assertClearOfEachOther(follower, pinned)
        assertClearOfEachOther(other, pinned)
        assertClearOfEachOther(follower, other)
    }

    /**
     * How far past the band the wearer looked says what kind of movement they
     * are asking for. Worn: a deliberate turn answered at the drift pace read as
     * the panels having a hard time keeping up, while the same pace applied to a
     * small excursion is exactly the correction they should never notice.
     */
    @Test
    fun `a deliberate turn is answered faster than drift is corrected`() {
        fun secondsToArrive(headDegrees: Float): Float {
            val scene = Scene()
            val panel = scene.add(panel("a", widthMetres = 1.61f, heightMetres = 1f))
            scene.layOutCylindrical()
            val target = rad(headDegrees)
            var frames = 0
            while (frames < 60 * 30) {
                scene.advanceFollow(target, 1f / 60f)
                frames++
                if (kotlin.math.abs(deg(Panel.wrapPi(panel.pose.yawRadians - target))) < 1f) break
            }
            return frames / 60f
        }

        // The band for one 22.7 degree panel reaches about 17 degrees, and a
        // drift needs 6 past it.
        val drift = secondsToArrive(26f)
        val deliberate = secondsToArrive(60f)

        assertTrue("the drift never arrived", drift < 29f)

        assertTrue(
            "a turn of 60° took %.1fs, a drift of 20° took %.1fs".format(deliberate, drift),
            deliberate < drift,
        )
        assertTrue("a deliberate turn took %.1fs".format(deliberate), deliberate < 1.5f)
    }

    /** A ray at a point in a panel's pixels, for a panel straight ahead at 4 m. */
    private fun rayAtPixel(panel: Panel, x: Float, y: Float) = Vec3(
        (x / panel.widthPx - 0.5f) * panel.pose.widthMetres,
        (0.5f - y / panel.heightPx) * panel.pose.heightMetres,
        -panel.pose.distanceMetres,
    )

    @Test
    fun `a hit says which part of the window it landed on`() {
        val scene = Scene()
        val window = scene.open(panel("a"))
        val pin = TitleBar.pin(window.widthPx, window.heightPx)
        val close = TitleBar.close(window.widthPx, window.heightPx)
        val bar = TitleBar.bar(window.widthPx, window.heightPx)

        assertEquals(HitPart.CONTENT, scene.hitTest(rayAtPixel(window, 640f, 400f))?.part)
        assertEquals(HitPart.TITLE_BAR, scene.hitTest(rayAtPixel(window, bar.left + 30f, bar.top + 28f))?.part)
        assertEquals(HitPart.PIN, scene.hitTest(rayAtPixel(window, pin.left + 20f, pin.top + 20f))?.part)
        assertEquals(HitPart.CLOSE, scene.hitTest(rayAtPixel(window, close.left + 20f, close.top + 20f))?.part)
        assertNull("between the panel and its bar", scene.hitTest(rayAtPixel(window, 640f, 800f + TitleBar.GAP_PX / 2f)))
        assertNull("beside the bar", scene.hitTest(rayAtPixel(window, bar.left - 10f, bar.top + 28f)))
    }

    @Test
    fun `a Back button is only there while the window asks for one`() {
        val scene = Scene()
        val window = scene.open(panel("a"))
        val back = TitleBar.back(window.widthPx, window.heightPx)
        val ray = rayAtPixel(window, back.left + 20f, back.top + 20f)

        assertEquals(HitPart.TITLE_BAR, scene.hitTest(ray)?.part)
        window.backButton = true
        assertEquals(HitPart.BACK, scene.hitTest(ray)?.part)
    }

    @Test
    fun `a cancelled grab leaves the window where it was and unplaced`() {
        val scene = Scene()
        val window = scene.open(panel("a"))
        scene.advanceFollow(0f, 0.016f)
        val yaw = window.pose.yawRadians
        scene.beginGrab(window, forward())
        scene.updateGrab(rayAtYaw(20f))
        scene.cancelGrab()
        assertNull(scene.grabbed)
        assertEquals(yaw, window.pose.yawRadians, 1e-6f)
        assertFalse(window.slotPlacedByHand)
    }

    private fun withOverlays(scene: Scene = Scene()): Scene = scene.apply {
        setOverlays(
            panel("dock", widthMetres = 0.88f, heightMetres = 0.104f, engine = FakePanelEngine(880, 104)),
            panel("launcher", engine = FakePanelEngine(1280, 800)),
        )
    }

    private fun Scene.run(headYaw: Float = 0f, seconds: Float = 2f) {
        repeat((seconds * 60).toInt()) { advanceFollow(headYaw, 1f / 60) }
    }

    private fun pitchDegrees(panel: Panel) = Math.toDegrees(panel.pose.pitchRadians.toDouble()).toFloat()

    @Test
    fun `with no window open the launcher is centred on the horizon, the dock below it`() {
        val scene = withOverlays()
        scene.run()
        assertTrue(scene.launcherShown)
        assertTrue(scene.dockShown)
        assertEquals(0f, pitchDegrees(scene.launcher!!), 0.01f)
        assertTrue(pitchDegrees(scene.dock!!) < -7f)
    }

    @Test
    fun `the dock sits under the lowest title bar and turns with the head`() {
        val scene = withOverlays()
        scene.open(panel("a"))
        scene.run(headYaw = rayYaw(20f))

        val barBottom = scene.cursorReach().first().bottom
        val dock = scene.dock!!
        assertTrue(dock.pose.pitchRadians + dock.angularHalfHeightRadians < barBottom)
        assertEquals(20f, Math.toDegrees(dock.pose.yawRadians.toDouble()).toFloat(), 0.1f)
        assertFalse("a window open puts the launcher away", scene.launcherShown)
    }

    @Test
    fun `an auto-hidden dock comes up below the windows, and the cursor can reach it`() {
        val scene = withOverlays()
        scene.dockVisibility = DockVisibility.AUTO_HIDE
        scene.open(panel("a"))
        scene.hitTest(forward())
        scene.run()
        assertFalse(scene.dockShown)
        assertTrue("its place stays reachable", scene.cursorReach().any { it.yaw == scene.dock!!.pose.yawRadians })

        val dock = scene.dock!!
        val atDock = Vec3(0f, kotlin.math.tan(dock.pose.pitchRadians), -1f)
        scene.hitTest(atDock)
        scene.run(seconds = 0.5f)
        assertTrue(scene.dockShown)
        assertEquals(1f, scene.dockOpacity, 1e-6f)

        scene.hitTest(forward())
        scene.run(seconds = 0.5f)
        assertFalse(scene.dockShown)
    }

    @Test
    fun `auto-hidden windows go once the pointer has been off them a while, and come back when it is on one`() {
        val scene = withOverlays()
        scene.windowsAutoHide = true
        scene.windowsHideDelaySeconds = 1f
        scene.open(panel("a"))
        val away = Vec3(-1f, 0f, 0f)

        scene.hitTest(away)
        scene.run(seconds = 0.5f)
        assertTrue("not before the delay", scene.windowsShown)
        scene.run(seconds = 1f)
        assertFalse(scene.windowsShown)
        assertEquals(0f, scene.windowsOpacity, 1e-6f)

        scene.hitTest(forward())
        scene.run(seconds = 0.05f)
        assertTrue("pointing at one brings them back at once", scene.windowsShown)
        scene.run(seconds = 0.5f)
        assertEquals(1f, scene.windowsOpacity, 1e-6f)
    }

    @Test
    fun `windows stay shown while one is held or the launcher is up, and always when auto-hide is off`() {
        val scene = withOverlays()
        scene.open(panel("a"))
        scene.hitTest(Vec3(-1f, 0f, 0f))
        scene.run(seconds = 3f)
        assertTrue(scene.windowsShown)

        scene.windowsAutoHide = true
        scene.launcherRequested = true
        scene.run(seconds = 3f)
        assertTrue(scene.windowsShown)
        scene.launcherRequested = false
        scene.run(seconds = 3f)
        assertFalse(scene.windowsShown)
    }

    @Test
    fun `overlays are hit first and never take focus`() {
        val scene = withOverlays()
        val window = scene.open(panel("a"))
        scene.setOverlays(scene.dock, scene.launcher)
        scene.launcherRequested = true
        scene.run()

        val hit = scene.hitTest(Vec3(0f, kotlin.math.tan(scene.launcher!!.pose.pitchRadians), -1f))
        assertSame(scene.launcher, hit?.panel)
        assertSame(window, scene.focused)
    }

    private fun rayYaw(degrees: Float) = Math.toRadians(degrees.toDouble()).toFloat()

    @Test
    fun `centring on a window kept aside by a hand-placed neighbour still brings it in front`() {
        val scene = Scene()
        scene.collisionPolicy = CollisionPolicy.AVOID_ALL
        val a = scene.open(panel("a"))
        val b = scene.open(panel("b"))
        scene.run(seconds = 1f)
        scene.beginGrab(a, rayAtYaw(0f))
        scene.updateGrab(rayAtYaw(Math.toDegrees(b.pose.yawRadians.toDouble()).toFloat() + 5f))
        scene.endGrab(0f)
        scene.run(seconds = 3f)
        val head = rayYaw(70f)

        scene.centreOn(b, head)
        scene.run(headYaw = head, seconds = 6f)

        assertEquals(head, b.pose.yawRadians, rayYaw(1.2f))
    }

    @Test
    fun `centring on a window parked beside a pinned one brings it in front`() {
        val scene = Scene()
        scene.collisionPolicy = CollisionPolicy.AVOID_PINNED
        val pinned = scene.open(panel("pinned"))
        val b = scene.open(panel("b"))
        scene.run(seconds = 1f)
        scene.setFollow(pinned, SmoothFollow.PINNED)
        scene.run(seconds = 3f)
        val head = rayYaw(80f)

        scene.centreOn(b, head)
        scene.run(headYaw = head, seconds = 6f)

        assertEquals(head, b.pose.yawRadians, rayYaw(1.2f))
    }

    @Test
    fun `gaze below the windows brings up an auto-hidden dock, whatever the pointer does`() {
        val scene = withOverlays()
        scene.dockVisibility = DockVisibility.AUTO_HIDE
        scene.open(panel("a"))
        scene.hitTest(forward())
        scene.gazePitchRadians = 0f
        scene.run(seconds = 0.5f)
        assertFalse(scene.dockShown)

        scene.gazePitchRadians = rayYaw(-12f)
        scene.run(seconds = 0.5f)
        assertTrue(scene.dockShown)

        scene.gazePitchRadians = 0f
        scene.run(seconds = 0.5f)
        assertFalse(scene.dockShown)
    }

    @Test
    fun `typing goes to the launcher while it is up, otherwise to the focused window`() {
        val scene = withOverlays()
        assertSame("nothing open: the launcher", scene.launcher, scene.keyTarget)

        val window = scene.open(panel("a"))
        assertSame(window, scene.keyTarget)

        scene.launcherRequested = true
        assertSame(scene.launcher, scene.keyTarget)
    }

    private class SizedEngine(var width: Int = 1280, var height: Int = 800) : PanelEngine by FakePanelEngine() {
        override val widthPx: Int get() = width
        override val heightPx: Int get() = height
        override fun resize(widthPx: Int, heightPx: Int) {
            width = widthPx
            height = heightPx
        }
    }

    @Test
    fun `just outside a corner is its resize handle, just inside is still the page`() {
        val scene = Scene()
        val window = scene.open(panel("a"))
        assertEquals(HitPart.RESIZE_TOP_LEFT, scene.hitTest(rayAtPixel(window, -20f, -20f))?.part)
        assertEquals(HitPart.RESIZE_BOTTOM_RIGHT, scene.hitTest(rayAtPixel(window, 1300f, 820f))?.part)
        assertEquals(HitPart.RESIZE_TOP_RIGHT, scene.hitTest(rayAtPixel(window, 1260f, -30f))?.part)
        assertEquals(HitPart.CONTENT, scene.hitTest(rayAtPixel(window, 15f, 15f))?.part)
        assertNull("far from any corner", scene.hitTest(rayAtPixel(window, 640f, -30f)))
    }

    @Test
    fun `a resize drag asks for the size the pointer gives, within the window's limits`() {
        val scene = Scene()
        val window = scene.open(panel("a", engine = SizedEngine()))
        window.sizeLimits = SizeLimits(600, 400, 1600, 1000)
        scene.beginResize(window, Corner.BOTTOM_RIGHT)

        scene.updateResize(rayAtPixel(window, 1480f, 900f))
        assertEquals(1480, scene.resizing!!.widthPx)
        assertEquals(900, scene.resizing!!.heightPx)

        scene.updateResize(rayAtPixel(window, 3000f, 100f))
        assertEquals(1600, scene.resizing!!.widthPx)
        assertEquals(400, scene.resizing!!.heightPx)
    }

    private fun leftEdge(window: Panel) = window.pose.yawRadians + window.angularHalfWidthRadians
    private fun topEdge(window: Panel) = window.pose.pitchRadians + window.angularHalfHeightRadians

    @Test
    fun `a resized window keeps the corner opposite the one dragged, and eases there`() {
        val scene = Scene()
        val engine = SizedEngine()
        val window = scene.open(panel("a", engine = engine))
        scene.setFollow(window, SmoothFollow.PINNED)
        scene.run(seconds = 0.5f)
        val left = leftEdge(window)
        val top = topEdge(window)

        engine.resize(1600, 1000)
        scene.resized(window, 1280, 800, Corner.TOP_LEFT)
        assertTrue(scene.isAnimatingResize(window))
        scene.run(seconds = 0.5f)

        assertFalse(scene.isAnimatingResize(window))
        assertEquals(1.6f, window.pose.widthMetres, 1e-4f)
        assertEquals(left, leftEdge(window), rayYaw(0.05f))
        assertEquals(top, topEdge(window), rayYaw(0.05f))
    }

    @Test
    fun `a resized follower keeps its corner through its slot`() {
        val scene = Scene()
        val engine = SizedEngine()
        val window = scene.open(panel("a", engine = engine))
        scene.run(seconds = 1f)
        val left = leftEdge(window)

        engine.resize(1800, 800)
        scene.resized(window, 1280, 800, Corner.TOP_LEFT)
        scene.run(seconds = 2f)

        assertEquals(left, leftEdge(window), rayYaw(0.6f))
        assertTrue(window.slotYawRadians < 0f)
    }

    private fun withPopup(scene: Scene = Scene()): Scene = scene.apply {
        setPopupPanel(panel("menu", widthMetres = 0.3f, heightMetres = 0.2f, engine = FakePanelEngine(300, 200)))
    }

    @Test
    fun `a popup opens with its corner at the point and is hit before the window under it`() {
        val scene = withPopup()
        val window = scene.add(panel("a"))
        scene.showPopup(window, 640f, 400f)

        val corner = scene.hitTest(rayAtPixel(window, 645f, 405f))
        assertSame(scene.popup, corner!!.panel)
        assertEquals(5f, corner.hit.x, 1f)
        assertEquals(5f, corner.hit.y, 1f)
        assertSame(window, scene.hitTest(rayAtPixel(window, 635f, 405f))!!.panel)
    }

    @Test
    fun `a popup keeps the window's pixel size`() {
        val scene = withPopup()
        val window = scene.add(panel("a"))
        scene.showPopup(window, 100f, 100f)

        val popup = scene.popup!!
        assertEquals(window.pose.widthMetres / window.widthPx, popup.pose.widthMetres / popup.widthPx, 1e-6f)
    }

    @Test
    fun `a popup near the window's corner opens to the left and up`() {
        val scene = withPopup()
        val window = scene.add(panel("a"))
        scene.showPopup(window, 1200f, 760f)

        val hit = scene.hitTest(rayAtPixel(window, 1195f, 755f))
        assertSame(scene.popup, hit!!.panel)
        // Each panel faces the wearer, so away from the window's centre the two planes part slightly.
        assertEquals(295f, hit.hit.x, 4f)
        assertEquals(195f, hit.hit.y, 4f)
    }

    @Test
    fun `a popup follows its window and goes when the window does`() {
        val scene = withPopup()
        val window = scene.open(panel("a"))
        scene.setFollow(window, SmoothFollow.PINNED)
        scene.showPopup(window, 640f, 400f)
        window.pose = window.pose.copy(yawRadians = rayYaw(20f))
        scene.advanceFollow(0f, 1f / 60)
        assertTrue(scene.popup!!.pose.yawRadians > rayYaw(15f))

        scene.remove(window)
        scene.advanceFollow(0f, 1f / 60)
        assertFalse(scene.popupShown)
    }

    @Test
    fun `a hidden popup is not hit`() {
        val scene = withPopup()
        val window = scene.add(panel("a"))
        scene.showPopup(window, 640f, 400f)
        scene.hidePopup()

        assertSame(window, scene.hitTest(rayAtPixel(window, 645f, 405f))!!.panel)
    }
}
