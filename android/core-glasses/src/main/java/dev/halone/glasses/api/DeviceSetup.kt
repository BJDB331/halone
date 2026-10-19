package dev.halone.glasses.api

import dev.halone.glasses.transport.HidTransport

/**
 * What a host should tell the wearer, or do for them, before a device works:
 * for glasses whose behaviour depends on settings in their own menu that the
 * host cannot read. Optional on [DriverMatch]; most drivers have none.
 */
interface DeviceSetup {
    /** Something to do on the glasses before starting, shown as a reminder. */
    val beforeStarting: String?

    /** How to turn the data link on from the glasses' own menu, when it is missing. */
    val linkOffHelp: String

    /** The USB interface [enableLink] needs claimed. */
    val controlInterface: Int

    /**
     * Turns the device's data link on for the wearer. The device re-enumerates
     * when this succeeds, so every handle to it goes stale. Only ever called
     * when the wearer asked for it.
     */
    fun enableLink(transport: HidTransport): Result<Unit>
}
