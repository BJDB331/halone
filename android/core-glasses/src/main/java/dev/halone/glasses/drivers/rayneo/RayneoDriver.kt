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
            if (handshake == null) {
                failures += "interface $interfaceId: no reply to device info"
                runCatching { transport.close() }
                continue
            }

            return Result.success(
                RayneoSession(
                    info = match.info,
                    optics = opticsFor(handshake.board),
                    board = handshake.board,
                    firmwareBuild = handshake.build,
                    initialBrightness = handshake.brightness,
                    transport = transport,
                    axes = AXES,
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

    private class Handshake(val board: Int, val build: String?, val brightness: Int?)

    /**
     * Asks the interface who it is. Doubles as the check that this is the
     * right interface at all: the glasses have been seen enumerating the vendor
     * HID as interface 0 and, in a composite configuration, as interface 5.
     *
     * Also stops any stream a previous process left running — it survives the
     * host closing the device — so the endpoint starts quiet.
     */
    private fun handshake(transport: HidTransport): Handshake? {
        var reply: RayneoReport.Ack? = null
        RayneoChannel(transport, onReport = {}).also { probe ->
            probe.start()
            try {
                // Not awaited: this runs on the caller's thread, which may be
                // the UI's, once per candidate interface.
                probe.send(RayneoProtocol.CMD_STREAM_OFF)
                reply = probe.request(RayneoProtocol.CMD_DEVICE_INFO)
            } finally {
                probe.close()
            }
        }
        val info = reply ?: return null
        return Handshake(
            board = info.board,
            build = RayneoProtocol.buildDate(info),
            brightness = RayneoProtocol.brightness(info),
        )
    }

    internal companion object {

        const val SERIES_MODEL = "GT / GT Max"

        /** Live (display-active) configuration first, then the composite one. */
        val INTERFACE_CANDIDATES = listOf(0, 5)

        /**
         * Sensor frame, from the GT's measured head mounting: right is sensor
         * +X, forward is sensor +Y, up is sensor +Z, and gravity at rest reads
         * on +Z. Into the canonical frame (+X right, +Y up, +Z toward the
         * wearer):
         *
         * - canonical X = sensor +X
         * - canonical Y = sensor +Z
         * - canonical Z = sensor −Y (backward is minus forward)
         *
         * An odd permutation with one sign flip, so determinant +1: a proper
         * rotation, and the gyro transforms with it unchanged.
         *
         * **[unverified]** The source calls this mapping approximate, a second
         * project reports the sensor board pitched about 29° in the frame, and
         * neither had a GT Max. Run the `axes` development action on GT Max
         * hardware before trusting it, and set `imuPitchOffsetDegrees` from
         * what it shows.
         */
        val AXES = AxisConvention(
            xFrom = AxisConvention.Axis.X_POS,
            yFrom = AxisConvention.Axis.Z_POS,
            zFrom = AxisConvention.Axis.Y_NEG,
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
