package app.logdate.ui.platform

/**
 * The single source of truth for what each [HapticEvent] feels like. Tune feel here — every
 * call site goes through [LogDateHaptics] and ends up dispatching through this table.
 *
 * Per-platform overrides (e.g. richer Android composed primitives, sequenced iOS generators)
 * happen inside the `pattern()` and `tick()` overrides on each platform's
 * [PlatformHapticsController] — this table just declares which primitive is invoked.
 */
internal fun PlatformHapticsController.fire(event: HapticEvent) {
    when (event) {
        // Generic
        HapticEvent.Selection -> selection()
        HapticEvent.ToggleOn -> impact(HapticImpactStrength.Light)
        HapticEvent.ToggleOff -> tick()

        // Authoring
        HapticEvent.SaveSucceeded -> notification(HapticNotificationType.Success)
        HapticEvent.AutoSaved -> Unit // intentionally invisible — auto-save must not feel
        HapticEvent.BlockAdded -> impact(HapticImpactStrength.Light)
        HapticEvent.BlockReordered -> impact(HapticImpactStrength.Soft)
        HapticEvent.RecordingStarted -> impact(HapticImpactStrength.Medium)
        HapticEvent.RecordingFinished -> notification(HapticNotificationType.Success)
        HapticEvent.RecordingCancelled -> notification(HapticNotificationType.Warning)
        HapticEvent.TranscriptionReady -> tick()
        HapticEvent.AudioScrubCrossSegment -> tick()
        HapticEvent.AudioSnapToSegment -> impact(HapticImpactStrength.Soft)

        // Navigation / temporal
        HapticEvent.DayExpanded -> impact(HapticImpactStrength.Light)
        HapticEvent.JumpedToToday -> impact(HapticImpactStrength.Medium)
        HapticEvent.RewindCardCentered -> tick()
        HapticEvent.RewindEndReached ->
            pattern(
                HapticPattern.of(
                    HapticStep.Tick,
                    HapticStep.Wait(millis = 60),
                    HapticStep.Tick,
                    HapticStep.Wait(millis = 60),
                    HapticStep.Notification(HapticNotificationType.Success),
                ),
            )
        HapticEvent.PageTurned -> tick()

        // Canvas (postcards)
        HapticEvent.ToolSelected -> selection()
        HapticEvent.StrokeStarted -> tick()
        HapticEvent.StrokeCompleted -> tick()
        HapticEvent.Undo -> impact(HapticImpactStrength.Light)
        HapticEvent.Redo -> impact(HapticImpactStrength.Light)

        // Status / result
        HapticEvent.Warning -> notification(HapticNotificationType.Warning)
        HapticEvent.ConfirmDestruction -> impact(HapticImpactStrength.Heavy)
        HapticEvent.Error -> notification(HapticNotificationType.Error)

        // Sync (deliberately rare)
        HapticEvent.SyncCompletedWithChanges -> tick()
        HapticEvent.SyncConflictSurfaced -> notification(HapticNotificationType.Warning)
    }
}
