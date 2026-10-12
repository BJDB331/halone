package dev.halone.app.display

import android.app.Presentation
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.Display
import android.view.View
import android.view.WindowManager

/**
 * Renders a per-eye test pattern so it is visually obvious whether the glasses
 * are splitting the frame. In SBS mode the left eye sees a red "L" card and the
 * right eye a green "R" card; in mirror mode both eyes see the whole thing,
 * which reads as one wide image containing both cards.
 */
class StereoTestPresentation(
    context: Context,
    display: Display,
    private val requestedModeId: Int?,
    private val onReport: (String) -> Unit,
) : Presentation(context, display) {

    private lateinit var view: TestPatternView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedModeId?.let { modeId ->
            window?.let { w ->
                val attrs = w.attributes
                attrs.preferredDisplayModeId = modeId
                w.attributes = attrs
                w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        view = TestPatternView(context)
        setContentView(view)
    }

    override fun onStart() {
        super.onStart()
        view.post {
            val mode = display.mode
            onReport(
                "presentation surface=${view.width}x${view.height} " +
                    "display mode now id=${mode.modeId} ${mode.physicalWidth}x${mode.physicalHeight} " +
                    "@${"%.2f".format(mode.refreshRate)} (requested id=$requestedModeId)"
            )
        }
    }

    private class TestPatternView(context: Context) : View(context) {
        private val fill = Paint()
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = Color.WHITE
        }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val half = w / 2f

            fill.color = Color.rgb(150, 20, 20)
            canvas.drawRect(0f, 0f, half, h, fill)
            fill.color = Color.rgb(20, 130, 40)
            canvas.drawRect(half, 0f, w, h, fill)

            text.textSize = h * 0.35f
            canvas.drawText("L", half * 0.5f, h * 0.62f, text)
            canvas.drawText("R", half + half * 0.5f, h * 0.62f, text)

            text.textSize = h * 0.05f
            canvas.drawText("${width}x$height", half * 0.5f, h * 0.85f, text)
            canvas.drawText("${width}x$height", half + half * 0.5f, h * 0.85f, text)

            canvas.drawLine(half, 0f, half, h, stroke)
            canvas.drawRect(8f, 8f, half - 8f, h - 8f, stroke)
            canvas.drawRect(half + 8f, 8f, w - 8f, h - 8f, stroke)

            val step = h / 12f
            var y = step
            while (y < h) {
                canvas.drawLine(half * 0.1f, y, half * 0.2f, y, stroke)
                canvas.drawLine(half + half * 0.8f, y, half + half * 0.9f, y, stroke)
                y += step
            }
        }
    }
}
