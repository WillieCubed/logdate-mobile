package app.logdate.client.calendar

import app.logdate.client.domain.events.CalendarImportLauncher

/**
 * Android implementation of [CalendarImportLauncher] that delegates to
 * [CalendarImportScheduler]. Bound in the Android Koin module so the calendar sync
 * settings ViewModel can fire "Sync now" runs without depending on WorkManager directly.
 */
class AndroidCalendarImportLauncher(
    private val scheduler: CalendarImportScheduler,
) : CalendarImportLauncher {
    override fun runNow() {
        scheduler.enqueueImmediateRun()
    }
}
