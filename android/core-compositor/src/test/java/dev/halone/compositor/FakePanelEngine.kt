package dev.halone.compositor

import android.graphics.SurfaceTexture

/**
 * A panel with no pixels behind it.
 *
 * The scene's arithmetic — hit testing, following, layout — is what decides
 * whether the environment feels solid, and none of it needs a real engine. The
 * two thread-bound halves of the handshake are the only part that does, and they
 * throw here rather than pretending.
 */
class FakePanelEngine(
    override val widthPx: Int = 1280,
    override val heightPx: Int = 800,
) : PanelEngine {

    val taps = mutableListOf<Pair<Float, Float>>()
    var closed = false
        private set

    override fun createTexture(textureId: Int): SurfaceTexture =
        error("needs a GL context")

    override fun attach() = error("needs the UI thread")

    override fun dispatchTap(x: Float, y: Float) {
        taps += x to y
    }

    override fun dispatchScroll(fromX: Float, fromY: Float, toX: Float, toY: Float, steps: Int) = Unit

    override fun close() {
        closed = true
    }
}

fun panel(
    id: String,
    yawDegrees: Float = 0f,
    pitchDegrees: Float = 0f,
    distanceMetres: Float = 4f,
    widthMetres: Float = 1.28f,
    heightMetres: Float = 0.80f,
    follow: SmoothFollow = SmoothFollow(),
    engine: PanelEngine = FakePanelEngine(),
): Panel = Panel(
    id = id,
    engine = engine,
    pose = PanelPose(
        yawRadians = Math.toRadians(yawDegrees.toDouble()).toFloat(),
        pitchRadians = Math.toRadians(pitchDegrees.toDouble()).toFloat(),
        distanceMetres = distanceMetres,
        widthMetres = widthMetres,
        heightMetres = heightMetres,
    ),
    follow = follow,
)
