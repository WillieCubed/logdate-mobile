package app.logdate.client.domain.events

import app.logdate.client.domain.location.LocationStop
import app.logdate.client.domain.location.LocationStopEvidenceKind
import app.logdate.client.domain.places.PlaceResolutionResult
import app.logdate.client.domain.places.toDisplayName
import app.logdate.client.domain.places.toPlaceKey
import app.logdate.client.domain.places.toUserPlaceId
import app.logdate.client.domain.recommendation.PlaceFamiliarityRepository
import app.logdate.client.intelligence.AIResult
import app.logdate.client.intelligence.events.EventCluster
import app.logdate.client.intelligence.events.EventName
import app.logdate.client.repository.events.EventRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.media.IndexedMediaRepository
import app.logdate.shared.model.Event
import app.logdate.shared.model.Location
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Walks the user's recent location stops, photos, and notes and turns ambiguous "you spent
 * time somewhere and captured something" patterns into concrete [Event]s the timeline can
 * surface.
 *
 * The pipeline is intentionally heuristic: it is run by a background worker on the user's
 * device, so the cost of a missed cluster is small (it'll be picked up on the next sweep)
 * and the cost of a false positive is significant (the user sees an event in their timeline
 * that doesn't represent anything they remember). The thresholds favor restraint.
 *
 * Naming is the only AI-touched step. When the cache and the model are unavailable — or
 * when [aiNamingEnabled] is `false` — every cluster falls back to a deterministic
 * "<time of day> at <place>" label so the worker still produces useful results offline.
 */
class InferEventsUseCase(
    private val observeLocationStops: () -> Flow<List<LocationStop>>,
    private val indexedMediaRepository: IndexedMediaRepository,
    private val notesRepository: JournalNotesRepository,
    private val placeFamiliarity: PlaceFamiliarityRepository,
    private val resolvePlaceForLocation: suspend (Location) -> PlaceResolutionResult,
    private val eventRepository: EventRepository,
    private val suggestEventName: suspend (EventCluster) -> AIResult<EventName>,
    private val now: () -> Instant = { Clock.System.now() },
) {
    /**
     * Runs one inference pass.
     *
     * @param window How far back to scan for stops, photos, and notes. Anything older is
     *   assumed to already have been processed by an earlier run.
     * @param sensitivity Controls the minimum signal count a cluster must reach before it
     *   becomes an event. Backed by the user's auto-events settings.
     * @param aiNamingEnabled Whether to invoke [suggestEventName] for a label. When false,
     *   the use case never calls the extractor and every event uses the heuristic name.
     * @return The number of events created on this pass, or `Result.failure` if a stage
     *   threw something the use case couldn't recover from.
     */
    suspend operator fun invoke(
        window: Duration = 7.days,
        sensitivity: EventInferenceSensitivity = EventInferenceSensitivity.MEDIUM,
        aiNamingEnabled: Boolean = true,
    ): Result<Int> =
        runCatching {
            val nowInstant = now()
            val windowStart = nowInstant - window
            val stops =
                observeLocationStops()
                    .first()
                    .filter { stop ->
                        stop.evidenceKind == LocationStopEvidenceKind.STAY &&
                            stop.hasReliableDuration &&
                            stop.duration >= MIN_STOP_DURATION &&
                            stop.startTime >= windowStart
                    }
            if (stops.isEmpty()) {
                return@runCatching 0
            }

            // Pull media, notes, and existing events ONCE for the whole window. Each per-stop
            // cluster then filters in-memory by its [stop.startTime - SLACK, stop.endTime + SLACK]
            // bounds. Three reads for the whole pass instead of three per stop.
            val windowEnd = nowInstant + SLACK
            val windowFetchStart = windowStart - SLACK
            val mediaInWindow = indexedMediaRepository.getForPeriod(windowFetchStart, windowEnd).first()
            val notesInWindow = notesRepository.observeNotesInRange(windowFetchStart, windowEnd).first()
            val eventsInWindow = eventRepository.observeEventsForDateRange(windowFetchStart, windowEnd).first()

            var created = 0
            for (stop in stops) {
                val place = resolvePlace(stop)
                if (place == null) {
                    Napier.d(tag = TAG, message = "Skipping stop with no resolvable place")
                    continue
                }
                if (isFamiliarPlace(place.placeKey)) {
                    continue
                }

                val clusterStart = stop.startTime - SLACK
                val clusterEnd = stop.endTime + SLACK
                if (clusterOverlapsExistingEvent(eventsInWindow, clusterStart, clusterEnd)) {
                    continue
                }

                val media = mediaInWindow.filter { it.timestamp in clusterStart..clusterEnd }
                val notes = notesInWindow.filter { it.creationTimestamp in clusterStart..clusterEnd }
                if (media.size + notes.size < sensitivity.signalThreshold) {
                    continue
                }

                val cluster =
                    EventCluster(
                        placeName = place.displayName,
                        timeOfDay = describeTimeOfDay(stop.startTime),
                        mediaCount = media.size,
                        noteCount = notes.size,
                        firstNoteSnippet = firstTextSnippet(notes),
                    )
                val name = nameCluster(cluster, aiNamingEnabled)
                val event =
                    Event(
                        title = name.title,
                        description = name.description,
                        startTime = stop.startTime,
                        endTime = stop.endTime,
                        placeId = place.userPlaceId,
                    )
                val result = eventRepository.createEvent(event)
                if (result.isSuccess) {
                    created += 1
                } else {
                    Napier.w(
                        tag = TAG,
                        message = "Failed to persist inferred event",
                        throwable = result.exceptionOrNull(),
                    )
                }
            }
            created
        }.onFailure { error ->
            Napier.e(tag = TAG, message = "Event inference pass failed", throwable = error)
        }

    private suspend fun resolvePlace(stop: LocationStop): ResolvedPlace? {
        val resolved = resolvePlaceForLocation(stop.location)
        val key = resolved.toPlaceKey() ?: return null
        val name = resolved.toDisplayName() ?: return null
        return ResolvedPlace(
            displayName = name,
            placeKey = key,
            userPlaceId = resolved.toUserPlaceId(),
        )
    }

    private suspend fun isFamiliarPlace(placeKey: String): Boolean {
        val familiarity = placeFamiliarity.get(placeKey) ?: return false
        return familiarity.visitCount >= FAMILIAR_VISIT_THRESHOLD
    }

    /**
     * True when any pre-fetched event overlaps the cluster's `[clusterStart, clusterEnd)`
     * range. Matches the half-open form used by [EventDao.observeForDateRange] so a cluster
     * starting at exactly `existing.endTime` is treated as immediately-after, not overlapping.
     */
    private fun clusterOverlapsExistingEvent(
        candidates: List<Event>,
        clusterStart: Instant,
        clusterEnd: Instant,
    ): Boolean =
        candidates.any { existing ->
            existing.startTime < clusterEnd && (existing.endTime ?: existing.startTime) >= clusterStart
        }

    private suspend fun nameCluster(
        cluster: EventCluster,
        aiNamingEnabled: Boolean,
    ): EventNameResult {
        if (!aiNamingEnabled) {
            return heuristicName(cluster)
        }
        return when (val response = suggestEventName(cluster)) {
            is AIResult.Success -> EventNameResult(response.value.title, response.value.description)
            is AIResult.Error, is AIResult.Unavailable -> heuristicName(cluster)
        }
    }

    private fun heuristicName(cluster: EventCluster): EventNameResult {
        val title =
            if (cluster.placeName != null) {
                "${cluster.timeOfDay} at ${cluster.placeName}"
            } else {
                cluster.timeOfDay
            }
        val description =
            buildString {
                if (cluster.placeName != null) {
                    append("Time spent at ").append(cluster.placeName)
                } else {
                    append("Time spent ").append(cluster.timeOfDay.lowercase())
                }
                if (cluster.mediaCount > 0) {
                    append(" with ").append(cluster.mediaCount).append(" capture")
                    if (cluster.mediaCount != 1) append('s')
                }
                append('.')
            }
        return EventNameResult(title = title, description = description)
    }

    private fun firstTextSnippet(notes: List<JournalNote>): String? {
        val text =
            notes
                .filterIsInstance<JournalNote.Text>()
                .minByOrNull { it.creationTimestamp }
                ?.content
                ?: return null
        return text.take(MAX_SNIPPET_LENGTH)
    }

    /**
     * Picks a coarse, prompt-friendly time-of-day phrase for [instant]. Used as both the
     * heuristic title and as the cluster context handed to the AI extractor. The hour is
     * resolved in the device's local timezone — the user's "evening" should not get labeled
     * "early morning" because the cluster timestamp happens to fall after UTC midnight.
     */
    private fun describeTimeOfDay(instant: Instant): String {
        val hour = instant.toLocalDateTime(TimeZone.currentSystemDefault()).hour
        return when (hour) {
            in 5..10 -> "Morning"
            in 11..13 -> "Midday"
            in 14..17 -> "Afternoon"
            in 18..21 -> "Evening"
            else -> "Late night"
        }
    }

    private data class ResolvedPlace(
        val displayName: String,
        val placeKey: String,
        val userPlaceId: Uuid?,
    )

    private data class EventNameResult(
        val title: String,
        val description: String,
    )

    companion object {
        private const val TAG = "InferEventsUseCase"
        private const val MAX_SNIPPET_LENGTH = 200
        private const val FAMILIAR_VISIT_THRESHOLD = 5
        private val MIN_STOP_DURATION = 30.minutes
        private val SLACK = 15.minutes
    }
}
