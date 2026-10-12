package dev.halone.app.pointer

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import dev.halone.compositor.CursorImage
import dev.halone.compositor.CursorShape
import org.json.JSONObject

/** The arrow's pictures, from `assets/cursors` (see `tools/icons/fetch-cursors.sh`). */
object CursorImages {
    private const val DIR = "cursors"

    fun load(assets: AssetManager): Map<CursorShape, CursorImage> {
        val hotspots = runCatching { JSONObject(assets.open("$DIR/hotspots.json").bufferedReader().use { it.readText() }) }.getOrNull()
            ?: return emptyMap()
        return CursorShape.entries.mapNotNull { shape ->
            val hotspot = hotspots.optJSONArray(shape.css) ?: return@mapNotNull null
            val bitmap = runCatching { assets.open("$DIR/${shape.css}.png").use(BitmapFactory::decodeStream) }.getOrNull() ?: return@mapNotNull null
            shape to CursorImage(bitmap, hotspot.getDouble(0).toFloat(), hotspot.getDouble(1).toFloat())
        }.toMap()
    }
}
