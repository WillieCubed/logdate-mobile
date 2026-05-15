package app.logdate.ui.platform

/**
 * The single source of truth for what each [HapticEvent] feels like. Tune feel here — every
 * call site goes through [LogDateHaptics] and ends up dispatching through this table.
 *
 * Per-platform overrides (e.g. richer Android composed primitives, sequenced iOS generators)
 * happen inside the `tick()` override on each platform's [PlatformHapticsController] — this
 * table just declares which primitive is invoked.
 */
internal fun PlatformHapticsController.fire(event: HapticEvent) {
    when (event) {
        HapticEvent.SaveSucceeded -> notification(HapticNotificationType.Success)
        HapticEvent.RecordingStarted -> impact(HapticImpactStrength.Medium)
        HapticEvent.RecordingFinished -> notification(HapticNotificationType.Success)
        HapticEvent.TranscriptionReady -> tick()
        HapticEvent.ConfirmDestruction -> impact(HapticImpactStrength.Heavy)
    }
}
