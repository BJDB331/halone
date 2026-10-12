package dev.halone.compositor

internal actual fun <T> concurrentPanelList(): MutableList<T> = ArrayList()

internal actual fun <T> MutableList<T>.replaceEach(transform: (T) -> T) {
    for (i in indices) this[i] = transform(this[i])
}
