package dev.halone.compositor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLUtils
import android.text.TextPaint
import android.text.TextUtils

/**
 * Paints each window's title bar into a texture, the simulator's design drawn
 * with `Canvas`, and repaints only when what it shows changes. Render thread
 * only.
 */
internal class TitleBarPainter {

    private data class Look(
        val title: String,
        val tag: String?,
        val pinned: Boolean,
        val back: Boolean,
        val shape: Boolean,
        val depth: Boolean,
        val depthOn: Boolean,
        val hovered: HitPart?,
        val widthPx: Int,
    )

    private class Painted(val texture: Int, val look: Look)

    private val painted = HashMap<String, Painted>()

    /** The texture for [panel]'s bar as it should look now, painting it if needed. */
    fun texture(panel: Panel, hovered: HitPart?, shape: Boolean, depth: Boolean = false): Int {
        val look = Look(
            title = panel.title,
            tag = panel.tag,
            pinned = !panel.follow.enabled,
            back = panel.backButton,
            shape = shape,
            depth = depth,
            depthOn = panel.depth3D,
            hovered = hovered?.takeIf {
                it == HitPart.PIN || it == HitPart.CLOSE || it == HitPart.BACK || it == HitPart.SIZE || it == HitPart.DEPTH
            },
            widthPx = panel.widthPx,
        )
        painted[panel.id]?.let { if (it.look == look) return it.texture }
        val bar = TitleBar.bar(panel.widthPx, panel.heightPx)
        val bitmap = paint(look, bar, panel)
        val texture = painted[panel.id]?.texture ?: newTexture()
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        bitmap.recycle()
        painted[panel.id] = Painted(texture, look)
        return texture
    }

    /** Textures no longer needed, for the caller to delete on the render thread. */
    fun forget(liveIds: Set<String>): List<Int> {
        val gone = painted.keys.filter { it !in liveIds }
        return gone.map { painted.remove(it)!!.texture }
    }

    private fun newTexture(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return ids[0]
    }

    private fun paint(look: Look, bar: PanelRect, panel: Panel): Bitmap {
        val bitmap = Bitmap.createBitmap((bar.width * SCALE).toInt(), (bar.height * SCALE).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(SCALE, SCALE)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        fill.color = BACKGROUND
        canvas.drawRoundRect(RectF(0f, 0f, bar.width, bar.height), bar.height / 2f, bar.height / 2f, fill)

        val midY = bar.height / 2f
        fill.color = MUTED
        val gripX = TitleBar.PADDING_START_PX
        for (column in 0..1) for (row in -1..1) {
            canvas.drawCircle(gripX + column * 7f, midY + row * 7f, 2f, fill)
        }

        val pin = TitleBar.pin(panel.widthPx, panel.heightPx).relativeTo(bar)
        val close = TitleBar.close(panel.widthPx, panel.heightPx).relativeTo(bar)
        val size = TitleBar.size(panel.widthPx, panel.heightPx).relativeTo(bar)
        val depth = TitleBar.depth(panel.widthPx, panel.heightPx, look.shape).relativeTo(bar)
        var textEnd = (if (look.depth) depth.left else if (look.shape) size.left else pin.left) - GAP_PX

        look.tag?.let { tag ->
            val tagPaint = textPaint(15f, MUTED)
            val width = tagPaint.measureText(tag) + 20f
            val rect = RectF(textEnd - width, midY - 13f, textEnd, midY + 13f)
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = BORDER
            }
            canvas.drawRoundRect(rect, 10f, 10f, stroke)
            canvas.drawText(tag, rect.left + 10f, baseline(tagPaint, midY), tagPaint)
            textEnd = rect.left - GAP_PX
        }

        val titlePaint = textPaint(20f, TEXT)
        val back = TitleBar.back(panel.widthPx, panel.heightPx).relativeTo(bar)
        val titleStart = if (look.back) back.right + GAP_PX / 2f else gripX + 7f + 2f + GAP_PX
        val title = TextUtils.ellipsize(look.title, titlePaint, (textEnd - titleStart).coerceAtLeast(0f), TextUtils.TruncateAt.END)
        canvas.drawText(title, 0, title.length, titleStart, baseline(titlePaint, midY), titlePaint)

        if (look.back) {
            button(canvas, back, active = false, hovered = look.hovered == HitPart.BACK)
            drawBack(canvas, back, iconColor(look.hovered == HitPart.BACK))
        }
        if (look.shape) {
            button(canvas, size, active = false, hovered = look.hovered == HitPart.SIZE)
            drawSize(canvas, size, iconColor(look.hovered == HitPart.SIZE), landscape = panel.widthPx < panel.heightPx)
        }
        if (look.depth) {
            button(canvas, depth, active = look.depthOn, hovered = look.hovered == HitPart.DEPTH)
            drawDepth(canvas, depth, iconColor(look.depthOn || look.hovered == HitPart.DEPTH))
        }
        button(canvas, pin, active = look.pinned, hovered = look.hovered == HitPart.PIN)
        drawPin(canvas, pin, filled = look.pinned, color = iconColor(look.pinned || look.hovered == HitPart.PIN))
        button(canvas, close, active = false, hovered = look.hovered == HitPart.CLOSE)
        drawClose(canvas, close, iconColor(look.hovered == HitPart.CLOSE))
        return bitmap
    }

    private fun button(canvas: Canvas, rect: PanelRect, active: Boolean, hovered: Boolean) {
        if (!active && !hovered) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HOVER }
        canvas.drawCircle(rect.left + rect.width / 2f, rect.top + rect.height / 2f, rect.width / 2f, paint)
    }

    private fun drawPin(canvas: Canvas, rect: PanelRect, filled: Boolean, color: Int) {
        val cx = rect.left + rect.width / 2f
        val cy = rect.top + rect.height / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = 2f
            strokeCap = Paint.Cap.ROUND
            style = if (filled) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        }
        canvas.drawCircle(cx, cy - 4f, 5f, paint)
        paint.style = Paint.Style.STROKE
        canvas.drawLine(cx, cy + 1f, cx, cy + 10f, paint)
    }

    private fun drawBack(canvas: Canvas, rect: PanelRect, color: Int) {
        val cx = rect.left + rect.width / 2f
        val cy = rect.top + rect.height / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = 2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.STROKE
        }
        val path = Path().apply {
            moveTo(cx + 7f, cy); lineTo(cx - 7f, cy)
            moveTo(cx - 1f, cy - 6f); lineTo(cx - 7f, cy); lineTo(cx - 1f, cy + 6f)
        }
        canvas.drawPath(path, paint)
    }

    /** A rounded rectangle in the shape the next press will give the window. */
    private fun drawSize(canvas: Canvas, rect: PanelRect, color: Int, landscape: Boolean) {
        val cx = rect.left + rect.width / 2f
        val cy = rect.top + rect.height / 2f
        val (hw, hh) = if (landscape) 9f to 5.5f else 5.5f to 9f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        canvas.drawRoundRect(RectF(cx - hw, cy - hh, cx + hw, cy + hh), 2.5f, 2.5f, paint)
    }

    /** "3D", bold while conversion is on. */
    private fun drawDepth(canvas: Canvas, rect: PanelRect, color: Int) {
        val paint = textPaint(15f, color).apply {
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("3D", rect.left + rect.width / 2f, baseline(paint, rect.top + rect.height / 2f), paint)
    }

    private fun drawClose(canvas: Canvas, rect: PanelRect, color: Int) {
        val cx = rect.left + rect.width / 2f
        val cy = rect.top + rect.height / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = 2f
            strokeCap = Paint.Cap.ROUND
        }
        val path = Path().apply {
            moveTo(cx - 6f, cy - 6f); lineTo(cx + 6f, cy + 6f)
            moveTo(cx + 6f, cy - 6f); lineTo(cx - 6f, cy + 6f)
        }
        paint.style = Paint.Style.STROKE
        canvas.drawPath(path, paint)
    }

    private fun iconColor(strong: Boolean) = if (strong) TEXT else Color.argb(179, 232, 238, 247)

    private fun textPaint(sizePx: Float, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sizePx
        this.color = color
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private fun baseline(paint: Paint, midY: Float) = midY - (paint.descent() + paint.ascent()) / 2f

    private fun PanelRect.relativeTo(bar: PanelRect) = PanelRect(left - bar.left, top - bar.top, width, height)

    private companion object {
        /** Painted at twice the panel's pixels and mipmapped, so text stays crisp at any distance. */
        const val SCALE = 2f
        const val GAP_PX = 14f
        val BACKGROUND = Color.argb(235, 22, 28, 38)
        val TEXT = Color.rgb(232, 238, 247)
        val MUTED = Color.rgb(139, 152, 171)
        val BORDER = Color.rgb(42, 52, 68)
        val HOVER = Color.rgb(29, 37, 51)
    }
}
