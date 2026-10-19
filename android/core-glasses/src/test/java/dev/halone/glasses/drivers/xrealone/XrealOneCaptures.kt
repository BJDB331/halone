package dev.halone.glasses.drivers.xrealone

import java.io.ByteArrayOutputStream

internal object XrealOneCaptures {

    /** 600 messages straight off a One Pro's port 52998; see `resources/xrealone/LICENSE`. */
    val REPORTS: ByteArray by lazy {
        XrealOneCaptures::class.java.getResourceAsStream("/xrealone/one-pro-reports.bin")!!.use { it.readBytes() }
    }

    fun messages(stream: ByteArray): List<Pair<Int, ByteArray>> {
        val out = mutableListOf<Pair<Int, ByteArray>>()
        XrealOneFramer(1 shl 20).apply {
            append(stream, stream.size)
            drain { magic, data, offset, length -> out += magic to data.copyOfRange(offset, offset + length) }
        }
        return out
    }

    fun firstImuMessage(): ByteArray {
        val (_, body) = messages(REPORTS).first { (_, body) -> XrealOneReport.type(body, 0) == XrealOneReport.TYPE_IMU }
        return frame(XrealOneProtocol.MSG_REPORT, body)
    }

    fun frame(magic: Int, payload: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write((magic ushr 8) and 0xFF)
        write(magic and 0xFF)
        for (shift in intArrayOf(24, 16, 8, 0)) write((payload.size ushr shift) and 0xFF)
        write(payload)
    }.toByteArray()

    fun reply(magic: Int, transactionId: Int, body: ByteArray): ByteArray {
        val payload = ByteArrayOutputStream().apply {
            for (shift in intArrayOf(24, 16, 8, 0)) write((transactionId ushr shift) and 0xFF)
            write(body)
        }.toByteArray()
        return frame(magic, payload)
    }

    /** `{4: {2: value}}`, the shape of a successful string reply. */
    fun stringReplyBody(value: String): ByteArray {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val inner = byteArrayOf(0x12) + varint(bytes.size) + bytes
        return byteArrayOf(0x22) + varint(inner.size) + inner
    }

    private fun varint(value: Int): ByteArray {
        val out = ByteArrayOutputStream()
        var rest = value
        while (rest >= 0x80) {
            out.write((rest and 0x7F) or 0x80)
            rest = rest ushr 7
        }
        out.write(rest)
        return out.toByteArray()
    }

    /** Same schema as a real blob, invented numbers, and the IMU pitched 35° against the displays. */
    val CONFIG_JSON = """
        {
          "FSN": "TEST00000",
          "glasses_version": 8,
          "last_modified_time": "2026-01-01 00:00:00",
          "IMU": {"device_1": {
            "accel_bias": [0.01, 0.02, 0.03], "gyro_bias": [-0.003, -0.002, -0.001],
            "mag_bias": [0, 0, 0], "scale_accel": [1, 1, 1], "scale_gyro": [1, 1, 1], "scale_mag": [1, 1, 1],
            "gyro_q_mag": [-0.5, -0.5, 0.5, 0.5], "imu_noises": [0.0003, 0.00001, 0.006, 0.0006]
          }},
          "display": {
            "resolution": [1920, 1080],
            "k_left_display": [2200, 0, 960, 0, 2200, 540, 0, 0, 1],
            "k_right_display": [2200, 0, 960, 0, 2200, 540, 0, 0, 1],
            "target_p_left_display": [-0.06, 0.005, -0.03],
            "target_p_right_display": [0.005, 0.005, -0.03],
            "target_q_left_display": [-0.300706, 0, 0, 0.953717],
            "target_q_right_display": [-0.300706, 0, 0, 0.953717]
          },
          "display_distortion": {"left_display": {"type": 0}, "right_display": {"type": 0}}
        }
    """.trimIndent()
}
