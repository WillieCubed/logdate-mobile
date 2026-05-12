package app.logdate.ui.platform

/**
 * A short composed haptic sequence. Used for moments that warrant more than a single
 * primitive — e.g. the Rewind end-of-list celebration or a multi-stage confirmation.
 *
 * Patterns are intentionally limited to a handful of steps; longer sequences feel like
 * vibration, not feedback. Construct via [HapticPattern.of].
 */
class HapticPattern internal constructor(
    /**
     * Ordered haptic steps that make up the pattern.
     */
    val steps: List<HapticStep>,
) {
    companion object {
        fun of(vararg steps: HapticStep): HapticPattern = HapticPattern(steps.toList())
    }
}

/** Atomic step inside a [HapticPattern]. */
sealed interface HapticStep {
    /** A short, low-amplitude tick — the lightest feedback we expose. */
    data object Tick : HapticStep

    /** A selection feedback ([PlatformHapticsController.selection]). */
    data object Selection : HapticStep

    /** An impact at the given [strength]. */
    data class Impact(
        /**
         * Relative intensity of the impact feedback.
         */
        val strength: HapticImpactStrength,
    ) : HapticStep

    /** A success/warning/error notification. */
    data class Notification(
        /**
         * Semantic notification style to play.
         */
        val type: HapticNotificationType,
    ) : HapticStep

    /** A pause between steps. */
    data class Wait(
        /**
         * Pause duration in milliseconds. Must be non-negative.
         */
        val millis: Long,
    ) : HapticStep {
        init {
            require(millis >= 0) { "Haptic wait duration must be non-negative." }
        }
    }
}
