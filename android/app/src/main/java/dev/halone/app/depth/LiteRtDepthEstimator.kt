package dev.halone.app.depth

import android.content.Context
import dev.halone.compositor.DepthEstimator
import dev.halone.compositor.DepthMap
import dev.halone.compositor.DepthPost
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

    private val dir = File(context.filesDir, "depth")
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
    private val previous = HashMap<String, ByteArray>()

    @Volatile
    var lastMs = 0L
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
                input.position(0)
                for (i in 0 until SIZE * SIZE) {
                    input.putFloat((rgba.get().toInt() and 0xFF) / 255f)
                    input.putFloat((rgba.get().toInt() and 0xFF) / 255f)
                    input.putFloat((rgba.get().toInt() and 0xFF) / 255f)
                    rgba.get()
                }
                input.position(0)
                output.position(0)
                val started = System.nanoTime()
                model.run(input, output)
                lastMs = (System.nanoTime() - started) / 1_000_000
                output.position(0)
                output.asFloatBuffer().get(raw)
                val map = DepthPost.normalise(raw, SIZE, previous[panelId])
                previous[panelId] = map
                if (previous.size > MAX_REMEMBERED) previous.keys.firstOrNull { it != panelId }?.let(previous::remove)
                done(DepthMap(DepthPost.OUTPUT_SIZE, map))
            } catch (e: Throwable) {
                log("  depth: estimate failed: $e")
            } finally {
                busy.set(false)
            }
        }
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

    private fun load() {
        val compatibility = CompatibilityList()
        val loaded = if (compatibility.isDelegateSupportedOnThisDevice) {
            runCatching {
                val delegate = GpuDelegate(compatibility.bestOptionsForThisDevice)
                Interpreter(modelFile, Interpreter.Options().addDelegate(delegate)).also { gpu = delegate }
            }.onFailure { log("  depth: GPU unavailable ($it), using the CPU") }.getOrNull()
        } else null
        val model = loaded ?: Interpreter(modelFile, Interpreter.Options().setNumThreads(CPU_THREADS))
        interpreter = model
        log("  depth: model loaded on the ${if (gpu != null) "GPU" else "CPU"}")
        notice("3D conversion ready")
    }

    fun close() {
        worker.execute {
            interpreter?.close()
            interpreter = null
            gpu?.close()
            gpu = null
        }
        worker.shutdown()
    }

    private companion object {
        const val SIZE = 518
        const val CPU_THREADS = 4
        const val MAX_REMEMBERED = 8
        const val MODEL_NAME = "depth_anything_v2_small.tflite"
        const val MODEL_URL =
            "https://huggingface.co/qualcomm/Depth-Anything-V2/resolve/3388c56142d6ea92507a2456155fada5a019edc7/Depth-Anything-V2.tflite"
        const val MODEL_SHA256 = "e2f3ce97fd589e29323f8b82d932c7e7da8ec437883fc0b34d08b3efc78abcd9"
    }
}
