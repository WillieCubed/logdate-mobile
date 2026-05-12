package app.logdate.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The semantic haptic facade used by feature code. Each method names a moment in the product
 * (e.g. [saveSucceeded], [rewindEndReached]) — the underlying primitive lives in
 * [HapticEventToTokens]. Tune feel in one place, not scattered across dozens of call sites.
 *
 * Acquire via [LocalLogDateHaptics] inside a Composable. Outside Composables, capture the
 * instance once and use it from your handler.
 */
interface LogDateHaptics {
    // Generic
    fun selection()

    fun toggle(on: Boolean)

    // Authoring
    fun saveSucceeded()

    fun autoSaved()

    fun blockAdded()

    fun blockReordered()

    fun recordingStarted()

    fun recordingFinished()

    fun recordingCancelled()

    fun transcriptionReady()

    fun audioScrubCrossSegment()

    fun audioSnapToSegment()

    // Navigation / temporal
    fun dayExpanded()

    fun jumpedToToday()

    fun rewindCardCentered()

    fun rewindEndReached()

    fun pageTurned()

    // Canvas (postcards)
    fun toolSelected()

    fun strokeStarted()

    fun strokeCompleted()

    fun undo()

    fun redo()

    // Status / result
    fun warning()

    fun confirmDestruction()

    fun error()

    // Sync (deliberately rare)
    fun syncCompletedWithChanges()

    fun syncConflictSurfaced()
}

/**
 * Default mapping from semantic events to platform primitives. The [reduceMotion] callback
 * is consulted on every fire; when it returns `true`, only events in
 * [HapticEvent.criticalEvents] survive — see the design rationale in
 * `docs/superpowers/specs/...` (recording lifecycle, destructive confirmations, errors,
 * sync conflicts are the only delight-irrelevant events).
 */
class DefaultLogDateHaptics(
    private val controller: PlatformHapticsController,
    private val reduceMotion: () -> Boolean = { false },
) : LogDateHaptics {
    private fun fire(event: HapticEvent) {
        if (reduceMotion() && event !in HapticEvent.criticalEvents) return
        controller.fire(event)
    }

    override fun selection() = fire(HapticEvent.Selection)

    override fun toggle(on: Boolean) = fire(if (on) HapticEvent.ToggleOn else HapticEvent.ToggleOff)

    override fun saveSucceeded() = fire(HapticEvent.SaveSucceeded)

    override fun autoSaved() = fire(HapticEvent.AutoSaved)

    override fun blockAdded() = fire(HapticEvent.BlockAdded)

    override fun blockReordered() = fire(HapticEvent.BlockReordered)

    override fun recordingStarted() = fire(HapticEvent.RecordingStarted)

    override fun recordingFinished() = fire(HapticEvent.RecordingFinished)

    override fun recordingCancelled() = fire(HapticEvent.RecordingCancelled)

    override fun transcriptionReady() = fire(HapticEvent.TranscriptionReady)

    override fun audioScrubCrossSegment() = fire(HapticEvent.AudioScrubCrossSegment)

    override fun audioSnapToSegment() = fire(HapticEvent.AudioSnapToSegment)

    override fun dayExpanded() = fire(HapticEvent.DayExpanded)

    override fun jumpedToToday() = fire(HapticEvent.JumpedToToday)

    override fun rewindCardCentered() = fire(HapticEvent.RewindCardCentered)

    override fun rewindEndReached() = fire(HapticEvent.RewindEndReached)

    override fun pageTurned() = fire(HapticEvent.PageTurned)

    override fun toolSelected() = fire(HapticEvent.ToolSelected)

    override fun strokeStarted() = fire(HapticEvent.StrokeStarted)

    override fun strokeCompleted() = fire(HapticEvent.StrokeCompleted)

    override fun undo() = fire(HapticEvent.Undo)

    override fun redo() = fire(HapticEvent.Redo)

    override fun warning() = fire(HapticEvent.Warning)

    override fun confirmDestruction() = fire(HapticEvent.ConfirmDestruction)

    override fun error() = fire(HapticEvent.Error)

    override fun syncCompletedWithChanges() = fire(HapticEvent.SyncCompletedWithChanges)

    override fun syncConflictSurfaced() = fire(HapticEvent.SyncConflictSurfaced)
}

/** Composition-local handle to the semantic haptics facade. */
val LocalLogDateHaptics =
    staticCompositionLocalOf<LogDateHaptics> {
        DefaultLogDateHaptics(NoOpPlatformHaptics)
    }

@Composable
fun rememberLogDateHaptics(): LogDateHaptics = LocalLogDateHaptics.current
