package dev.halone.glasses.transport

import java.io.Closeable

/**
 * A raw bidirectional HID channel: no report IDs, no framing, just bytes in and
 * bytes out, matching what hidraw gives the reference Linux drivers.
 *
 * Abstracting this is what makes drivers testable. A driver written against
 * `HidTransport` can be replayed against a recorded packet capture on a
 * developer machine with no hardware attached, which is the difference between
 * a project that accepts community device support and one that cannot.
 */
interface HidTransport : Closeable {
    val name: String

    /** Returns bytes written, or a negative value on failure. */
    fun write(packet: ByteArray, timeoutMs: Int = DEFAULT_TIMEOUT_MS): Int

    /** Returns bytes read, 0 on timeout, or a negative value on failure. */
    fun read(into: ByteArray, timeoutMs: Int = DEFAULT_TIMEOUT_MS): Int

    companion object {
        const val DEFAULT_TIMEOUT_MS = 250
    }
}

/**
 * Opens the transports a driver asked for. Keeping this behind an interface is
 * what lets the registry hand a driver real USB endpoints in production and
 * canned buffers under test.
 */
interface HidTransportProvider {
    /** @param interfaceId the USB `bInterfaceNumber`, not an enumeration index. */
    fun open(interfaceId: Int): Result<HidTransport>

    /**
     * The `bInterfaceNumber` of every HID-class interface on the device, for
     * drivers whose hardware moves its interfaces around between
     * configurations. Empty when the provider cannot tell, as under test.
     */
    fun hidInterfaceIds(): List<Int> = emptyList()
}
