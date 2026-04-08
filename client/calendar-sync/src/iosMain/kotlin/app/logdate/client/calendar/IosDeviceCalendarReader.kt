package app.logdate.client.calendar

import kotlin.time.Instant

/**
 * Stub iOS [DeviceCalendarReader].
 *
 * The full EventKit integration (`EKEventStore`, calendar enumeration, event predicate
 * queries) is intentionally not wired yet — calendar import currently ships only on
 * Android. This implementation returns no calendars and no events so iOS-side code can
 * still call into the reader without conditional branches, and the settings screen
 * gracefully shows an empty list rather than crashing.
 */
class IosDeviceCalendarReader : DeviceCalendarReader {
    override suspend fun hasPermission(): Boolean = false

    override suspend fun listCalendars(): List<DeviceCalendar> = emptyList()

    override suspend fun readEvents(
        calendarIds: Set<String>,
        start: Instant,
        end: Instant,
    ): List<DeviceCalendarEvent> = emptyList()
}
