package dev.halone.glasses.drivers.xreal

import dev.halone.glasses.transport.HidTransport

/**
 * Pulls the factory calibration blob off the glasses over the sensor endpoint.
 *
 * The transfer is two commands: `0x14` answers with the total size, then `0x15`
 * is sent repeatedly and each reply carries the next slice. On an Air 2 Pro the
 * blob is ~54 KB and a reply holds at most 56 payload bytes, so this is about a
 * thousand round trips — a second or so, not something to run per frame.
 *
 * **The framing here is inferred, not measured.** The command IDs come from the
 * open-source reference drivers, but neither of them implemented the transfer,
 * so the shape of the `0x14` answer (assumed: a little-endian u32 size) and the
 * assumption that `0x15` walks the blob in order are ours. They are written to
 * fail loudly and to hand back everything they did read, and every reply is
 * offered to [onReply] before interpretation so a capture survives a wrong
 * guess. Check `docs/protocol/xreal-hid.md` before trusting the assembled blob.
 */
internal class XrealCalibrationReader(
    private val transport: HidTransport,
    private val payloadSize: Int,
) {

    fun read(onReply: (ByteArray) -> Unit): Result<ByteArray> {
        // A stream left running by a *previous process* keeps arriving at ~1 kHz
        // and buries the reply we are waiting for. Our own session stops the
        // stream before calling this, but a fresh install after a streaming
        // session does not get that chance — which is exactly how this failed:
        // `0x14` answered, then the backlog swallowed every `0x15`.
        transport.write(XrealProtocol.stopImuStream())

        val sizeReply = request(
            XrealProtocol.readCalibrationLength(),
            XrealProtocol.IMU_MSG_GET_CAL_DATA_LENGTH,
            onReply,
        ) ?: return Result.failure(
            IllegalStateException("no reply to the calibration-length request (msgid 0x14)")
        )

        if (sizeReply.size < 4) {
            return Result.failure(
                IllegalStateException(
                    "calibration-length reply carried ${sizeReply.size} payload bytes, " +
                        "expected at least 4: ${XrealProtocol.hex(sizeReply)}"
                )
            )
        }

        val total = u32(sizeReply, 0)
        if (total <= 0 || total > MAX_BLOB_BYTES) {
            return Result.failure(
                IllegalStateException(
                    "calibration size $total is not plausible (payload " +
                        "${XrealProtocol.hex(sizeReply)}) — the length reply is probably " +
                        "not a little-endian u32, see the raw capture"
                )
            )
        }

        val blob = ByteArray(total)
        var got = 0
        var emptyReplies = 0

        while (got < total) {
            val segment = request(
                XrealProtocol.readCalibrationSegment(),
                XrealProtocol.IMU_MSG_GET_CAL_DATA,
                onReply,
            ) ?: return partial(blob, got, total, "device stopped answering segment requests")

            if (segment.isEmpty()) {
                // One empty reply could be a hiccup; a run of them means the
                // device has nothing more to give and our size is wrong.
                if (++emptyReplies >= MAX_EMPTY_REPLIES) {
                    return partial(blob, got, total, "$MAX_EMPTY_REPLIES empty replies in a row")
                }
                continue
            }
            emptyReplies = 0

            val take = minOf(segment.size, total - got)
            segment.copyInto(blob, got, 0, take)
            got += take
        }

        return Result.success(blob)
    }

    private fun partial(blob: ByteArray, got: Int, total: Int, why: String): Result<ByteArray> =
        Result.failure(
            IncompleteCalibration(
                partial = blob.copyOf(got),
                expected = total,
                message = "read $got of $total bytes: $why",
            )
        )

    /**
     * Writes a command and returns the payload of the first reply carrying the
     * matching msgid. Sensor frames keep arriving on this endpoint for a while
     * after the stream is stopped, so anything that is not a command reply is
     * skipped rather than treated as an error.
     */
    private fun request(command: ByteArray, msgId: Int, onReply: (ByteArray) -> Unit): ByteArray? {
        if (transport.write(command) < 0) return null

        val buffer = ByteArray(payloadSize)
        var frames = 0
        var timeouts = 0
        val deadline = System.nanoTime() + REQUEST_BUDGET_MS * 1_000_000L

        // Budgeted by wall clock rather than by frame count. A count is the
        // wrong unit: skipping a queued sensor frame costs microseconds, so a
        // backlog burns through any plausible frame allowance long before it
        // has cost enough time to be worth giving up on — which is how a
        // 256-frame budget turned into a 260 ms failure with the reply still in
        // the queue. The frame cap stays only as a backstop against a transport
        // that never blocks at all.
        while (frames < MAX_FRAMES_PER_REQUEST &&
            timeouts < MAX_TIMEOUTS_PER_REQUEST &&
            System.nanoTime() < deadline
        ) {
            val n = transport.read(buffer, READ_TIMEOUT_MS)
            if (n <= 0) {
                timeouts++
                continue
            }
            frames++
            if (buffer[0] != XrealProtocol.IMU_HEAD) continue

            val payloadLength = XrealProtocol.imuPayloadLength(buffer, n)
            if (payloadLength < 0) continue

            onReply(buffer.copyOf(n))
            if (XrealProtocol.imuMsgId(buffer) != msgId) continue

            return buffer.copyOfRange(
                XrealProtocol.IMU_REPLY_DATA_OFFSET,
                XrealProtocol.IMU_REPLY_DATA_OFFSET + payloadLength,
            )
        }
        return null
    }

    private fun u32(b: ByteArray, at: Int): Int {
        var v = 0L
        for (i in 0 until 4) v = v or ((b[at + i].toLong() and 0xFF) shl (8 * i))
        return if (v > Int.MAX_VALUE) -1 else v.toInt()
    }

    private companion object {
        /** An Air 2 Pro reports 54313; anything past this is a misparse. */
        const val MAX_BLOB_BYTES = 4 * 1024 * 1024
        const val MAX_EMPTY_REPLIES = 8
        /** Backstop only; the real limit is [REQUEST_BUDGET_MS]. */
        const val MAX_FRAMES_PER_REQUEST = 8192
        const val MAX_TIMEOUTS_PER_REQUEST = 3
        const val READ_TIMEOUT_MS = 150

        /**
         * How long one request may spend skipping other traffic. Generous
         * because it is only ever reached on the first request, where a stale
         * stream has to drain; the ~1000 that follow answer immediately.
         */
        const val REQUEST_BUDGET_MS = 1_000L
    }
}

/**
 * Thrown when the transfer stopped early. Carries what was read, because half a
 * calibration blob plus the raw capture is worth far more than an error string
 * when the format is still being worked out.
 */
class IncompleteCalibration(
    val partial: ByteArray,
    val expected: Int,
    message: String,
) : Exception(message)
