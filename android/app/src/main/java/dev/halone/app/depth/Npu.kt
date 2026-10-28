package dev.halone.app.depth

import com.qualcomm.qti.QnnDelegate
import org.tensorflow.lite.Delegate
import java.lang.reflect.Method

/**
 * Qualcomm's QNN delegate on the HTP backend: LiteRT models on the Hexagon
 * NPU of Snapdragon 8 Gen 1 and later, a chip of its own beside the GPU, so
 * estimating depth stops costing the compositor frames.
 *
 * The model is a float one, and the HTP's default is quantized networks only:
 * without half precision asked for, the delegate takes only the few parts it
 * can and the rest runs on the CPU, several times slower than the GPU.
 * The option setters are found by name — they are not in Google's
 * documentation and have moved between releases — and only the HTP's own:
 * the options also carry GPU and DSP setters with the same endings and
 * constants, and setting one of those instead was the mistake once.
 */
internal class Npu private constructor(private val qnn: QnnDelegate) {

    val delegate: Delegate get() = qnn

    fun close() = runCatching { qnn.close() }.let { }

    companion object {
        /** A delegate, or null where this phone has no NPU the delegate can use. */
        fun create(skelDirectory: String, cacheDirectory: String, log: (String) -> Unit): Npu? {
            val options = QnnDelegate.Options()
            options.setBackendType(QnnDelegate.Options.BackendType.HTP_BACKEND)
            options.setSkelLibraryDir(skelDirectory)
            val precision = setHtpEnum(options, "Precision", listOf("FP16"), log)
            setHtpEnum(options, "PerformanceMode", listOf("BURST", "SUSTAINED_HIGH", "HIGH_PERFORMANCE"), log)
            setHtpEnum(options, "UseConvHmx", listOf("ON"), log)
            // The graph the NPU builds from the model, kept for the next start.
            setString(options, "setCacheDir", cacheDirectory, log)
            setString(options, "setModelToken", "depth-anything-v2-small-fp16", log)
            if (!precision) {
                log("  depth: the NPU can't be asked for half precision; leaving it for the GPU")
                return null
            }
            return try {
                Npu(QnnDelegate(options))
            } catch (e: UnsupportedOperationException) {
                log("  depth: no NPU here: ${e.message}")
                null
            }
        }

        /** Every option setter, for the log, once. */
        private fun setters(options: Any): List<Method> =
            options.javaClass.methods.filter { it.name.startsWith("set") && it.parameterTypes.size == 1 }

        /**
         * Calls the HTP's own one-argument setter whose name ends in
         * [suffix] and takes an enum, with the first of its constants that
         * contains one of [wanted], in order; true if it did.
         */
        private fun setHtpEnum(options: Any, suffix: String, wanted: List<String>, log: (String) -> Unit): Boolean {
            val candidates = setters(options).filter { it.name.endsWith(suffix) && it.parameterTypes[0].isEnum }
            val setter = candidates.firstOrNull { it.name.startsWith("setHtp") }
                ?: run {
                    log("  depth: no HTP ${suffix} option among ${candidates.joinToString { it.name }.ifEmpty { "none" }}")
                    return false
                }
            val constants = setter.parameterTypes[0].enumConstants ?: return false
            val choice = wanted.firstNotNullOfOrNull { want -> constants.firstOrNull { it.toString().contains(want) } }
                ?: run { log("  depth: no ${wanted.first()} among ${constants.joinToString()}"); return false }
            return runCatching { setter.invoke(options, choice) }
                .onSuccess { log("  depth: NPU ${setter.name}($choice)") }
                .onFailure { log("  depth: NPU ${setter.name} failed: $it") }
                .isSuccess
        }

        private fun setString(options: Any, name: String, value: String, log: (String) -> Unit) {
            val setter = setters(options).firstOrNull { it.name == name && it.parameterTypes[0] == String::class.java } ?: return
            runCatching { setter.invoke(options, value) }.onFailure { log("  depth: NPU $name failed: $it") }
        }
    }
}
