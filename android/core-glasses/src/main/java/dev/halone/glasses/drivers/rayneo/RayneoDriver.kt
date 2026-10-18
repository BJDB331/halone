package dev.halone.glasses.drivers.rayneo

import dev.halone.glasses.api.AxisConvention
import dev.halone.glasses.api.DeviceInfo
import dev.halone.glasses.api.DriverMatch
import dev.halone.glasses.api.GlassesDevice
import dev.halone.glasses.api.GlassesDriver
import dev.halone.glasses.api.Optics
import dev.halone.glasses.api.TransportRequest
import dev.halone.glasses.api.UsbIdentity
import dev.halone.glasses.transport.HidTransport
import dev.halone.glasses.transport.HidTransportProvider

/**
 * RayNeo GT series: the GT and the GT Max.
 *
 * Both enumerate as `3941:AF50` and speak the same protocol; only the board id
 * in the device-info reply tells them apart, and `probe` may not do I/O. So
 * the match names the series, and [connect] picks the optics once the glasses
 * have said which they are.
 *
 * Raw IMU over one vendor HID interface, fused here: the glasses' own on-board
 * 3DoF is switched to plain "follow" for the session so that it does not
 * counter-rotate the image as well. See `docs/protocol/rayneo-hid.md`.
 *
 * **Not yet run on hardware by this project.** Everything below comes from two
 * independent open-source GT projects that did run it, and the GT Max specifics
 * — its board id and optics — have not been seen by either. The `[unverified]`
 * notes say what to check.
 */
class RayneoDriver : GlassesDriver {

    override val id = "rayneo"

    override val supportedDevices =
        "RayNeo GT Max, RayNeo GT (from published protocol notes; not yet verified on hardware)"

    override fun probe(identity: UsbIdentity): DriverMatch? {
        if (identity.vendorId != RayneoProtocol.VENDOR_ID) return null
        if (identity.productId != RayneoProtocol.PRODUCT_ID) return null
        return DriverMatch(
            info = DeviceInfo(
                vendor = "RayNeo",
                model = SERIES_MODEL,
                driverId = "$id:gt",
            ),
            // The GT Max is the model this driver is aimed at; connect swaps in
            // the GT's optics if the board says otherwise.
            optics = GT_MAX_OPTICS,
            transports = TransportRequest(INTERFACE_CANDIDATES),
        )
    }

    override fun connect(match: DriverMatch, transports: HidTransportProvider): Result<GlassesDevice> {
        val failures = mutableListOf<String>()

        // The two numbers seen so far first, then any other HID interface the
        // device has: the vendor interface has not been pinned down on Android.
        val candidates = (match.transports.interfaceIds + transports.hidInterfaceIds()).distinct()

        for (interfaceId in candidates) {
            val transport = transports.open(interfaceId).getOrElse {
                failures += "interface $interfaceId: ${it.message}"
                null
            } ?: continue

            val handshake = handshake(transport)
            if (handshake is Handshake.Silent) {
                failures += "interface $interfaceId: no reply to device info [${transport.name}; ${handshake.diagnosis}]"
                runCatching { transport.close() }
                continue
            }
            handshake as Handshake.Answered

            return Result.success(
                RayneoSession(
                    info = match.info,
                    optics = opticsFor(handshake.board),
                    board = handshake.board,
                    firmwareBuild = handshake.build,
                    initialBrightness = handshake.brightness,
                    transport = transport,
                    axes = AXES,
                    framing = handshake.framing,
                )
            )
        }

        return Result.failure(
            IllegalStateException(
                "no RayNeo control interface answered (tried ${candidates.joinToString()}: " +
                    "${failures.joinToString("; ")})"
            )
        )
    }

    private sealed interface Handshake {
        class Answered(
            val board: Int,
            val build: String?,
            val brightness: Int?,
            val framing: RayneoProtocol.Framing,
        ) : Handshake

        /** What the endpoint did instead of answering, per framing tried. */
        class Silent(val diagnosis: String) : Handshake
    }

    /**
     * Asks the interface who it is. Doubles as the check that this is the
     * right interface at all: the glasses have been seen enumerating the vendor
     * HID as interface 0 and, in a composite configuration, as interface 5.
     *
     * Also stops any stream a previous process left running — it survives the
     * host closing the device — so the endpoint starts quiet.
     */
    private fun handshake(transport: HidTransport): Handshake {
        val diagnoses = mutableListOf<String>()
        for (framing in RayneoProtocol.Framing.entries) {
            val probe = RayneoChannel(transport, onReport = {}, framing = framing)
            val info = try {
                probe.start()
                // Not awaited: this runs on the caller's thread, which may be
                // the UI's, once per candidate interface.
                probe.send(RayneoProtocol.CMD_STREAM_OFF)
                probe.request(RayneoProtocol.CMD_DEVICE_INFO)
            } finally {
                probe.close()
            }
            if (info != null) {
                return Handshake.Answered(
                    board = info.board,
                    build = RayneoProtocol.buildDate(info),
                    brightness = RayneoProtocol.brightness(info),
                    framing = framing,
                )
            }
            diagnoses += "${framing.name.lowercase()}: ${probe.describe()}"
        }
        return Handshake.Silent(diagnoses.joinToString("; "))
    }

    internal companion object {

        const val SERIES_MODEL = "GT / GT Max"

        /** Live (display-active) configuration first, then the composite one. */
        val INTERFACE_CANDIDATES = listOf(0, 5)

        /**
         * Sensor frame: **forward is sensor +X, left is +Y, up is +Z**, with
         * gravity at rest on +Z — the frame the macOS GT project works in
         * with an identity mounting. Into the canonical frame (+X right, +Y
         * up, +Z toward the wearer):
         *
         * - canonical X = sensor −Y (right is minus left)
         * - canonical Y = sensor +Z
         * - canonical Z = sensor −X (backward is minus forward)
         *
         * A cyclic permutation with two sign flips, so determinant +1: a
         * proper rotation, and the gyro transforms with it unchanged.
         *
         * **[measured]** on a GT Max, the mapping first used here — right on
         * sensor +X, forward on +Y, from the Windows project's notes — was a
         * quarter turn out: nodding rolled the scene and the horizon sat
         * tilted. Forward and left are the two horizontal axes swapped back.
         * With it, nods and tilts stay world-locked on a GT Max; the board's
         * pitch in the frame is corrected through `imuPitchOffsetDegrees`.
         */
        val AXES = AxisConvention(
            xFrom = AxisConvention.Axis.Y_NEG,
            yFrom = AxisConvention.Axis.Z_POS,
            zFrom = AxisConvention.Axis.X_NEG,
        )

        /**
         * GT Max: 1920x1080 micro-OLED per eye, prism ("Peacock 3.0 Max")
         * optics, 59° advertised.
         */
        val GT_MAX_OPTICS = Optics(
            // [unverified] 57, not 59: the low end of the published figures
            // (57 and 59 both appear). On the GT, a rendering FoV tuned by eye
            // came out within a degree of the spec, so the spec is a fair
            // start — but measure it with the off-axis rotation null, as was
            // done for XREAL, before believing it.
            diagonalFovDegrees = 57f,
            perEyeWidth = 1920,
            perEyeHeight = 1080,
            // The frame comes in three sizes; M (64–67 mm) is the middle one.
            defaultIpdMm = 65f,
            // [unverified] Derived, not measured: the advertised 267" screen
            // (at 100% scale) spans 57–59° at about 6 m, and the GT's own
            // advertised size at its 46° lands in the same place.
            virtualImageDistanceMetres = 6f,
            // Measured on the GT, not the Max: 21.875 ticks of 100 µs per
            // sample, not the 476 Hz of earlier notes.
            imuRateHz = 457,
            // [measured, by eye] The board sits nose-up in the frame. The
            // macOS GT project reports ~29°; on a GT Max, 29° still left the
            // scene about 15° low at a level gaze, so 44°. Fine-tune by eye:
            // content high means lower this, content low means raise it.
            imuPitchOffsetDegrees = 44f,
            // [unverified] A placeholder, not a measurement: about half
            // XREAL's 85 ms, on the principle recorded there that residual lag
            // is far less objectionable than overshoot. Measure with the
            // `latency` action and a camera.
            predictionConstantMs = 40f,
            predictionFrametimeMultiplier = 1f,
        )

        /** GT: birdbath optics, 46°. A rendering FoV tuned by eye on one landed at 45–46°. */
        val GT_OPTICS = GT_MAX_OPTICS.copy(diagonalFovDegrees = 45.5f)

        fun opticsFor(board: Int): Optics =
            if (board == RayneoProtocol.BOARD_GT) GT_OPTICS else GT_MAX_OPTICS
    }
}
