package app.logdate.client.domain.events

import app.logdate.client.calendar.DeviceCalendarEvent
import app.logdate.client.calendar.DeviceCalendarReader
import app.logdate.client.repository.events.EventRepository
import app.logdate.shared.model.Event
import app.logdate.shared.model.ExternalCalendarSource
import io.github.aakira.napier.Napier
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Mirrors the user's selected device calendars into LogDate's own event store.
 *
 * The flow is intentionally **read-only**: LogDate never writes back to the OS calendar
 * provider. Each run pulls a window of upcoming and recently-past events from the
 * [DeviceCalendarReader], then for every event:
 *
 * - looks up an existing LogDate event by its provider-stable [Event.externalCalendarId],
 * - inserts a new event when there's no match,
 * - updates the existing event in place when title/time/place have drifted,
 * - skips when the existing event is byte-for-byte identical.
 *
 * The result is an [ImportSummary] that the worker stores so the auto-events settings
 * screen can show "X created, Y updated" without having to spelunk through the timeline.
 */
class ImportDeviceCalendarEventsUseCase(
    private val deviceCalendarReader: DeviceCalendarReader,
    private val eventRepository: EventRepository,
    private val now: () -> Instant = { Clock.System.now() },
) {
    /**
     * @param selectedCalendarIds Which device calendars to mirror. Empty → no-op (the user
     *   hasn't picked any calendars yet, no point in querying).
     * @param lookback How far into the past to import. Defaults to 30 days so the timeline
     *   gets recent calendar events as historical context, not just upcoming ones.
     * @param lookahead How far into the future to import. Defaults to 60 days so reminders
     *   for upcoming events show up in plenty of time.
     */
    suspend operator fun invoke(
        selectedCalendarIds: Set<String>,
        lookback: kotlin.time.Duration = LOOKBACK,
        lookahead: kotlin.time.Duration = LOOKAHEAD,
    ): ImportResult {
        if (!deviceCalendarReader.hasPermission()) {
            return ImportResult.PermissionDenied
        }
        if (selectedCalendarIds.isEmpty()) {
            return ImportResult.Success(ImportSummary())
        }
        return runCatching {
            val nowInstant = now()
            val deviceEvents =
                deviceCalendarReader.readEvents(
                    calendarIds = selectedCalendarIds,
                    start = nowInstant - lookback,
                    end = nowInstant + lookahead,
                )
            var created = 0
            var updated = 0
            var skipped = 0
            for (deviceEvent in deviceEvents) {
                val externalId = deviceEvent.toExternalId()
                val existing = eventRepository.findByExternalCalendarId(externalId)
                if (existing == null) {
                    val event = deviceEvent.toNewEvent(externalId)
                    val createResult = eventRepository.createEvent(event)
                    if (createResult.isSuccess) {
                        created += 1
                    } else {
                        Napier.w(
                            tag = TAG,
                            message = "Failed to import calendar event $externalId",
                            throwable = createResult.exceptionOrNull(),
                        )
                    }
                } else if (existing.differsFrom(deviceEvent)) {
                    val updateResult =
                        eventRepository.updateEvent(existing.applyDeviceEvent(deviceEvent))
                    if (updateResult.isSuccess) {
                        updated += 1
                    } else {
                        Napier.w(
                            tag = TAG,
                            message = "Failed to update imported event $externalId",
                            throwable = updateResult.exceptionOrNull(),
                        )
                    }
                } else {
                    skipped += 1
                }
            }
            ImportResult.Success(ImportSummary(created = created, updated = updated, skipped = skipped))
        }.getOrElse { error ->
            Napier.e(tag = TAG, message = "Calendar import pass failed", throwable = error)
            ImportResult.Failure
        }
    }

    /**
     * Stable external id used to dedupe across runs. Combines the account name with the
     * provider's event id so two accounts on the same device can't collide on the same
     * underlying event id space.
     */
    private fun DeviceCalendarEvent.toExternalId(): String = "$accountName:$externalId"

    private fun DeviceCalendarEvent.toNewEvent(externalId: String): Event =
        Event(
            title = title,
            description = description,
            startTime = startTime,
            endTime = endTime,
            externalCalendarId = externalId,
            externalCalendarSource = ExternalCalendarSource.DEVICE_CALENDAR,
        )

    private fun Event.applyDeviceEvent(deviceEvent: DeviceCalendarEvent): Event =
        copy(
            title = deviceEvent.title,
            description = deviceEvent.description ?: description,
            startTime = deviceEvent.startTime,
            endTime = deviceEvent.endTime,
        )

    /**
     * True when any of the fields the import worker touches have drifted between the LogDate
     * event and the live device event. The user-edited fields ([Event.coverImageUri],
     * [Event.placeId]) are intentionally excluded so re-syncs don't clobber a user choice.
     */
    private fun Event.differsFrom(deviceEvent: DeviceCalendarEvent): Boolean =
        title != deviceEvent.title ||
            startTime != deviceEvent.startTime ||
            endTime != deviceEvent.endTime ||
            (deviceEvent.description != null && description != deviceEvent.description)

    companion object {
        private const val TAG = "ImportDeviceCalendarEventsUseCase"
        private val LOOKBACK = 30.days
        private val LOOKAHEAD = 60.days
    }
}

/**
 * Outcome of one [ImportDeviceCalendarEventsUseCase] pass. The sealed type lets the worker
 * distinguish "we didn't have permission" (don't retry, prompt the user) from "the import
 * itself blew up" (do retry).
 */
sealed interface ImportResult {
    data class Success(
        val summary: ImportSummary,
    ) : ImportResult

    data object PermissionDenied : ImportResult

    data object Failure : ImportResult
}

/** Aggregate counts for one import pass, surfaced verbatim in the settings status card. */
data class ImportSummary(
    val created: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
)
