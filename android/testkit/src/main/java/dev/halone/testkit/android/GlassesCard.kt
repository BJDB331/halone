package dev.halone.testkit.android

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import dev.halone.display.DisplayProbe
import dev.halone.testkit.R
import dev.halone.testkit.ab.HeadView
import dev.halone.testkit.ab.SceneParams
import dev.halone.testkit.ab.SceneRenderer

/**
 * Mirrors the current instruction onto the glasses, large, so the tester can
 * read it while wearing them. Follows the display through mode switches, which
 * remove it and add it back with a new id.
 */
class GlassesCard(private val context: Context) : DisplayManager.DisplayListener {

    private val displays = context.getSystemService(DisplayManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var presentation: CardPresentation? = null
    private var text: String = ""
    private var scene: SceneRenderer? = null
    private var sceneView: HeadView? = null

    fun start() {
        displays.registerDisplayListener(this, main)
        attach()
    }

    fun stop() {
        displays.unregisterDisplayListener(this)
        runCatching { presentation?.dismiss() }
        presentation = null
    }

    fun show(text: String) {
        this.text = text
        presentation?.update(text)
    }

    fun showScene(view: HeadView?, params: SceneParams?) {
        if (view == null) {
            scene = null
            sceneView = null
            presentation?.showText()
            return
        }
        if (view !== sceneView) {
            sceneView = view
            scene = SceneRenderer(view).also { presentation?.showScene(it) }
        }
        scene?.params = params
    }

    override fun onDisplayAdded(displayId: Int) = attach()

    override fun onDisplayRemoved(displayId: Int) {
        if (presentation?.display?.displayId == displayId) {
            runCatching { presentation?.dismiss() }
            presentation = null
        }
    }

    override fun onDisplayChanged(displayId: Int) {
        if (presentation?.display?.displayId == displayId) {
            runCatching { presentation?.dismiss() }
            presentation = null
        }
        attach()
    }

    private fun attach() {
        if (presentation != null) return
        val display = DisplayProbe.externalDisplay(context) ?: return
        presentation = runCatching {
            CardPresentation(context, display).also { card ->
                card.show()
                card.update(text)
                scene?.let { card.showScene(it) }
            }
        }.getOrNull()
    }

    private class CardPresentation(context: Context, display: Display) : Presentation(context, display) {
        private lateinit var body: TextView
        private lateinit var card: LinearLayout

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.BLACK)
                setPadding(160, 80, 160, 80)
            }
            body = TextView(context).apply {
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
                gravity = Gravity.CENTER
            }
            val hint = TextView(context).apply {
                setTextColor(Color.GRAY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                gravity = Gravity.CENTER
                text = context.getString(R.string.card_hint)
                setPadding(0, 48, 0, 0)
            }
            layout.addView(body)
            layout.addView(hint)
            card = layout
            setContentView(layout)
        }

        fun showText() {
            if (::card.isInitialized) setContentView(card)
        }

        fun showScene(renderer: SceneRenderer) {
            setContentView(GLSurfaceView(context).apply {
                setEGLContextClientVersion(2)
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            })
        }

        fun update(text: String) {
            if (::body.isInitialized) body.text = text
        }
    }
}
