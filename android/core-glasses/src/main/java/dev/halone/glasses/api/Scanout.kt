package dev.halone.glasses.api

/**
 * A display refresh the glasses have timed for us.
 *
 * Not one per refresh: a device is free to report a beacon every so many
 * frames, which is enough to recover both phase and period and far cheaper than
 * an interrupt per frame. [sequence] counts beacons, not frames.
 *
 * [deviceTimeNs] is the interesting half — taken by the display controller
 * itself, on the clock the IMU stamps its samples with, and free of the jitter
 * of getting the news across USB. [hostTimeNs] is merely when we heard, which
 * measures that delay and nothing else. [periodNs] is the refresh interval the
 * device reports, which may be nominal; the interval between beacons is the
 * more precise measurement of the same thing.
 */
data class ScanoutEvent(
    val sequence: Long,
    val deviceTimeNs: Long,
    val periodNs: Long,
    val hostTimeNs: Long,
)

/**
 * One round trip against the device's clock, in the shape NTP uses.
 *
 * [offsetNs] is what to add to a host reading to get a device one. Its accuracy
 * is bounded by [roundTripNs]: with the two one-way delays unknown but summing
 * to the round trip, the offset is known to within half of it. That is why the
 * round trip is reported rather than hidden — a sample taken while the endpoint
 * was busy is worth less than a quiet one, and only the caller can see how many
 * it can afford to take.
 */
data class ClockSample(
    val offsetNs: Long,
    val roundTripNs: Long,
)

/**
 * Display timing straight from the hardware, for compositors that would
 * otherwise have to estimate when a frame will actually be lit.
 *
 * Optional: a device that cannot report vsync simply does not offer this, and
 * the compositor falls back to a measured constant.
 */
interface ScanoutSource {

    /**
     * Starts reporting refreshes to [listener], which is called on the reader
     * thread and must not block it.
     */
    fun start(listener: (ScanoutEvent) -> Unit): Result<Unit>

    fun stop()

    /** One clock round trip. Costs a command exchange, so it is the caller's to pace. */
    fun syncClock(): Result<ClockSample>

    /**
     * Recent raw packets from the timing endpoint, whether or not they were
     * understood, newest last. For bringing a new device up: framing is the one
     * thing that cannot be inferred from the numbers when the numbers are wrong.
     */
    val recentPackets: List<String> get() = emptyList()

    /** Every packet the timing endpoint has delivered, understood or not. */
    val packetsSeen: Long get() = 0
}
