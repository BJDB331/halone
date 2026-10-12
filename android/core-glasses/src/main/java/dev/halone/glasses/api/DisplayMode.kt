package dev.halone.glasses.api

/** How a single video frame is divided between the two eyes. */
enum class StereoLayout {
    /** One image shown to both eyes. No stereo depth. */
    MONO,

    /** Full side-by-side: the frame is twice as wide, each half a full-resolution eye. */
    FULL_SBS,

    /** Half side-by-side: normal frame width, each eye horizontally squashed. */
    HALF_SBS,

    /** Over-under / top-bottom split. */
    OVER_UNDER,
}

/**
 * A display mode the glasses can be put into.
 *
 * [key] is opaque to everything above the driver — the compositor asks for a
 * [StereoLayout] and a refresh rate and lets the driver map that onto whatever
 * byte its firmware wants. This keeps vendor mode numbering out of the
 * rendering code.
 */
data class DisplayMode(
    val key: String,
    val layout: StereoLayout,
    /** Resolution of the whole video frame, not of one eye. */
    val frameWidth: Int,
    val frameHeight: Int,
    val refreshHz: Float,
    /**
     * True when this mode has been confirmed working on real hardware.
     *
     * Vendor documentation and reverse-engineering notes routinely list modes a
     * given firmware rejects, so mode selection prefers verified modes: better
     * a mode we know works than the theoretically best one. Contributors flip
     * this as they test, which also makes the table double as a coverage map.
     */
    val verified: Boolean = false,
) {
    val perEyeWidth: Int
        get() = if (layout == StereoLayout.FULL_SBS) frameWidth / 2 else frameWidth

    val perEyeHeight: Int
        get() = if (layout == StereoLayout.OVER_UNDER) frameHeight / 2 else frameHeight

    val isStereo: Boolean get() = layout != StereoLayout.MONO

    override fun toString() =
        "$key(${layout}, ${frameWidth}x$frameHeight @${"%.0f".format(refreshHz)})"
}
