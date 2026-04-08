package app.logdate.client.calendar

import kotlin.time.Instant

/**
 * Reads calendar metadata and events the device already knows about, regardless of which
 * account (Google, iCloud, local) supplied them.
 *
 * The interface is intentionally **read-only**. LogDate never writes back to the system
 * calendar — the import worker mirrors selected calendars into LogDate's own event store
 * so the rest of the app can treat them like any other event, but the source of truth for
 * the user's actual calendar stays with whichever provider owns it. This separation also
 * keeps the runtime permission surface to a single `READ_CALENDAR` grant on Android and
 * `EKEntityType.event` on iOS.
 *
 * Implementations are platform-specific. Non-supported platforms (desktop, currently iOS)
 * return empty results from every method so common-side code can call into the reader
 * without branching.
 */
interface DeviceCalendarReader {
    /**
     * Whether the host platform has granted the read-only calendar permission required for
     * [listCalendars] and [readEvents] to return real data. Stub implementations always
     * return `true` because they never read anything sensitive in the first place.
     */
    suspend fun hasPermission(): Boolean

    /**
     * Lists every calendar the OS exposes to the app, grouped by account in the
     * [DeviceCalendar.accountName] field. The settings flow uses this to let the user
     * pick which calendars to mirror.
     */
    suspend fun listCalendars(): List<DeviceCalendar>

    /**
     * Returns events from [calendarIds] whose start time falls inside `[start, end]`.
     * Pass an empty [calendarIds] set to read from no calendars (used to short-circuit
     * when the user hasn't picked any).
     */
    suspend fun readEvents(
        calendarIds: Set<String>,
        start: Instant,
        end: Instant,
    ): List<DeviceCalendarEvent>
}

/**
 * One row from `CalendarContract.Calendars` (Android) or `EKCalendar` (iOS), normalized
 * to a flat shape that LogDate's settings UI can render without platform-specific code.
 */
data class DeviceCalendar(
    /** Provider-stable identifier the [DeviceCalendarReader] uses for follow-up reads. */
    val id: String,
    /** Human-readable label, usually the account email or calendar name. */
    val displayName: String,
    /** Account label used to group calendars in the UI ("Google", "iCloud", "Local"). */
    val accountName: String,
    /** Raw account type the OS reports — useful for further grouping or attribution. */
    val accountType: String,
    /** ARGB swatch color, when the provider supplies one. */
    val color: Long? = null,
    /** True for the user's primary/default calendar on the account. */
    val isPrimary: Boolean = false,
)

/**
 * One event mirrored from a [DeviceCalendar]. The [externalId] is the provider's stable
 * identifier and is preserved by the import worker so a re-sync can find existing rows
 * instead of creating duplicates.
 */
data class DeviceCalendarEvent(
    val externalId: String,
    val calendarId: String,
    val accountName: String,
    val title: String,
    val description: String? = null,
    val startTime: Instant,
    val endTime: Instant? = null,
    val placeName: String? = null,
)
