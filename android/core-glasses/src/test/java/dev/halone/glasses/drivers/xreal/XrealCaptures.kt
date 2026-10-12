package dev.halone.glasses.drivers.xreal

import dev.halone.glasses.transport.ReplayHidTransport.Companion.parseHex

/**
 * Real frames captured from an XREAL Air 2 Pro (Galaxy S26 Ultra, Android 16).
 *
 * Using genuine captures rather than hand-built fixtures is deliberate: every
 * protocol mistake we made was the kind that produced plausible-looking wrong
 * answers from synthetic data, and only real bytes caught them.
 */
internal object XrealCaptures {

    /** Sensor frame, glasses at rest on a desk. */
    val SENSOR_AT_REST = (
        "01 02 00 00 d8 27 63 34 31 00 00 00 a0 0f 00 00 " +
            "00 01 40 06 00 c0 00 00 c0 07 00 20 00 00 00 00 " +
            "01 c0 dc ff 80 3c 02 40 f3 07 00 80 00 04 00 00 " +
            "ab 7f 18 80 72 81 00 00 00 00 00 00 00 00 00 00"
        ).parseHex()

    /** Reply to a read-display-mode request while in mirror mode (payload `01`). */
    val REPLY_MODE_MIRROR = pad(
        "fd 98 58 06 b3 16 00 00 00 00 00 96 4b fe 1b 07 00 00 00 00 00 00 00 01"
    )

    /** Reply to a read-display-mode request while in SBS mode (payload `03`). */
    val REPLY_MODE_SBS = pad(
        "fd 9d b3 ff 6f 16 00 00 00 00 00 6a 0b 6e 1c 07 00 00 00 00 00 00 00 03"
    )

    /** Acknowledgement of a write-display-mode command (msgid 0x08). */
    val REPLY_MODE_WRITE_ACK = pad(
        "fd 1b 32 50 1a 12 00 00 00 00 00 19 00 7d 2a 08 00 00 00 00 00 00 00 00"
    )

    /**
     * An unsolicited MCU packet (msgid 0x6C18) that arrived interleaved with
     * command replies on the same endpoint. This is the frame that made our
     * first display-mode reads return garbage.
     */
    val ASYNC_UNSOLICITED = pad(
        "fd 7b e9 65 8a 12 00 00 00 00 00 65 5c 3b 00 18 6c 00 00 00 00 00 01 00"
    )

    /** Acknowledgement of an IMU start/stop command, echoing the command framing. */
    val IMU_COMMAND_ACK = pad("aa 53 e1 26 35 04 00 19 00")

    private fun pad(hex: String): ByteArray {
        return hex.parseHex().copyOf(64)
    }
}
