package app.logdate.feature.editor.ui.audio.haptics

/**
 * Interface for providing haptic feedback during audio scrubbing.
 *
 * Implementations should use platform-specific vibration APIs to
 * create tactile feedback that enhances the scrubbing experience.
 */
interface HapticController {
    /**
     * Called when the user starts dragging the waveform.
     * Should provide a subtle initial feedback.
     */
    fun onDragStart()

    /**
     * Called periodically during dragging.
     * Should provide very subtle continuous feedback.
     */
    fun onDragging()

    /**
     * Called when the user drags across a segment boundary.
     * Should provide noticeable tick feedback.
     */
    fun onCrossSegment()

    /**
     * Called when the user's drag position snaps to a segment.
     * Should provide stronger click feedback.
     */
    fun onSnapToSegment()

    /**
     * Called when the user stops dragging.
     * Should provide subtle release feedback.
     */
    fun onDragEnd()
}

/**
 * No-op implementation for platforms without haptic support.
 */
object NoOpHapticController : HapticController {
    override fun onDragStart() {}

    override fun onDragging() {}

    override fun onCrossSegment() {}

    override fun onSnapToSegment() {}

    override fun onDragEnd() {}
}
