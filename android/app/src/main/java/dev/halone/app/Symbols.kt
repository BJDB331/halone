package dev.halone.app

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat

/** Material Symbols Rounded, the same cut-down font the system apps use; a symbol is its name, drawn as a ligature. */
object Symbols {
    private var typeface: Typeface? = null

    fun typeface(context: Context): Typeface =
        typeface ?: ResourcesCompat.getFont(context, R.font.symbols)!!.also { typeface = it }

    fun view(context: Context, name: String, sizeSp: Float, colour: Int): TextView = TextView(context).apply {
        typeface = typeface(context)
        text = name
        textSize = sizeSp
        setTextColor(colour)
        gravity = Gravity.CENTER
        includeFontPadding = false
    }
}
