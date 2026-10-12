package dev.halone.compositor

/** Iterable from the render thread while another thread adds and removes. */
internal expect fun <T> concurrentPanelList(): MutableList<T>

/** Replaces every element in one step, so a concurrent reader never sees a half-done swap. */
internal expect fun <T> MutableList<T>.replaceEach(transform: (T) -> T)
