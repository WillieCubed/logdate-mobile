package app.logdate.ui.platform

/**
 * Every named haptic event the LogDate app can fire. The mapping from event → primitive
 * lives in [HapticEventToTokens] (the single tuning file). Feature code never references
 * primitives directly — it calls [LogDateHaptics] methods, which use this enum internally.
 *
 * Membership in [criticalEvents] determines whether the event survives system reduce-motion
 * or "haptics off" preferences. Anything safety-relevant (recording lifecycle, destructive
 * confirmations, errors, sync conflicts) is critical; everything else is suppressible.
 */
enum class HapticEvent {
    // Generic
    Selection,
    ToggleOn,
    ToggleOff,

    // Authoring
    SaveSucceeded,
    AutoSaved,
    BlockAdded,
    BlockReordered,
    RecordingStarted,
    RecordingFinished,
    RecordingCancelled,
    TranscriptionReady,
    AudioScrubCrossSegment,
    AudioSnapToSegment,

    // Navigation / temporal
    DayExpanded,
    JumpedToToday,
    RewindCardCentered,
    RewindEndReached,
    PageTurned,

    // Canvas (postcards)
    ToolSelected,
    StrokeStarted,
    StrokeCompleted,
    Undo,
    Redo,

    // Status / result
    Warning,
    ConfirmDestruction,
    Error,

    // Sync (deliberately rare)
    SyncCompletedWithChanges,
    SyncConflictSurfaced,
    ;

    companion object {
        /**
         * Events that always fire even when the OS reports reduce-motion or haptics-off. These
         * are safety signals (recording lifecycle, destructive confirmations, errors, sync
         * conflicts) — never delight. Suppressing them would let users miss something important.
         */
        val criticalEvents: Set<HapticEvent> =
            setOf(
                RecordingStarted,
                RecordingFinished,
                RecordingCancelled,
                Warning,
                ConfirmDestruction,
                Error,
                SyncConflictSurfaced,
            )
    }
}
