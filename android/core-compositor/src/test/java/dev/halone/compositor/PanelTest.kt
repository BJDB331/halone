package dev.halone.compositor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelTest {

    private fun rad(degrees: Double) = Math.toRadians(degrees).toFloat()
    private fun deg(radians: Float) = Math.toDegrees(radians.toDouble()).toFloat()

    /**
     * Inside the deadzone the panel must be *perfectly* world-locked. Any
     * movement at all here is what makes a scene feel attached to the head
     * instead of to the world.
     */
    @Test
    fun `a panel does not move while the head stays inside the deadzone`() {
        val p = panel("a", follow = SmoothFollow(deadzoneDegrees = 12f))

        repeat(120) { assertFalse(p.advanceFollow(rad(11.0), dt = 1f / 60f)) }

        assertEquals(0f, deg(p.pose.yawRadians), 1e-4f)
    }

    /**
     * The deadzone is a gate, not a stopping point: once pulled outside it, the
     * panel returns the whole way to its slot. Stopping at the edge made the
     * resting place depend on the direction the wearer had turned.
     */
    @Test
    fun `once pulled outside the deadzone a panel returns all the way to its slot`() {
        val p = panel("a", follow = SmoothFollow(deadzoneDegrees = 12f, timeConstantSeconds = 2.5f))

        repeat(60 * 30) { p.advanceFollow(rad(90.0), dt = 1f / 60f) }

        assertEquals(90f, deg(p.pose.yawRadians), 1f)
    }

    /**
     * Worn, this was the bug: turning one way left the panel nearly centred and
     * turning the other left it 27 degrees out, on the edge of the field of view,
     * appearing never to arrive.
     */
    @Test
    fun `the resting place does not depend on the direction of approach`() {
        fun restingYaw(headYawDegrees: Double, startYawDegrees: Double): Float {
            val p = panel("a").apply {
                moveToSlot(rad(-15.0))
                pose = pose.copy(yawRadians = rad(startYawDegrees))
            }
            repeat(60 * 60) { p.advanceFollow(rad(headYawDegrees), dt = 1f / 60f) }
            return deg(p.pose.yawRadians)
        }

        // Same head yaw, approached from either side.
        val fromTheLeft = restingYaw(headYawDegrees = 0.0, startYawDegrees = 60.0)
        val fromTheRight = restingYaw(headYawDegrees = 0.0, startYawDegrees = -60.0)

        assertEquals("slot is -15, so both must rest there", -15f, fromTheLeft, 1f)
        assertEquals(-15f, fromTheRight, 1f)
    }

    /** A correction the wearer can see is mistuned, however correct it is. */
    @Test
    fun `following is slow enough to read as drift rather than a snap`() {
        val p = panel("a", follow = SmoothFollow(deadzoneDegrees = 12f, timeConstantSeconds = 2.5f))

        p.advanceFollow(rad(90.0), dt = 1f / 60f)

        // One frame of a 90 degree error must move the panel well under a degree.
        assertTrue(deg(p.pose.yawRadians) < 1f)
    }

    @Test
    fun `a pinned panel ignores the head entirely`() {
        val p = panel("a", yawDegrees = 20f, follow = SmoothFollow.PINNED)

        repeat(600) { assertFalse(p.advanceFollow(rad(90.0), dt = 1f / 60f)) }

        assertEquals(20f, deg(p.pose.yawRadians), 1e-4f)
    }

    /**
     * A stall is not motion. Following across a long gap would dump the whole
     * accumulated error into one frame, which reads as the panel jumping.
     */
    @Test
    fun `a stalled frame does not move the panel`() {
        val p = panel("a")

        assertFalse(p.advanceFollow(rad(90.0), dt = 2f))

        assertEquals(0f, deg(p.pose.yawRadians), 1e-4f)
    }

    @Test
    fun `following takes the short way round behind the wearer`() {
        // Straight ahead of the wearer, but drifted to the far side of +-180.
        val p = panel("a", yawDegrees = -170f).apply { slotYawRadians = 0f }

        repeat(60 * 30) { p.advanceFollow(rad(170.0), dt = 1f / 60f) }

        // -170 to 170 is 20 degrees the short way round, not 340 the long way,
        // and the panel returns the whole way to its slot at 170.
        assertEquals(170f, kotlin.math.abs(deg(p.pose.yawRadians)), 1f)
    }

    /**
     * The bug this fixes was found by wearing it: two followers converged onto
     * one heading and, being at the same distance, z-fought.
     *
     * A follower chases its own slot relative to the wearer, so a group of them
     * keeps its arrangement no matter how far the head turns.
     */
    @Test
    fun `followers keep their spacing instead of collapsing onto each other`() {
        val scene = Scene()
        val left = scene.add(panel("left"))
        val right = scene.add(panel("right"))
        scene.layOutCylindrical(spacingDegrees = 30f)

        for (headYaw in listOf(0.0, 20.0, 90.0, -140.0, 175.0)) {
            repeat(60 * 20) { scene.advanceFollow(rad(headYaw), dt = 1f / 60f) }

            val separation = kotlin.math.abs(
                deg(Panel.wrapPi(right.pose.yawRadians - left.pose.yawRadians))
            )
            assertEquals("head at $headYaw", 30f, separation, 1f)
        }
    }

    /** An off-centre follower settles at its slot, not in front of the wearer. */
    @Test
    fun `a follower settles at its slot ahead of the wearer`() {
        val p = panel("a").apply { moveToSlot(rad(30.0)) }

        repeat(60 * 30) { p.advanceFollow(rad(60.0), dt = 1f / 60f) }

        // Its slot is 30 degrees off the wearer's heading of 60.
        assertEquals(90f, deg(p.pose.yawRadians), 1f)
    }

    /**
     * Angular size is the unit that matters: what decides whether text is
     * readable is the angle a panel subtends, not its size in metres.
     */
    @Test
    fun `fitting to the field of view keeps apparent size across distances`() {
        val near = panel("near", distanceMetres = 2f)
        val far = panel("far", distanceMetres = 8f)

        near.fitToFov(verticalFovDegrees = 19f, verticalFill = 0.75f)
        far.fitToFov(verticalFovDegrees = 19f, verticalFill = 0.75f)

        val nearAngle = 2 * kotlin.math.atan(near.pose.heightMetres / 2 / near.pose.distanceMetres)
        val farAngle = 2 * kotlin.math.atan(far.pose.heightMetres / 2 / far.pose.distanceMetres)
        assertEquals(nearAngle, farAngle, 1e-4f)
        assertEquals(19f * 0.75f, deg(nearAngle), 0.2f)
        // Aspect comes from the engine's pixels, so panel pixels stay square.
        assertEquals(1280f / 800f, near.pose.widthMetres / near.pose.heightMetres, 1e-4f)
    }

    @Test
    fun `a narrower field of view shrinks a panel that was authored angularly`() {
        val spec = panel("a")
        val measured = panel("b")

        spec.fitToFov(verticalFovDegrees = 23.5f, verticalFill = 0.75f)
        measured.fitToFov(verticalFovDegrees = 19f, verticalFill = 0.75f)

        assertTrue(measured.pose.heightMetres < spec.pose.heightMetres)
    }
}
