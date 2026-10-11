package app.logdate.wear.v2.playback

/**
 * Turns crown rotation into whole seek steps. A step is [pixelsPerStep] of rotation and moves the
 * position by [stepMs]; a part turn counts toward the next step, so slow turns still add up.
 */
class CrownScrubber(
    private val pixelsPerStep: Float = DEFAULT_PIXELS_PER_STEP,
    val stepMs: Long = DEFAULT_STEP_MS,
) {
    private var pending = 0f

    /** The whole steps [pixels] completes: positive forward, negative back. */
    fun onRotate(pixels: Float): Int {
        pending += pixels
        val steps = (pending / pixelsPerStep).toInt()
        pending -= steps * pixelsPerStep
        return steps
    }

    fun reset() {
        pending = 0f
    }

    companion object {
        const val DEFAULT_PIXELS_PER_STEP = 60f
        const val DEFAULT_STEP_MS = 5_000L
    }
}
