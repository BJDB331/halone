package dev.halone.glasses.api

data class DeviceInfo(
    val vendor: String,
    val model: String,
    /** Stable per-driver key, e.g. `xreal:air2pro`. Used in logs and settings. */
    val driverId: String,
    val serial: String? = null,
    val firmware: String? = null,
) {
    val displayName: String get() = "$vendor $model"
}

/**
 * Physical characteristics the compositor needs in order to render for this
 * specific hardware. Values are per device family, not per unit.
 */
data class Optics(
    /**
     * Diagonal field of view for a **symmetric** projection, in degrees.
     *
     * Superseded whenever the glasses hand over their own calibration, which
     * carries a per-eye asymmetric frustum this cannot express — see
     * [Frustum]. What remains here is the fallback for hardware that has no
     * calibration to read, and it is deliberately *not* the calibrated figure:
     * a worn A/B found 22.6° rendered symmetrically to be worse than either the
     * calibrated asymmetric projection or this narrower one. If you have to be
     * symmetric, be narrow.
     *
     * Not necessarily the number on the box.
     *
     * This must be the *rendering* field of view. Getting it wrong is subtle:
     * a world-locked flat plane seen off-axis appears to rotate as the gaze
     * crosses it, reversing direction either side of centre, because near and
     * far edges are displaced by the wrong amounts. Nothing looks broken
     * head-on, so it is easy to ship.
     *
     * The null point of that rotation measures it far more precisely than a spec
     * sheet does.
     */
    val diagonalFovDegrees: Float,
    val perEyeWidth: Int,
    val perEyeHeight: Int,
    val defaultIpdMm: Float = 63f,
    /**
     * Rotation, in degrees, from the IMU's mounting to the display's optical
     * axis. Glasses commonly mount the sensor board at a slight pitch, which
     * shows up as a constant horizon tilt if ignored.
     */
    val imuPitchOffsetDegrees: Float = 0f,
    /** Nominal sample rate, for sizing buffers and sanity-checking the stream. */
    val imuRateHz: Int,
    /** Aspect ratio of one eye, used to split the diagonal FoV into components. */
    val eyeAspect: Float = 16f / 9f,
    /**
     * Distance at which the optics form the virtual image, in metres.
     *
     * Birdbath optics focus at one fixed distance. Rendering stereo disparity
     * for some *other* distance sets vergence against accommodation, and the
     * result is not a subtle artefact — content placed at 2 m when the image
     * forms at 4 m reads as persistently, unplaceably wrong.
     *
     * So panel depth is **not a free parameter**. The compositor should place
     * panels here by default and treat departures from it as a comfort cost,
     * which is a real constraint on any UI that wants to lay windows out in
     * depth.
     */
    val virtualImageDistanceMetres: Float = 4f,
    /**
     * Motion-to-photon prediction hint for this hardware, as
     * `constant + frametimeMultiplier * frametime`. Measured per device;
     * see spike S6.
     */
    val predictionConstantMs: Float = 0f,
    val predictionFrametimeMultiplier: Float = 1f,
    /**
     * Multiplies the factory-calibrated frustum. Below 1 where wearers judge
     * the calibration's field of view too wide, which on XREAL hardware they
     * consistently have.
     */
    val calibratedFovScale: Float = 1f,
    /**
     * How far the compositor spreads a head movement down the frame, as a
     * fraction of one refresh: 1 for a panel lit top row first across the
     * whole refresh (XREAL, measured), 0 for one lit all at once or whose scan
     * is not known.
     *
     * Wrong is worse than off. A per-row correction for a scan the panel does
     * not do stretches and squeezes the image on every nod, which reads as
     * dropped frames — most of all looking up and down, since that is the
     * direction the rows run in.
     */
    val scanoutCompensation: Float = 1f,
    /**
     * How far, in degrees, the published head pose may sit still while the
     * head is at rest before it follows — 0 for none.
     *
     * Glasses' own "pinned" modes look rock-solid largely because they hold
     * the image while the head is still; a raw IMU that is noisy at rest
     * (RayNeo GT series: ~0.7 °/s) otherwise shows its noise as a shimmer.
     * Only engaged at rest, so a moving head is tracked without added lag.
     */
    val restDeadbandDegrees: Float = 0f,
) {
    /**
     * Vertical half-angle follows from the diagonal and the aspect:
     * `tan(hDiag) = tan(hVert) * sqrt(1 + aspect^2)`.
     */
    val verticalFovDegrees: Float
        get() {
            val halfDiag = Math.toRadians(diagonalFovDegrees / 2.0)
            val halfVert = kotlin.math.atan(
                kotlin.math.tan(halfDiag) / kotlin.math.sqrt(1.0 + eyeAspect * eyeAspect)
            )
            return (2.0 * Math.toDegrees(halfVert)).toFloat()
        }
}

