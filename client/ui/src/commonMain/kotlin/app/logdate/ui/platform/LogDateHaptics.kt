package app.logdate.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The semantic haptic facade used by feature code. Each method names a moment in the product
 * (e.g. [saveSucceeded], [confirmDestruction]) — the underlying primitive lives in
 * [HapticEventToTokens]. Tune feel in one place, not scattered across dozens of call sites.
 *
 * This surface is intentionally narrow: a haptic is only justified when the user just initiated
 * a real state change AND the change isn't already obvious from the screen. Anything else is
 * noise — see `docs/superpowers/specs/...`.
 *
 * Acquire via [LocalLogDateHaptics] inside a Composable. Outside Composables, capture the
 * instance once and use it from your handler.
 */
interface LogDateHaptics {
    fun saveSucceeded()

    fun recordingStarted()

    fun recordingFinished()

    fun transcriptionReady()

    fun confirmDestruction()
}

/**
 * Default mapping from semantic events to platform primitives. The [reduceMotion] callback
 * is consulted on every fire; when it returns `true`, only events in
 * [HapticEvent.criticalEvents] survive — see the design rationale in
 * `docs/superpowers/specs/...` (recording lifecycle and destructive confirmations are the
 * only delight-irrelevant events left after the haptics audit).
 */
class DefaultLogDateHaptics(
    private val controller: PlatformHapticsController,
    private val reduceMotion: () -> Boolean = { false },
) : LogDateHaptics {
    private fun fire(event: HapticEvent) {
        if (reduceMotion() && event !in HapticEvent.criticalEvents) return
        controller.fire(event)
    }

    override fun saveSucceeded() = fire(HapticEvent.SaveSucceeded)

    override fun recordingStarted() = fire(HapticEvent.RecordingStarted)

    override fun recordingFinished() = fire(HapticEvent.RecordingFinished)

    override fun transcriptionReady() = fire(HapticEvent.TranscriptionReady)

    override fun confirmDestruction() = fire(HapticEvent.ConfirmDestruction)
}

/** Composition-local handle to the semantic haptics facade. */
val LocalLogDateHaptics =
    staticCompositionLocalOf<LogDateHaptics> {
        DefaultLogDateHaptics(NoOpPlatformHaptics)
    }

@Composable
fun rememberLogDateHaptics(): LogDateHaptics = LocalLogDateHaptics.current
