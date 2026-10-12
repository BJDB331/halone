package dev.halone.compositor

import java.util.concurrent.CopyOnWriteArrayList

internal actual fun <T> concurrentPanelList(): MutableList<T> = CopyOnWriteArrayList()

internal actual fun <T> MutableList<T>.replaceEach(transform: (T) -> T) = replaceAll(transform)
