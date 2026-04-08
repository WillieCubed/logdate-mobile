package app.logdate.client.calendar

import kotlin.time.Instant

/**
 * Desktop / JVM no-op [DeviceCalendarReader]. There is no concept of an OS calendar on
 * the desktop target, so we return empty results. The settings screen detects this via
 * [hasPermission] returning `false` and renders the appropriate empty state.
 */
class StubDeviceCalendarReader : DeviceCalendarReader {
    override suspend fun hasPermission(): Boolean = false

    override suspend fun listCalendars(): List<DeviceCalendar> = emptyList()

    override suspend fun readEvents(
        calendarIds: Set<String>,
        start: Instant,
        end: Instant,
    ): List<DeviceCalendarEvent> = emptyList()
}
