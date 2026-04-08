package app.logdate.client.domain.events

/**
 * Lets non-Android UI layers ask the platform to run a device calendar import pass right
 * now. The actual scheduler lives in the Android app module because it talks to
 * WorkManager; this interface keeps the calendar sync settings ViewModel free of any
 * direct Android dependency.
 *
 * Mirrors [EventInferenceLauncher] for the auto-events worker. Non-Android platforms get
 * the [NoopCalendarImportLauncher] fallback so calendar sync UI compiles cleanly even
 * where the import isn't supported.
 */
interface CalendarImportLauncher {
    /** Schedules a one-shot import run. Idempotent if a run is already in flight. */
    fun runNow()
}

/** Platform-agnostic no-op fallback used on iOS / desktop where calendar sync is unsupported. */
object NoopCalendarImportLauncher : CalendarImportLauncher {
    override fun runNow() = Unit
}
