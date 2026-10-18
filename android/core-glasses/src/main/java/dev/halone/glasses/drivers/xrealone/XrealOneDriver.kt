package dev.halone.glasses.drivers.xrealone

import dev.halone.glasses.api.AxisConvention
import dev.halone.glasses.api.DeviceInfo
import dev.halone.glasses.api.DisplayMode
import dev.halone.glasses.api.DriverMatch
import dev.halone.glasses.api.GlassesDevice
import dev.halone.glasses.api.GlassesDriver
import dev.halone.glasses.api.NetworkRequest
import dev.halone.glasses.api.Optics
import dev.halone.glasses.api.StereoLayout
import dev.halone.glasses.api.TransportRequest
import dev.halone.glasses.api.UsbIdentity
import dev.halone.glasses.transport.HidTransportProvider

internal data class XrealOneModel(
    val productId: Int,
    val model: String,
    val optics: Optics,
    val modes: List<DisplayMode>,
)

/**
 * XREAL One and One Pro. Unlike the Air series these carry their sensors and
 * controls over a USB network link (see `docs/protocol/xreal-one.md`), so the
 * driver asks for TCP ports and never claims a USB interface.
 *
 * Written from published reverse-engineering and one real capture; nothing
 * here has run against hardware we own.
 */
class XrealOneDriver : GlassesDriver {

    override val id = "xrealone"

    override val supportedDevices =
        MODELS.values.joinToString(", ") { "XREAL ${it.model}" } +
            " (from published protocol notes; not yet verified on hardware)"

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
                network = NetworkRequest(
                    hosts = listOf(XrealOneProtocol.HOST_NCM, XrealOneProtocol.HOST_ECM),
                    ports = listOf(XrealOneProtocol.PORT_REPORTS, XrealOneProtocol.PORT_CONTROL),
                ),
            ),
            tag = model,
        )
    }

    override fun connect(match: DriverMatch, transports: HidTransportProvider): Result<GlassesDevice> {
        val model = match.tag as? XrealOneModel
            ?: return Result.failure(IllegalArgumentException("match did not come from XrealOneDriver"))

        val reports = transports.open(XrealOneProtocol.PORT_REPORTS).getOrElse {
            return Result.failure(
                IllegalStateException(
                    "could not open the report stream: ${it.message}. Check that the glasses are in " +
                        "Follow mode with Stabilizer off; the port stays closed otherwise.",
                )
            )
        }
        val control = transports.open(XrealOneProtocol.PORT_CONTROL).getOrNull()

        return Result.success(XrealOneSession(match.info, model, reports, control, AXES))
    }

    internal companion object {
        const val VENDOR_ID = 0x3318

        /**
         * The report frame is x right, y down, z forward. Measured on a 1S, then
         * confirmed on one One Pro by a test-kit session: look up/down, tilt and
         * worn gravity all agree, with the IMU pitched ~31° against the head.
         */
        val AXES = AxisConvention(
            xFrom = AxisConvention.Axis.X_POS,
            yFrom = AxisConvention.Axis.Y_NEG,
            zFrom = AxisConvention.Axis.Z_NEG,
        )

        val INPUT_MODES = mapOf(
            "mono" to XrealOneProtocol.INPUT_MODE_MONO,
            "sbs" to XrealOneProtocol.INPUT_MODE_SBS,
        )

        /**
         * The refresh rate is picked in the glasses' own menu and cannot be set
         * from here, so each layout is listed once at the rate the 1S used.
         */
        private val MODES = listOf(
            DisplayMode("mono", StereoLayout.MONO, 1920, 1080, 60f),
            DisplayMode("sbs", StereoLayout.FULL_SBS, 3840, 1080, 60f),
        )

        /** Spec-sheet field of view; the factory calibration replaces it when readable. */
        private fun optics(diagonalFovDegrees: Float) = Optics(
            diagonalFovDegrees = diagonalFovDegrees,
            perEyeWidth = 1920,
            perEyeHeight = 1080,
            imuRateHz = 1000,
            // The Air's measured latency, standing in until one of these is measured.
            predictionConstantMs = 85f,
            predictionFrametimeMultiplier = 1f,
        )

        val MODELS = mapOf(
            0x0436 to XrealOneModel(0x0436, "One Pro", optics(57f), MODES),
            0x0438 to XrealOneModel(0x0438, "One", optics(50f), MODES),
        )
    }
}
