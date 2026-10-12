package dev.halone.glasses.math

import kotlin.math.abs
import kotlin.math.roundToLong

private const val DEGREES_TO_RADIANS = 0.017453292519943295
private const val RADIANS_TO_DEGREES = 57.29577951308232

fun toRadians(degrees: Double): Double = degrees * DEGREES_TO_RADIANS

fun toDegrees(radians: Double): Double = radians * RADIANS_TO_DEGREES

fun formatFixed(value: Double, decimals: Int, positivePrefix: String = ""): String {
    var scale = 1L
    repeat(decimals) { scale *= 10 }
    val scaled = (abs(value) * scale).roundToLong()
    val whole = scaled / scale
    val fraction = (scaled % scale).toString().padStart(decimals, '0')
    val sign = if (value < 0 && scaled != 0L) "-" else positivePrefix
    return if (decimals == 0) "$sign$whole" else "$sign$whole.$fraction"
}
