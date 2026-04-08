package app.logdate.client.calendar

import io.github.aakira.napier.Napier
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.EventKit.EKAuthorizationStatus
import platform.EventKit.EKAuthorizationStatusAuthorized
import platform.EventKit.EKCalendar
import platform.EventKit.EKEntityType
import platform.EventKit.EKEvent
import platform.EventKit.EKEventStore
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import kotlin.coroutines.resume
import kotlin.time.Instant

/**
 * iOS [DeviceCalendarReader] backed by `EKEventStore`.
 *
 * Talks to the user's actual calendars (Google, iCloud, local) through Apple's EventKit
 * framework. Read-only — LogDate never writes back, the import worker mirrors a snapshot
 * into the local event store and the user's source calendars stay untouched.
 *
 * The store is held as a single instance for the lifetime of the reader. EventKit is
 * thread-safe for reads from background queues, so the suspend functions are free to
 * run on any dispatcher their caller picks.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosDeviceCalendarReader : DeviceCalendarReader {
    private val eventStore: EKEventStore = EKEventStore()

    override suspend fun hasPermission(): Boolean {
        val status = EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeEvent)
        if (status == EKAuthorizationStatusAuthorized) return true
        // The runtime permission flow lives at the UI layer; here we only return whether
        // the OS has already granted us access. The settings screen drives the prompt.
        return status.requestIfNeeded()
    }

    override suspend fun listCalendars(): List<DeviceCalendar> {
        if (!hasPermission()) return emptyList()
        @Suppress("UNCHECKED_CAST")
        val ekCalendars = eventStore.calendarsForEntityType(EKEntityType.EKEntityTypeEvent) as List<EKCalendar>
        return ekCalendars.map { calendar ->
            val source = calendar.source
            DeviceCalendar(
                id = calendar.calendarIdentifier,
                displayName = calendar.title,
                accountName = source?.title ?: "Local",
                accountType = source?.sourceIdentifier ?: "local",
                color = null,
                isPrimary = false,
            )
        }
    }

    override suspend fun readEvents(
        calendarIds: Set<String>,
        start: Instant,
        end: Instant,
    ): List<DeviceCalendarEvent> {
        if (calendarIds.isEmpty() || !hasPermission()) return emptyList()
        @Suppress("UNCHECKED_CAST")
        val allCalendars = eventStore.calendarsForEntityType(EKEntityType.EKEntityTypeEvent) as List<EKCalendar>
        val selected = allCalendars.filter { it.calendarIdentifier in calendarIds }
        if (selected.isEmpty()) return emptyList()

        val startDate = NSDate.dateWithTimeIntervalSince1970(start.toEpochMilliseconds().toDouble() / 1000.0)
        val endDate = NSDate.dateWithTimeIntervalSince1970(end.toEpochMilliseconds().toDouble() / 1000.0)
        val predicate =
            eventStore.predicateForEventsWithStartDate(
                startDate = startDate,
                endDate = endDate,
                calendars = selected,
            )

        @Suppress("UNCHECKED_CAST")
        val ekEvents = eventStore.eventsMatchingPredicate(predicate) as List<EKEvent>
        return ekEvents.mapNotNull { event ->
            val externalId = event.eventIdentifier ?: return@mapNotNull null
            val calendar = event.calendar ?: return@mapNotNull null
            val startDateValue = event.startDate ?: return@mapNotNull null
            DeviceCalendarEvent(
                externalId = externalId,
                calendarId = calendar.calendarIdentifier,
                accountName = calendar.source?.title ?: "Local",
                title = event.title ?: "(untitled)",
                description = event.notes,
                startTime = startDateValue.toInstant(),
                endTime = event.endDate?.toInstant(),
                placeName = event.location,
            )
        }
    }

    /**
     * Bridges EventKit's callback-based access request into a suspend boolean. Returns
     * `true` only when the user grants access on this call; for already-granted or
     * already-denied states the caller should consult [hasPermission] first.
     */
    private suspend fun EKAuthorizationStatus.requestIfNeeded(): Boolean =
        suspendCancellableCoroutine { continuation ->
            eventStore.requestAccessToEntityType(EKEntityType.EKEntityTypeEvent) { granted, error ->
                if (error != null) {
                    Napier.w("Failed to request EventKit access: ${error.localizedDescription}")
                }
                continuation.resume(granted)
            }
        }

    private fun NSDate.toInstant(): Instant = Instant.fromEpochMilliseconds((timeIntervalSince1970 * 1000.0).toLong())
}
