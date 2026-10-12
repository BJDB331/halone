package dev.halone.app.pointer

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dev.halone.glasses.math.Quaternion
import dev.halone.glasses.math.Vec3
import java.io.Closeable
import kotlin.math.sqrt

/**
 * The phone as a gyro pointer — the mode the original Nebula app had, where you
 * aim the handset like a laser pointer.
 *
 * Uses the phone's own rotation vector, not the glasses': this is a second,
 * independent pose. The awkward part would normally be reconciling Android's
 * sensor world frame (Z up, arbitrary heading) with our canonical device frame,
 * with a permanent risk of a mirrored axis.
 *
 * Calibration removes that problem entirely rather than solving it. Taking the
 * delta from a reference orientation, `qRef⁻¹ ⊗ qNow`, expresses the rotation in
 * the *reference phone frame*; applying it to that frame's forward axis (−Z)
 * yields a direction whose axes are, by construction, whatever the user's
 * "pointing at the middle of the panel" pose declared them to be. Hold the phone
 * naturally — screen toward you, back toward the panel — and −Z is forward, +X
 * right and +Y up with no frame conversion at all.
 */
class PhonePointer(
    context: Context,
    private val log: (String) -> Unit,
) : SensorEventListener, Closeable {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    /** Game rotation vector: gyro + accel, no magnetometer, so no compass wobble. */
    private val sensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    @Volatile
    private var current: Quaternion = Quaternion.IDENTITY

    @Volatile
    private var reference: Quaternion = Quaternion.IDENTITY

    /** Which phone axis is treated as "pointing", decided at calibration. */
    @Volatile
    private var pointingAxis: Vec3 = BACK_OF_PHONE

    /** Maps the reference phone frame onto the canonical frame. */
    @Volatile
    private var basis: Quaternion = Quaternion.IDENTITY

    /** How far the world frame has turned under the pointer since calibration. */
    @Volatile
    private var worldTurn: Quaternion = Quaternion.IDENTITY

    @Volatile
    var grip: String = "uncalibrated"
        private set

    @Volatile
    var samples = 0L
        private set

    val available: Boolean get() = sensor != null

    fun start(): Boolean {
        val target = sensor ?: run {
            log("  pointer: no rotation vector sensor on this device")
            return false
        }
        sensorManager.registerListener(this, target, SensorManager.SENSOR_DELAY_GAME)
        log("  pointer: listening to ${target.name}")
        return true
    }

    /**
     * Declares the current pose to be "pointing at the middle of the panel", and
     * works out which way the phone is being held.
     *
     * Which axis counts as "pointing" depends entirely on the grip, and guessing
     * wrong does not fail obviously — it produces a pointer that tracks, just
     * with the wrong rotation driving the wrong screen axis. Held flat like a
     * remote, the pointing axis is the top edge; held upright with the screen
     * toward your face, it is the back of the phone. Gravity distinguishes the
     * two: it tells us where the screen is facing, which is exactly the
     * difference between the grips.
     */
    fun calibrate() {
        reference = current
        worldTurn = Quaternion.IDENTITY

        // World up, expressed in the phone's own frame.
        val upInPhone = rotate(current.conjugate(), WORLD_UP)

        if (kotlin.math.abs(upInPhone.z) > FLAT_THRESHOLD) {
            // Screen facing up or down: the phone is a remote and the top edge
            // does the pointing.
            pointingAxis = TOP_EDGE
            basis = TOP_EDGE_TO_FORWARD
            grip = "flat (remote-style), pointing with the top edge"
        } else {
            // Screen roughly vertical: the back of the phone does the pointing.
            pointingAxis = BACK_OF_PHONE
            basis = Quaternion.IDENTITY
            grip = "upright (screen toward you), pointing with the back"
        }

        log("  pointer: calibrated — grip detected as $grip")
        log("  pointer: up-in-phone = [$upInPhone]")
    }

    /**
     * Keeps the pointer on the same physical direction when the world frame
     * turns under it, as a head recentre does: the phone has not moved, so
     * neither should what it points at.
     */
    fun turnWorld(by: Quaternion) {
        worldTurn = (by * worldTurn).normalized()
    }

    /** Pointing direction in the canonical frame, unit length. */
    fun direction(): Vec3 {
        val delta = (reference.conjugate() * current).normalized()
        return rotate(worldTurn, rotate(basis * delta, pointingAxis))
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        // The rotation vector is (x, y, z[, w]); w must be reconstructed when the
        // platform omits it.
        val x = v[0]
        val y = v[1]
        val z = v[2]
        val w = if (v.size >= 4) {
            v[3]
        } else {
            val t = 1f - (x * x + y * y + z * z)
            if (t > 0f) sqrt(t) else 0f
        }
        current = Quaternion(w, x, y, z).normalized()
        samples++
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun close() {
        sensorManager.unregisterListener(this)
    }

    companion object {
        /** Out of the back of the phone: forward when held screen-toward-you. */
        private val BACK_OF_PHONE = Vec3(0f, 0f, -1f)

        /** Out of the top edge: forward when the phone is held flat, like a remote. */
        private val TOP_EDGE = Vec3(0f, 1f, 0f)

        private val WORLD_UP = Vec3(0f, 0f, 1f)

        /** Above this much of gravity on the phone's Z, the screen faces up or down. */
        private const val FLAT_THRESHOLD = 0.7f

        /**
         * Rotation about X by -90 degrees, taking the phone's +Y to canonical
         * forward (-Z). It also sends the phone's +X to canonical right and its
         * +Z to canonical up, so the whole basis lands the right way round rather
         * than only the pointing axis.
         */
        private val TOP_EDGE_TO_FORWARD = Quaternion(0.70710678f, -0.70710678f, 0f, 0f)

        /** Rotates a vector by a quaternion: q ⊗ v ⊗ q⁻¹. */
        fun rotate(q: Quaternion, v: Vec3): Vec3 {
            val tx = 2f * (q.y * v.z - q.z * v.y)
            val ty = 2f * (q.z * v.x - q.x * v.z)
            val tz = 2f * (q.x * v.y - q.y * v.x)
            return Vec3(
                v.x + q.w * tx + (q.y * tz - q.z * ty),
                v.y + q.w * ty + (q.z * tx - q.x * tz),
                v.z + q.w * tz + (q.x * ty - q.y * tx),
            )
        }
    }
}
