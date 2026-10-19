package dev.halone.glasses.api

import dev.halone.glasses.transport.HidTransportProvider

/**
 * Identifies a USB device without committing to opening it. Deliberately not
 * Android's `UsbDevice`, so drivers stay unit-testable and the registry can be
 * exercised without a device attached.
 */
data class UsbIdentity(
    val vendorId: Int,
    val productId: Int,
    val manufacturerName: String? = null,
    val productName: String? = null,
    /** Distinguishes two identical pairs of glasses on the same host. */
    val busAddress: String? = null,
)

/**
 * What a driver needs claimed on its behalf before it can talk to the device.
 */
data class TransportRequest(
    /** USB `bInterfaceNumber` values, in the order the driver wants them. */
    val interfaceIds: List<Int> = emptyList(),
    /**
     * Set when the device's data link is a network the glasses bring up over
     * USB rather than a claimed interface. The host then opens TCP ports
     * instead, and needs no USB permission at all.
     */
    val network: NetworkRequest? = null,
) {
    val needsUsbPermission: Boolean get() = interfaceIds.isNotEmpty()
}

/**
 * [ports] are what the driver passes to [HidTransportProvider.open]. [hosts]
 * are tried in order until one answers.
 */
data class NetworkRequest(
    val hosts: List<String>,
    val ports: List<Int>,
)

/**
 * The extension point for supporting new hardware.
 *
 * A driver is stateless and cheap to construct: it answers "do I recognise this
 * device", declares which interfaces it needs, and then builds a
 * [GlassesDevice] over transports it is handed. It never touches Android USB
 * APIs itself, which is what keeps it testable and keeps permission handling in
 * one place.
 *
 * See `docs/adding-a-device.md` for the full walkthrough.
 */
interface GlassesDriver {
    /** Stable identifier, e.g. `xreal`. Appears in logs, settings and bug reports. */
    val id: String

    /** Human-readable list of what this driver claims to support. */
    val supportedDevices: String

    /**
     * Return a match if this driver handles the device, or null to pass.
     * Must not perform I/O — this runs for every attached USB device.
     */
    fun probe(identity: UsbIdentity): DriverMatch?

    /**
     * Build a device over the transports described by [DriverMatch.transports].
     * Called once permission is granted and the interfaces are claimed.
     */
    fun connect(match: DriverMatch, transports: HidTransportProvider): Result<GlassesDevice>
}

/**
 * A driver's answer to [GlassesDriver.probe]: enough to show the user what was
 * recognised, and enough for the host to claim the right interfaces.
 */
data class DriverMatch(
    val info: DeviceInfo,
    val optics: Optics,
    val transports: TransportRequest,
    /** Opaque driver-private payload, so probe results need no re-derivation. */
    val tag: Any? = null,
    val setup: DeviceSetup? = null,
)
