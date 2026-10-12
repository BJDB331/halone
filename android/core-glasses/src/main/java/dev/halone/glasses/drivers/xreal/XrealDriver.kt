package dev.halone.glasses.drivers.xreal

import dev.halone.glasses.api.AxisConvention
import dev.halone.glasses.api.DeviceInfo
import dev.halone.glasses.api.DisplayMode
import dev.halone.glasses.api.DriverMatch
import dev.halone.glasses.api.GlassesDevice
import dev.halone.glasses.api.GlassesDriver
import dev.halone.glasses.api.Optics
import dev.halone.glasses.api.StereoLayout
import dev.halone.glasses.api.TransportRequest
import dev.halone.glasses.api.UsbIdentity
import dev.halone.glasses.transport.HidTransportProvider

/**
 * Per-model facts. Everything that differs between Air-series models lives
 * here so the session code below stays model-agnostic.
 */
internal data class XrealModel(
    val productId: Int,
    val model: String,
    val imuInterface: Int,
    val mcuInterface: Int,
    /**
     * The vendor's own libraries name three HID interfaces — imu, vsync and
     * sdk — so display refresh reporting has an endpoint of its own rather than
     * sharing the control one. Null where it has not been identified.
     */
    val vsyncInterface: Int?,
    val imuPayloadSize: Int,
    val optics: Optics,
    val modes: List<DisplayMode>,
)

class XrealDriver : GlassesDriver {

    override val id = "xreal"

    override val supportedDevices =
        MODELS.values.joinToString(", ") { "XREAL ${it.model}" } +
            " (Air and Air 2 Pro verified on hardware; others from published protocol notes)"

    override fun probe(identity: UsbIdentity): DriverMatch? {
        if (identity.vendorId != VENDOR_ID) return null
        val model = MODELS[identity.productId] ?: return null
        return DriverMatch(
            info = DeviceInfo(
                vendor = "XREAL",
                model = model.model,
                driverId = "$id:${model.model.lowercase().replace(" ", "")}",
            ),
            optics = model.optics,
            transports = TransportRequest(
                listOfNotNull(model.imuInterface, model.mcuInterface, model.vsyncInterface)
            ),
            tag = model,
        )
    }

    override fun connect(
        match: DriverMatch,
        transports: HidTransportProvider,
    ): Result<GlassesDevice> {
        val model = match.tag as? XrealModel
            ?: return Result.failure(IllegalArgumentException("match did not come from XrealDriver"))

        val imu = transports.open(model.imuInterface).getOrElse {
            return Result.failure(IllegalStateException("could not open IMU interface ${model.imuInterface}: ${it.message}"))
        }
        // The MCU is optional: without it we lose display-mode and brightness
        // control but head tracking, the thing that matters most, still works.
        val mcu = transports.open(model.mcuInterface).getOrNull()
        val vsync = model.vsyncInterface?.let { transports.open(it).getOrNull() }

        return Result.success(XrealSession(match.info, model, imu, mcu, vsync, AXES))
    }

    internal companion object {
        const val VENDOR_ID = 0x3318

        /**
         * Measured on an Air 2 Pro with the glasses worn (see `docs/protocol/
         * xreal-hid.md`). XREAL's IMU frame is **not** the canonical one:
         *
         * - worn level, gravity reads hardware **+Z**, so hardware +Z is up
         *   → canonical Y = hw +Z
         * - chin down 32°, gravity moves onto hardware **+Y**, so hardware +Y
         *   points backward → canonical Z = hw +Y
         * - the remaining axis is then forced, not chosen: for a right-handed
         *   frame, canonical X = Y × Z = (hw Z) × (hw Y) = **−hw X**
         *
         * The remap has determinant +1, so it is a proper rotation and angular
         * velocity transforms with it unchanged — no extra sign flip for the gyro.
         *
         * Assuming identity here is what made the horizon roll away over
         * seconds: the gravity correction was comparing against the wrong axis,
         * so it could not anchor pitch and roll at all.
         */
        val AXES = AxisConvention(
            xFrom = AxisConvention.Axis.X_NEG,
            yFrom = AxisConvention.Axis.Z_POS,
            zFrom = AxisConvention.Axis.Y_POS,
        )

        private fun airOptics(rateHz: Int = 1000) = Optics(
            // 37.7 (19.0 vertical), not the 46 on the spec sheet. Measured on
            // hardware by finding where a world-locked plane stops appearing to
            // rotate as the gaze crosses it — a sharp null: 22 showed the
            // artefact clearly, 19 did not.
            //
            // The gap from the published figure is large enough to be worth
            // doubting, but 46 produced a visible artefact and this does not.
            // Possible explanations we have not separated: the spec may describe
            // the optical field rather than the one the video signal maps onto,
            // or may be measured differently.
            diagonalFovDegrees = 37.7f,
            // Measured by holding apparent size constant and varying only
            // disparity: 2 m felt persistently wrong, 4 m settled.
            virtualImageDistanceMetres = 4f,
            perEyeWidth = 1920,
            perEyeHeight = 1080,
            imuRateHz = rateHz,
            // ~102 ms of horizon at 60 Hz, from two independent measurements
            // that agree:
            //
            //  - high-speed camera: mean 112 ms over the best-conditioned takes
            //  - perceptual bracket on the glasses: residual lag still visible at
            //    90 ms, overshoot appearing at 120 ms, so ~100-110 ms
            //
            // Set just below the overshoot threshold: residual lag is far less
            // objectionable than a world that wobbles on every head movement.
            //
            // Worth knowing that this only became measurable by feel after the
            // scanout shear was corrected. Before that the skew masked the lag
            // entirely, and 57 ms felt indistinguishable from correct.
            predictionConstantMs = 85f,
            predictionFrametimeMultiplier = 1f,
        )

        /**
         * Mode bytes are firmware values, opaque above the driver. The Air and
         * Air 2 Pro accept 1 and 3 (verified); the higher-refresh variants are published
         * but untested, so they are listed and will simply fail cleanly if the
         * firmware rejects them.
         */
        private fun airModes(verifiedOnHardware: Boolean) = listOf(
            DisplayMode("mirror-60", StereoLayout.MONO, 1920, 1080, 60f, verifiedOnHardware),
            DisplayMode("mirror-72", StereoLayout.MONO, 1920, 1080, 72f),
            DisplayMode("mirror-90", StereoLayout.MONO, 1920, 1080, 90f),
            DisplayMode("mirror-120", StereoLayout.MONO, 1920, 1080, 120f),
            DisplayMode("sbs-60", StereoLayout.FULL_SBS, 3840, 1080, 60f, verifiedOnHardware),
            DisplayMode("sbs-72", StereoLayout.FULL_SBS, 3840, 1080, 72f),
            DisplayMode("sbs-90", StereoLayout.FULL_SBS, 3840, 1080, 90f),
            DisplayMode("half-sbs-60", StereoLayout.HALF_SBS, 1920, 1080, 60f),
        )

        /** Driver mode key -> firmware mode byte. */
        val MODE_BYTES = mapOf(
            "mirror-60" to 1,
            "sbs-60" to 3,
            "sbs-72" to 4,
            "mirror-72" to 5,
            "half-sbs-60" to 8,
            "sbs-90" to 9,
            "mirror-90" to 10,
            "mirror-120" to 11,
        )

        val MODELS = mapOf(
            0x0424 to XrealModel(0x0424, "Air", 3, 4, 5, 64, airOptics(), airModes(true)),
            0x0428 to XrealModel(0x0428, "Air 2", 3, 4, 5, 64, airOptics(), airModes(false)),
            // Air and Air 2 Pro: mirror-60 and sbs-60 are confirmed.
            0x0432 to XrealModel(0x0432, "Air 2 Pro", 3, 4, 5, 64, airOptics(), airModes(true)),
            // Different interfaces and a larger payload; 6DoF-capable hardware
            // that this driver does not yet exploit.
            0x0426 to XrealModel(0x0426, "Air 2 Ultra", 2, 0, null, 512, airOptics(), airModes(false)),
        )
    }
}
