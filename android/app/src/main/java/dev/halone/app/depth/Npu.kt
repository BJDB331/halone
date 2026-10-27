package dev.halone.app.depth

import com.qualcomm.qti.QnnDelegate
import org.tensorflow.lite.Delegate

/**
 * Qualcomm's QNN delegate on the HTP backend: LiteRT models on the Hexagon
 * NPU of Snapdragon 8 Gen 1 and later, a chip of its own beside the GPU, so
 * estimating depth stops costing the compositor frames.
 *
 * The model is a float one, and the HTP's default is quantized networks only,
 * so it is asked for half precision. The option setters are found by name:
 * they are not in Google's documentation and have moved between releases,
 * and an option this release doesn't have should cost the option, not the
 * NPU.
 */
internal class Npu private constructor(private val qnn: QnnDelegate) {

    val delegate: Delegate get() = qnn

    fun close() = runCatching { qnn.close() }.let { }

    companion object {
        /** A delegate, or null where this phone has no NPU the delegate can use. */
        fun create(skelDirectory: String, log: (String) -> Unit): Npu? {
            val options = QnnDelegate.Options()
            options.setBackendType(QnnDelegate.Options.BackendType.HTP_BACKEND)
            options.setSkelLibraryDir(skelDirectory)
            setEnum(options, "Precision", listOf("FP16"), log)
            setEnum(options, "PerformanceMode", listOf("BURST", "SUSTAINED_HIGH", "HIGH_PERFORMANCE"), log)
            return try {
                Npu(QnnDelegate(options))
            } catch (e: UnsupportedOperationException) {
                log("  depth: no NPU here: ${e.message}")
                null
            }
        }

        /**
         * Calls the one-argument setter on [options] whose name ends in
         * [suffix] and takes an enum, with the first of its constants that
         * contains one of [wanted], in order.
         */
        private fun setEnum(options: Any, suffix: String, wanted: List<String>, log: (String) -> Unit) {
            val setter = options.javaClass.methods.firstOrNull {
                it.name.startsWith("set") && it.name.endsWith(suffix) &&
                    it.parameterTypes.size == 1 && it.parameterTypes[0].isEnum
            } ?: run { log("  depth: the NPU delegate has no ${suffix} option"); return }
            val constants = setter.parameterTypes[0].enumConstants ?: return
            val choice = wanted.firstNotNullOfOrNull { want -> constants.firstOrNull { it.toString().contains(want) } }
                ?: run { log("  depth: no ${wanted.first()} among ${constants.joinToString()}"); return }
            runCatching { setter.invoke(options, choice) }
                .onSuccess { log("  depth: NPU ${setter.name}($choice)") }
                .onFailure { log("  depth: NPU ${setter.name} failed: $it") }
        }
    }
}
