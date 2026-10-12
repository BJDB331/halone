package dev.halone.glasses.math

data class Vec3(val x: Float, val y: Float, val z: Float) {
    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
    }

    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)

    val length: Float get() = kotlin.math.sqrt(x * x + y * y + z * z)

    infix fun cross(o: Vec3) = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x,
    )

    override fun toString() = listOf(x, y, z).joinToString(" ") { formatFixed(it.toDouble(), 4, " ") }
}
