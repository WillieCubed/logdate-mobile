package app.logdate.client.domain.rewind

import app.logdate.client.domain.media.IndexMediaForPeriodUseCase
import app.logdate.client.domain.notes.FetchNotesForDayUseCase
import app.logdate.client.intelligence.AIResult
import app.logdate.client.intelligence.entity.people.PeopleExtractor
import app.logdate.client.intelligence.rewind.strategy.RewindInput
import app.logdate.client.intelligence.rewind.strategy.RewindStrategySelector
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.client.repository.media.IndexedMediaRepository
import app.logdate.client.repository.rewind.RewindGenerationManager
import app.logdate.client.repository.rewind.RewindRepository
import app.logdate.shared.model.ActivityType
import app.logdate.shared.model.Rewind
import app.logdate.shared.model.RewindGenerationRequest
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Use case for generating a narrative-driven rewind for a given time period.
 *
 * This use case is a thin orchestration layer — it collects the period's text entries,
 * media, people, and location history, hands them to the tier-aware strategy chosen by
 * [RewindStrategySelector], and saves the produced Rewind. All real work (narrative
 * synthesis, curation, sequencing, fallback handling) lives inside the strategies.
 */
class GenerateBasicRewindUseCase(
    private val rewindRepository: RewindRepository,
    private val generationManager: RewindGenerationManager,
    private val fetchNotesForDay: FetchNotesForDayUseCase,
    private val indexedMediaRepository: IndexedMediaRepository,
    private val indexMediaForPeriod: IndexMediaForPeriodUseCase,
    private val generateRewindTitle: GenerateRewindTitleUseCase,
    private val strategySelector: RewindStrategySelector,
    private val peopleExtractor: PeopleExtractor,
    private val locationHistoryRepository: LocationHistoryRepository,
) {
    private companion object {
        private val DEFAULT_WAIT_TIMEOUT = 5.seconds
        private val DEFAULT_POLL_INTERVAL = 1.seconds
    }

    /**
     * Generates a Rewind for the specified time period.
     *
     * @param startTime Start of the time period (inclusive)
     * @param endTime End of the time period (exclusive)
     * @param waitTimeout Maximum time to wait for an in-progress generation
     * @param pollInterval How frequently to check generation status when waiting
     */
    suspend operator fun invoke(
        startTime: Instant,
        endTime: Instant,
        waitTimeout: Duration = DEFAULT_WAIT_TIMEOUT,
        pollInterval: Duration = DEFAULT_POLL_INTERVAL,
    ): GenerateBasicRewindResult {
        Napier.d("Generating rewind for period: $startTime to $endTime")

        if (generationManager.isGenerationInProgress(startTime, endTime)) {
            Napier.i("Rewind generation already in progress for this period")
            if (waitForExistingGeneration(startTime, endTime, waitTimeout, pollInterval)) {
                rewindRepository.getRewindBetween(startTime, endTime).firstOrNull()?.let { existingRewind ->
                    return GenerateBasicRewindResult.Success(existingRewind)
                }
            }
            return GenerateBasicRewindResult.AlreadyInProgress
        }

        val request =
            try {
                generationManager.requestGeneration(startTime, endTime)
            } catch (e: Exception) {
                Napier.e("Failed to create generation request", e)
                return GenerateBasicRewindResult.Error(
                    "Failed to initiate rewind generation: ${e.message ?: "Unknown error"}",
                    e,
                )
            }

        try {
            // Index media for the period — non-fatal if this fails.
            try {
                val newlyIndexedCount = indexMediaForPeriod(startTime, endTime)
                Napier.d("Indexed $newlyIndexedCount new media items")
            } catch (e: Exception) {
                Napier.w("Media indexing failed, continuing with already-indexed media", e)
            }

            val allTextEntries = mutableListOf<JournalNote.Text>()
            val timezone = TimeZone.currentSystemDefault()
            var currentDate = startTime.toLocalDateTime(timezone).date
            if (startTime < endTime) {
                val lastIncludedDate =
                    endTime
                        .minus(1.nanoseconds)
                        .toLocalDateTime(timezone)
                        .date
                while (currentDate <= lastIncludedDate) {
                    val notesForDay = fetchNotesForDay(currentDate).firstOrNull() ?: emptyList()
                    allTextEntries.addAll(notesForDay.filterIsInstance<JournalNote.Text>())
                    currentDate = currentDate.plus(1, DateTimeUnit.DAY)
                }
            }

            val mediaItems = indexedMediaRepository.getForPeriod(startTime, endTime).firstOrNull() ?: emptyList()

            if (allTextEntries.isEmpty() && mediaItems.isEmpty()) {
                updateGenerationStatus(
                    request.id,
                    RewindGenerationRequest.Status.FAILED,
                    "No content available for rewind",
                )
                return GenerateBasicRewindResult.NoContent
            }

            // Extract people for narrative + curation signals.
            val people =
                allTextEntries
                    .flatMap { entry ->
                        when (
                            val result =
                                peopleExtractor.extractPeople(
                                    documentId = entry.uid.toString(),
                                    text = entry.content,
                                )
                        ) {
                            is AIResult.Success -> result.value
                            is AIResult.Unavailable -> emptyList()
                            is AIResult.Error -> emptyList()
                        }
                    }.distinctBy { it.name }

            val locationHistory =
                runCatching { locationHistoryRepository.getLocationHistoryBetween(startTime, endTime) }
                    .getOrElse { emptyList() }

            val input =
                RewindInput(
                    periodStart = startTime,
                    periodEnd = endTime,
                    textEntries = allTextEntries,
                    media = mediaItems,
                    people = people,
                    locationHistory = locationHistory,
                    weekId = "${startTime.toLocalDateTime(timezone).date}",
                )

            val strategy = strategySelector.select()
            Napier.d("Selected strategy: ${strategy.name}")
            val output = strategy.produce(input)
            Napier.d(
                "Strategy ${output.provenance.strategy} produced ${output.content.size} panels" +
                    (output.provenance.fellBackFrom?.let { " (fell back from $it)" } ?: ""),
            )

            val titleInfo = generateRewindTitle(startTime, endTime)
            val rewind =
                Rewind(
                    uid = Uuid.random(),
                    startDate = startTime,
                    endDate = endTime,
                    generationDate = Clock.System.now(),
                    label = titleInfo.label,
                    title = titleInfo.title,
                    content = output.content,
                    metadata = output.metadata,
                )

            rewindRepository.saveRewind(rewind)
            updateGenerationStatus(request.id, RewindGenerationRequest.Status.COMPLETED)
            return GenerateBasicRewindResult.Success(rewind)
        } catch (e: Exception) {
            Napier.e("Failed to generate rewind", e)
            updateGenerationStatus(request.id, RewindGenerationRequest.Status.FAILED, e.message)
            return GenerateBasicRewindResult.Error(
                "Failed to generate rewind: ${e.message ?: "Unknown error"}",
                e,
            )
        }
    }

    private suspend fun waitForExistingGeneration(
        startTime: Instant,
        endTime: Instant,
        timeout: Duration,
        pollInterval: Duration,
    ): Boolean {
        val endWaitTime = Clock.System.now() + timeout
        while (Clock.System.now() < endWaitTime) {
            rewindRepository.getRewindBetween(startTime, endTime).firstOrNull()?.let { return true }
            if (!generationManager.isGenerationInProgress(startTime, endTime)) {
                Napier.w("Generation no longer in progress but no rewind found")
                return false
            }
            delay(pollInterval)
        }
        return false
    }

    private suspend fun updateGenerationStatus(
        requestId: Uuid,
        status: RewindGenerationRequest.Status,
        details: String? = null,
    ) {
        try {
            if (status == RewindGenerationRequest.Status.CANCELLED) {
                generationManager.cancelGeneration(requestId)
            } else {
                generationManager.updateRequestStatus(requestId, status, details)
            }
        } catch (e: Exception) {
            Napier.e("Failed to update generation status", e)
        }
    }
}

/**
 * Derives activity types from a list of narrative themes. Kept at file scope so it can
 * be unit tested without standing up the full use case dependency graph; the result is
 * used by intelligence strategies that build metadata.
 */
internal fun deriveActivitiesFromThemes(themes: List<String>): List<ActivityType> {
    val activities = mutableSetOf<ActivityType>()
    val lowerThemes = themes.map { it.lowercase() }

    for (theme in lowerThemes) {
        when {
            theme.contains("travel") ||
                theme.contains("trip") ||
                theme.contains("vacation") ||
                theme.contains("flight") ||
                theme.contains("road") -> activities.add(ActivityType.TRAVEL)
            theme.contains("social") ||
                theme.contains("friend") ||
                theme.contains("party") ||
                theme.contains("gathering") ||
                theme.contains("dinner") -> activities.add(ActivityType.SOCIAL)
            theme.contains("work") ||
                theme.contains("project") ||
                theme.contains("deadline") ||
                theme.contains("focus") ||
                theme.contains("productive") -> activities.add(ActivityType.FOCUSED_WORK)
            theme.contains("quiet") ||
                theme.contains("rest") ||
                theme.contains("relax") ||
                theme.contains("solitude") -> activities.add(ActivityType.QUIET)
            theme.contains("milestone") ||
                theme.contains("achievement") ||
                theme.contains("graduation") ||
                theme.contains("birthday") ||
                theme.contains("anniversary") -> activities.add(ActivityType.MILESTONE)
        }
    }

    return activities.toList().ifEmpty { listOf(ActivityType.MIXED) }
}
