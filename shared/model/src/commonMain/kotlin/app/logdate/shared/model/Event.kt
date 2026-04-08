package app.logdate.shared.model

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A time-bound event that media and notes can attach to.
 *
 * Events represent things that happen — a piano recital, a birthday party, a trip. They are
 * a foundational primitive referenced across the app: the timeline surfaces them as cards,
 * recommendations use them to nudge captures, and they are the future home of shared albums.
 *
 * Events are not user-created. They are auto-generated from on-device signals or grounded in
 * an external calendar (Google Calendar, Apple Calendar). Users can edit metadata such as the
 * title, description, cover image, and time bounds.
 */
data class Event(
    val id: Uuid = Uuid.random(),
    val title: String,
    val description: String? = null,
    val startTime: Instant,
    /**
     * The end of the event, or `null` for point-in-time or open-ended events.
     */
    val endTime: Instant? = null,
    val placeId: Uuid? = null,
    val coverImageUri: String? = null,
    /**
     * Identifier of the source calendar event this event is grounded in, if any.
     */
    val externalCalendarId: String? = null,
    val externalCalendarSource: ExternalCalendarSource? = null,
    val created: Instant = Clock.System.now(),
    val lastUpdated: Instant = Clock.System.now(),
)

/**
 * The external calendar service that an [Event] is grounded in.
 *
 * [DEVICE_CALENDAR] is the catch-all for anything LogDate reads from the OS calendar
 * provider — Google, iCloud, Outlook, local — without distinguishing the underlying
 * account type. The OS already aggregates them and the per-account label travels with
 * the event in [Event.externalCalendarId] (`accountName:externalId`), so a separate enum
 * variant per account would be more granular than the import flow needs.
 */
enum class ExternalCalendarSource {
    GOOGLE_CALENDAR,
    APPLE_CALENDAR,
    DEVICE_CALENDAR,
}

/**
 * Human-readable label for an [ExternalCalendarSource], suitable for showing in chips,
 * badges, or attribution lines next to a grounded event.
 */
fun ExternalCalendarSource.displayLabel(): String =
    when (this) {
        ExternalCalendarSource.GOOGLE_CALENDAR -> "Google Calendar"
        ExternalCalendarSource.APPLE_CALENDAR -> "Apple Calendar"
        ExternalCalendarSource.DEVICE_CALENDAR -> "Calendar"
    }
