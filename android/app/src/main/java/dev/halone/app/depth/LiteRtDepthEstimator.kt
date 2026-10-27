package dev.halone.app.depth

import android.content.Context
import dev.halone.compositor.DepthEstimator
import dev.halone.compositor.DepthMap
import dev.halone.compositor.DepthPost
import dev.halone.compositor.DepthStabiliser
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Depth Anything V2 Small (Qualcomm AI Hub's TFLite export, MIT licensed) on
 * LiteRT: one 518x518 RGB picture in, 0..1 floats, NHWC; relative inverse
 * depth out, bigger nearer — as measured on the file itself.
 *
 * The model is ~99 MB, so it is not in the APK: it downloads the first time a
 * window is converted, pinned to one revision and checked against its hash.
 * Until it is ready, converted windows keep the framing guess.
 *
 * It runs on the Snapdragon's NPU where there is one (see [Npu]), so the
 * model no longer competes with drawing for the GPU; the GPU, then the CPU,
 * where there is not.
 *
 * Everything runs on one background thread, which the GPU delegate requires
 * of the interpreter it serves.
 */
class LiteRtDepthEstimator(
    context: Context,
    private val log: (String) -> Unit,
    /** Short messages for the wearer: downloading, ready, failed. */
    private val notice: (String) -> Unit,
) : DepthEstimator {

    override val inputSize = SIZE

    // As fast as it keeps up on the NPU, which draws nothing, so each held
    // picture of a video can have depth of its own; four a second on the GPU, where
    // each estimate holds up drawing.
    override val intervalNs: Long
        get() = if (backend == Backend.NPU) 33_000_000L else 250_000_000L

    private val dir = File(context.filesDir, "depth")
    /** Where Qualcomm's NPU libraries are unpacked; the NPU loads its half from this path. */
    private val nativeLibraryDir = context.applicationInfo.nativeLibraryDir
    /** Where the last picture and its depth are saved now and then, for `adb pull`. */
    private val debugDir = context.getExternalFilesDir("depth-debug")
    private var lastDebugAt = 0L
    private var estimates = 0L
    private val modelFile = File(dir, MODEL_NAME)
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "depth").apply { priority = Thread.NORM_PRIORITY - 1 } }
    private val busy = AtomicBoolean(false)
    private val starting = AtomicBoolean(false)

    @Volatile
    private var interpreter: Interpreter? = null
    private var gpu: GpuDelegate? = null
    private val input = ByteBuffer.allocateDirect(SIZE * SIZE * 3 * 4).order(ByteOrder.nativeOrder())
    private val output = ByteBuffer.allocateDirect(SIZE * SIZE * 4).order(ByteOrder.nativeOrder())
    private val raw = FloatArray(SIZE * SIZE)
    private val pixels = ByteArray(SIZE * SIZE * 4)
    private val luma = FloatArray(SIZE * SIZE)
    private val stabilisers = LinkedHashMap<String, DepthStabiliser>()

    /** How much of the last depth is kept where a window's picture is unchanged, 0..0.9. */
    @Volatile
    private var smoothingAmount = DepthStabiliser.DEFAULT_SMOOTHING

    override fun setSmoothing(amount: Float) {
        smoothingAmount = amount
    }

    @Volatile
    var lastMs = 0L
        private set

    /** One line on how conversion is going, for the stats line and the log. */
    @Volatile
    var summary: String? = null
        private set

    override fun ready(): Boolean {
        if (interpreter == null) {
            start()
            return false
        }
        return !busy.get()
    }

    override fun estimate(panelId: String, rgba: ByteBuffer, done: (DepthMap) -> Unit) {
        if (!busy.compareAndSet(false, true)) return
        worker.execute {
            try {
                val model = interpreter ?: return@execute
                rgba.position(0)
                rgba.get(pixels)
                rgba.position(0)
                input.position(0)
                var brightness = 0L
                for (i in 0 until SIZE * SIZE) {
                    val r = pixels[i * 4].toInt() and 0xFF
                    val g = pixels[i * 4 + 1].toInt() and 0xFF
                    val b = pixels[i * 4 + 2].toInt() and 0xFF
                    brightness += r + g + b
                    luma[i] = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
                    input.putFloat(r / 255f)
                    input.putFloat(g / 255f)
                    input.putFloat(b / 255f)
                }
                input.position(0)
                output.position(0)
                val started = System.nanoTime()
                model.run(input, output)
                lastMs = (System.nanoTime() - started) / 1_000_000
                if (backend == Backend.NPU && npuTrial.exists()) {
                    npuTrial.delete()
                    log("  depth: first estimate on the NPU took $lastMs ms")
                }
                output.position(0)
                output.asFloatBuffer().get(raw)
                if (raw.any { !it.isFinite() }) {
                    // Half precision on the GPU can overflow inside the
                    // model's attention and come back as NaN — a map of
                    // nothing, which looked like conversion switching off.
                    // Full precision next, then the CPU.
                    log("  depth: the model returned non-finite values on the ${backend.label}")
                    fallBack()
                    return@execute
                }
                if (backend == Backend.NPU && (raw.maxOrNull() ?: 0f) - (raw.minOrNull() ?: 0f) < 1e-6f) {
                    // A delegate that runs but computes nothing useful.
                    log("  depth: the NPU returned a flat map")
                    fallBack()
                    return@execute
                }
                val stabiliser = stabilisers.getOrPut(panelId) { DepthStabiliser() }
                stabiliser.smoothing = smoothingAmount
                val map = stabiliser.next(raw, SIZE, luma, SIZE)
                // Most recently used last; forget the oldest beyond a few windows.
                stabilisers.remove(panelId)
                stabilisers[panelId] = stabiliser
                if (stabilisers.size > MAX_REMEMBERED) stabilisers.remove(stabilisers.keys.first())
                var low = Float.MAX_VALUE
                var high = -Float.MAX_VALUE
                for (v in raw) { if (v < low) low = v; if (v > high) high = v }
                summary = "3D ${lastMs} ms ${backend.label}, picture %d%%, depth %.2f..%.2f, relief %.2f, used %d%%${if (stabiliser.lastCut) ", cut" else ""}".format(
                    (brightness * 100 / (SIZE * SIZE * 3L * 255L)).toInt(), low, high, stabiliser.lastRelief, (stabiliser.lastGain * 100).toInt(),
                )
                if (estimates++ % LOG_EVERY == 0L) log("  depth: $summary")
                val now = System.currentTimeMillis()
                if (debugDir != null && now - lastDebugAt > DEBUG_EVERY_MS) {
                    lastDebugAt = now
                    saveDebug(rgba, map.texels)
                }
                done(map)
            } catch (e: Throwable) {
                log("  depth: estimate failed: $e")
            } finally {
                busy.set(false)
            }
        }
    }

    /** The picture as the model saw it and the depth it gave, as PNGs; overwritten each time. */
    private fun saveDebug(rgba: ByteBuffer, map: ByteArray) {
        runCatching {
            val picture = android.graphics.Bitmap.createBitmap(SIZE, SIZE, android.graphics.Bitmap.Config.ARGB_8888)
            rgba.position(0)
            picture.copyPixelsFromBuffer(rgba)
            File(debugDir, "picture.png").outputStream().use { picture.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            picture.recycle()
            val size = DepthPost.OUTPUT_SIZE
            val pixels = IntArray(map.size) { val v = map[it].toInt() and 0xFF; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
            val depth = android.graphics.Bitmap.createBitmap(pixels, size, size, android.graphics.Bitmap.Config.ARGB_8888)
            File(debugDir, "depth.png").outputStream().use { depth.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            depth.recycle()
        }.onFailure { log("  depth: could not save debug images: $it") }
    }

    /** Downloads the model if it is missing, then loads it; once. */
    private fun start() {
        if (!starting.compareAndSet(false, true)) return
        worker.execute {
            try {
                if (!modelFile.exists()) download()
                load()
            } catch (e: Throwable) {
                log("  depth: unavailable: $e")
                notice("3D conversion model couldn't load: ${e.message ?: e.javaClass.simpleName}")
                // Let a later conversion try again.
                starting.set(false)
            }
        }
    }

    private fun download() {
        notice("Downloading the 3D conversion model (99 MB)…")
        dir.mkdirs()
        val part = File(dir, "$MODEL_NAME.part")
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        try {
            if (connection.responseCode != 200) error("download answered ${connection.responseCode}")
            connection.inputStream.use { stream ->
                part.outputStream().use { out ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        val n = stream.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                        out.write(buffer, 0, n)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        if (hash != MODEL_SHA256) {
            part.delete()
            error("downloaded model failed its check")
        }
        if (!part.renameTo(modelFile)) error("could not save the model")
        log("  depth: model downloaded")
    }

    /**
     * Where the model runs, best first. Each is tried in turn when the one
     * before it can't load or gives back nonsense.
     */
    private enum class Backend(val label: String) {
        /** The Snapdragon's AI engine (Hexagon NPU), through Qualcomm's QNN delegate, in half precision. */
        NPU("NPU"),
        /** The GPU, allowed half precision. */
        GPU("GPU"),
        /** The GPU in full precision: half can overflow inside the model's attention. */
        GPU_FULL("GPU (full precision)"),
        CPU("CPU"),
    }

    private var tier = 0
    @Volatile
    private var backend = Backend.CPU
    private var npu: Npu? = null

    /** A marker that the NPU was being tried: still there at the next start means it crashed the app. */
    private val npuTrial = File(dir, "npu-trial")

    private fun fallBack() {
        release()
        tier++
        runCatching { load() }.onFailure { log("  depth: reload failed: $it") }
    }

    private fun release() {
        interpreter?.close()
        interpreter = null
        gpu?.close()
        gpu = null
        npu?.close()
        npu = null
    }

    private fun load() {
        val backends = Backend.values()
        while (tier < backends.size) {
            val candidate = backends[tier]
            val model = runCatching { open(candidate) }
                .onFailure { log("  depth: ${candidate.label} unavailable: $it") }
                .getOrNull()
            if (model != null) {
                interpreter = model
                backend = candidate
                log("  depth: model loaded on the ${candidate.label}")
                notice("3D conversion ready (${candidate.label})")
                return
            }
            tier++
        }
        error("no backend could load the model")
    }

    private fun open(backend: Backend): Interpreter? = when (backend) {
        Backend.NPU -> {
            if (npuTrial.exists()) {
                // The last attempt never finished: a vendor driver that takes
                // the process down would otherwise do it at every start.
                log("  depth: skipping the NPU, which did not survive its last try")
                null
            } else {
                dir.mkdirs()
                npuTrial.writeText("")
                val delegate = Npu.create(nativeLibraryDir, log)
                if (delegate == null) {
                    npuTrial.delete()
                    null
                } else {
                    runCatching { Interpreter(modelFile, Interpreter.Options().addDelegate(delegate.delegate)) }
                        .onSuccess { npu = delegate }
                        .onFailure { delegate.close(); npuTrial.delete() }
                        .getOrThrow()
                    // The marker comes off after the first inference survives.
                }
            }
        }
        Backend.GPU, Backend.GPU_FULL -> {
            val compatibility = CompatibilityList()
            if (!compatibility.isDelegateSupportedOnThisDevice) null
            else {
                val options = compatibility.bestOptionsForThisDevice.apply { if (backend == Backend.GPU_FULL) setPrecisionLossAllowed(false) }
                val delegate = GpuDelegate(options)
                runCatching { Interpreter(modelFile, Interpreter.Options().addDelegate(delegate)) }
                    .onSuccess { gpu = delegate }
                    .onFailure { delegate.close() }
                    .getOrThrow()
            }
        }
        Backend.CPU -> Interpreter(modelFile, Interpreter.Options().setNumThreads(CPU_THREADS))
    }

    fun close() {
        worker.execute { release() }
        worker.shutdown()
    }

    private companion object {
        const val SIZE = 518
        const val CPU_THREADS = 4
        const val MAX_REMEMBERED = 8
        const val LOG_EVERY = 50L
        const val DEBUG_EVERY_MS = 5_000L
        const val MODEL_NAME = "depth_anything_v2_small.tflite"
        const val MODEL_URL =
            "https://huggingface.co/qualcomm/Depth-Anything-V2/resolve/3388c56142d6ea92507a2456155fada5a019edc7/Depth-Anything-V2.tflite"
        const val MODEL_SHA256 = "e2f3ce97fd589e29323f8b82d932c7e7da8ec437883fc0b34d08b3efc78abcd9"
    }
}
