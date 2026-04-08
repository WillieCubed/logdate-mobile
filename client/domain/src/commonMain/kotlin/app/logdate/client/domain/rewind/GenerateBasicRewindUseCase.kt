package app.logdate.client.domain.rewind

import app.logdate.client.domain.media.IndexMediaForPeriodUseCase
import app.logdate.client.domain.notes.FetchNotesForDayUseCase
import app.logdate.client.intelligence.AIResult
import app.logdate.client.intelligence.entity.people.PeopleExtractor
import app.logdate.client.intelligence.narrative.RewindSequencer
import app.logdate.client.intelligence.narrative.WeekNarrativeSynthesizer
import app.logdate.client.intelligence.weather.WeatherFetchLocation
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.client.repository.media.IndexedMedia
import app.logdate.client.repository.media.IndexedMediaRepository
import app.logdate.client.repository.rewind.RewindGenerationManager
import app.logdate.client.repository.rewind.RewindRepository
import app.logdate.shared.model.ActivityType
import app.logdate.shared.model.LocationSummary
import app.logdate.shared.model.MapPoint
import app.logdate.shared.model.Rewind
import app.logdate.shared.model.RewindContent
import app.logdate.shared.model.RewindGenerationRequest
import app.logdate.shared.model.RewindMetadata
import app.logdate.shared.model.WeekNarrative
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Use case for generating a narrative-driven rewind for a given time period.
 *
 * This use case coordinates the generation of a rewind by:
 * 1. Ensuring all media in the time period is properly indexed
 * 2. Fetching notes and indexed media for the period
 * 3. Using AI to understand the STORY of the week (themes, emotional tone, key moments)
 * 4. Sequencing content into a narrative-driven structure
 * 5. Saving the rewind to the repository
 *
 * Unlike mechanical content aggregation, this creates a story that contextualizes
 * what the week was ABOUT, not just what content exists.
 */
class GenerateBasicRewindUseCase(
    private val rewindRepository: RewindRepository,
    private val generationManager: RewindGenerationManager,
    private val fetchNotesForDay: FetchNotesForDayUseCase,
    private val indexedMediaRepository: IndexedMediaRepository,
    private val indexMediaForPeriod: IndexMediaForPeriodUseCase,
    private val generateRewindTitle: GenerateRewindTitleUseCase,
    private val narrativeSynthesizer: WeekNarrativeSynthesizer,
    private val rewindSequencer: RewindSequencer,
    private val peopleExtractor: PeopleExtractor,
    private val locationHistoryRepository: LocationHistoryRepository,
) {
    private companion object {
        /**
         * Default wait timeout when checking for an existing rewind generation.
         */
        private val DEFAULT_WAIT_TIMEOUT = 5.seconds

        /**
         * Default polling interval when waiting for a rewind generation to complete.
         */
        private val DEFAULT_POLL_INTERVAL = 1.seconds

        /**
         * Cap on the number of [MapPoint]s persisted on a rewind so the metadata JSON
         * blob stays compact. Enough resolution for a coarse weekly path; the renderer
         * doesn't need higher fidelity to read as a map.
         */
        private const val MAP_POINT_LIMIT = 50
    }

    /**
     * Generates a rewind for the specified time period.
     *
     * This operation will:
     * 1. Check if a generation is already in progress
     * 2. Create a generation request to track progress
     * 3. Index any unindexed media in the time period
     * 4. Collect all notes and indexed media for the period
     * 5. Create and save a rewind with this content
     *
     * @param startTime Start of the time period (inclusive)
     * @param endTime End of the time period (exclusive)
     * @param waitTimeout Maximum time to wait for an in-progress generation
     * @param pollInterval How frequently to check generation status when waiting
     * @return Result of the generation attempt
     */
    suspend operator fun invoke(
        startTime: Instant,
        endTime: Instant,
        waitTimeout: Duration = DEFAULT_WAIT_TIMEOUT,
        pollInterval: Duration = DEFAULT_POLL_INTERVAL,
    ): GenerateBasicRewindResult {
        Napier.d("Generating rewind for period: $startTime to $endTime")

        // Check if a generation is already in progress
        if (generationManager.isGenerationInProgress(startTime, endTime)) {
            Napier.i("Rewind generation already in progress for this period")

            // TODO: In the future, we may want to support different types of rewinds
            // for the same time period (e.g., different themes or content focuses).
            // The current implementation assumes only one rewind per time period.

            // Wait for any in-progress generation to complete
            if (waitForExistingGeneration(startTime, endTime, waitTimeout, pollInterval)) {
                rewindRepository.getRewindBetween(startTime, endTime).firstOrNull()?.let { existingRewind ->
                    Napier.d("Successfully retrieved rewind after waiting")
                    return GenerateBasicRewindResult.Success(existingRewind)
                }
            }

            // If we waited but no rewind was produced, report that generation is in progress
            return GenerateBasicRewindResult.AlreadyInProgress
        }

        // Create generation request
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

        Napier.d("Created generation request: ${request.id}")

        try {
            // Index media for the period — non-fatal if this fails
            try {
                val newlyIndexedCount = indexMediaForPeriod(startTime, endTime)
                Napier.d("Indexed $newlyIndexedCount new media items")
            } catch (e: Exception) {
                Napier.w("Media indexing failed, continuing with already-indexed media", e)
            }

            // Collect all raw content for narrative synthesis
            val allTextEntries = mutableListOf<JournalNote.Text>()

            // Convert the date range to local dates for day-by-day processing
            val timezone = TimeZone.currentSystemDefault()
            var currentDate = startTime.toLocalDateTime(timezone).date
            val endDate = endTime.toLocalDateTime(timezone).date

            // Collect notes for each day in the period
            while (currentDate <= endDate) {
                val notesForDay = fetchNotesForDay(currentDate).firstOrNull() ?: emptyList()
                allTextEntries.addAll(notesForDay.filterIsInstance<JournalNote.Text>())
                currentDate = currentDate.plus(1, DateTimeUnit.DAY)
            }

            // Collect indexed media for the period
            val mediaItems = indexedMediaRepository.getForPeriod(startTime, endTime).first()

            // Check if we have any content
            if (allTextEntries.isEmpty() && mediaItems.isEmpty()) {
                updateGenerationStatus(
                    request.id,
                    RewindGenerationRequest.Status.FAILED,
                    "No content available for rewind",
                )
                return GenerateBasicRewindResult.NoContent
            }

            Napier.d("Collected ${allTextEntries.size} text entries and ${mediaItems.size} media items")

            // Extract people mentioned in entries for narrative context
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
                            is AIResult.Unavailable -> {
                                Napier.w("People extraction unavailable for entry ${entry.uid}")
                                emptyList()
                            }
                            is AIResult.Error -> {
                                Napier.w("Failed to extract people from entry ${entry.uid}: ${result.error}")
                                emptyList()
                            }
                        }
                    }.distinctBy { it.name }

            Napier.d("Extracted ${people.size} unique people from entries")

            // Generate week identifier for narrative caching
            val weekId = "${startTime.toLocalDateTime(timezone).date}"

            // Compute the rewind's primary location so the synthesizer can fetch
            // historical weather in parallel with the LLM call. Best-effort: a missing
            // or empty location history just skips weather entirely.
            val locationHistory =
                runCatching { locationHistoryRepository.getLocationHistoryBetween(startTime, endTime) }
                    .getOrElse { emptyList() }
            val primaryLocation = computePrimaryLocation(locationHistory)

            // Synthesize narrative understanding of the week
            val narrativeResult =
                narrativeSynthesizer.synthesize(
                    weekId = weekId,
                    textEntries = allTextEntries,
                    media = mediaItems,
                    people = people,
                    primaryLocation = primaryLocation,
                    periodStart = startTime,
                    periodEnd = endTime,
                    useCached = true,
                )

            // Sequence content into narrative-driven panels
            val narrativeContent =
                when (narrativeResult) {
                    is AIResult.Success -> {
                        Napier.d("Using AI narrative with ${narrativeResult.value.storyBeats.size} story beats")
                        rewindSequencer.sequence(
                            narrative = narrativeResult.value,
                            textEntries = allTextEntries,
                            media = mediaItems,
                            people = people,
                        )
                    }
                    is AIResult.Unavailable -> {
                        Napier.w("Narrative synthesis unavailable, falling back to chronological content")
                        // Fallback: chronological content if narrative synthesis fails
                        val fallbackContent = mutableListOf<RewindContent>()
                        fallbackContent.addAll(allTextEntries.map { it.toRewindContent() })
                        fallbackContent.addAll(mediaItems.map { it.toRewindContent() })
                        fallbackContent.sortedBy { it.timestamp }
                    }
                    is AIResult.Error -> {
                        Napier.w("Narrative synthesis failed, falling back to chronological content")
                        val fallbackContent = mutableListOf<RewindContent>()
                        fallbackContent.addAll(allTextEntries.map { it.toRewindContent() })
                        fallbackContent.addAll(mediaItems.map { it.toRewindContent() })
                        fallbackContent.sortedBy { it.timestamp }
                    }
                }

            Napier.d("Generated ${narrativeContent.size} narrative panels")

            // Generate a title and label based on the period
            val titleInfo = generateRewindTitle(startTime, endTime)

            // Build intelligence metadata from collected data
            val narrative = (narrativeResult as? AIResult.Success)?.value
            val metadata =
                buildMetadata(
                    narrative = narrative,
                    people = people,
                    locationHistory = locationHistory,
                )

            // Create the rewind with narrative-driven content
            val rewind =
                Rewind(
                    uid = Uuid.random(),
                    startDate = startTime,
                    endDate = endTime,
                    generationDate = Clock.System.now(),
                    label = titleInfo.label,
                    title = titleInfo.title,
                    content = narrativeContent,
                    metadata = metadata,
                )

            // Save the rewind to the repository
            rewindRepository.saveRewind(rewind)

            // Update generation request status
            updateGenerationStatus(
                request.id,
                RewindGenerationRequest.Status.COMPLETED,
            )

            Napier.d("Successfully generated rewind with ${narrativeContent.size} content items")
            return GenerateBasicRewindResult.Success(rewind)
        } catch (e: Exception) {
            Napier.e("Failed to generate rewind", e)
            updateGenerationStatus(
                request.id,
                RewindGenerationRequest.Status.FAILED,
                e.message,
            )
            return GenerateBasicRewindResult.Error(
                "Failed to generate rewind: ${e.message ?: "Unknown error"}",
                e,
            )
        }
    }

    /**
     * Builds intelligence metadata from the data collected during generation.
     *
     * @param locationHistory Pre-fetched history shared with the weather lookup so we
     *   don't hit the location DAO twice per rewind.
     */
    private fun buildMetadata(
        narrative: WeekNarrative?,
        people: List<app.logdate.shared.model.Person>,
        locationHistory: List<LocationHistoryItem>,
    ): RewindMetadata {
        // Derive activity types from narrative themes
        val activities =
            narrative?.themes?.let { deriveActivities(it) }
                ?: listOf(ActivityType.MIXED)

        // Extract milestones from story beats
        val milestones =
            narrative
                ?.storyBeats
                ?.filter { it.emotionalWeight.lowercase() in setOf("triumphant", "milestone", "significant", "proud") }
                ?.map { it.moment }
                ?: emptyList()

        // Build location summary from the already-fetched history
        val locationSummary =
            if (locationHistory.isNotEmpty()) {
                LocationSummary(
                    distinctLocations =
                        locationHistory
                            .map {
                                "${it.location.latitude.toInt()},${it.location.longitude.toInt()}"
                            }.distinct()
                            .size,
                    newPlaces = 0, // Would require historical comparison
                    primaryLocation = null, // Would require reverse geocoding
                )
            } else {
                null
            }

        return RewindMetadata(
            detectedActivities = activities,
            locationSummary = locationSummary,
            milestones = milestones,
            peopleHighlighted = people.map { it.name },
            reflectionPrompts = narrative?.reflectionPrompts ?: emptyList(),
            highlightedQuotes = narrative?.highlightedQuotes ?: emptyList(),
            weatherContext = narrative?.weatherContext,
            locationPath = downsampleToMapPoints(locationHistory),
        )
    }

    /**
     * Reduces a raw location history list to at most [MAP_POINT_LIMIT] points by even
     * sampling, so the metadata blob stays small enough to round-trip cheaply through
     * the rewinds row's metadata column. The first and last samples are always
     * preserved so the renderer's polyline still anchors at the period's actual
     * endpoints.
     */
    private fun downsampleToMapPoints(history: List<LocationHistoryItem>): List<MapPoint> {
        if (history.isEmpty()) return emptyList()
        val asPoints =
            history.map { item ->
                MapPoint(
                    latitude = item.location.latitude,
                    longitude = item.location.longitude,
                    timestamp = item.timestamp,
                )
            }
        if (asPoints.size <= MAP_POINT_LIMIT) return asPoints
        val step = asPoints.size.toDouble() / MAP_POINT_LIMIT
        val sampled = mutableListOf<MapPoint>()
        var index = 0.0
        while (sampled.size < MAP_POINT_LIMIT - 1 && index.toInt() < asPoints.size) {
            sampled.add(asPoints[index.toInt()])
            index += step
        }
        // Always include the last point so the polyline ends where the user actually was.
        if (sampled.lastOrNull() != asPoints.last()) sampled.add(asPoints.last())
        return sampled
    }

    /**
     * Picks one representative point from the period's location history to anchor a
     * weather lookup against. Uses the centroid of the entries because the user might
     * have moved around the same general area all week and a single sample would be
     * arbitrary; an average over all samples is stable and cheap.
     */
    private fun computePrimaryLocation(history: List<LocationHistoryItem>): WeatherFetchLocation? {
        if (history.isEmpty()) return null
        val avgLat = history.sumOf { it.location.latitude } / history.size
        val avgLon = history.sumOf { it.location.longitude } / history.size
        return WeatherFetchLocation(latitude = avgLat, longitude = avgLon)
    }

    private fun deriveActivities(themes: List<String>): List<ActivityType> = deriveActivitiesFromThemes(themes)

    /**
     * Waits for an existing rewind generation to complete.
     *
     * @param startTime Start of the time period
     * @param endTime End of the time period
     * @param timeout Maximum time to wait
     * @param pollInterval How frequently to check generation status
     * @return True if waiting was successful (generation completed), false otherwise
     */
    private suspend fun waitForExistingGeneration(
        startTime: Instant,
        endTime: Instant,
        timeout: Duration,
        pollInterval: Duration,
    ): Boolean {
        Napier.d("Waiting for existing generation to complete (timeout: $timeout)")

        val endWaitTime = Clock.System.now() + timeout

        while (Clock.System.now() < endWaitTime) {
            // Check if the rewind is now available
            rewindRepository.getRewindBetween(startTime, endTime).firstOrNull()?.let {
                return true
            }

            // Check if generation is still in progress
            if (!generationManager.isGenerationInProgress(startTime, endTime)) {
                // Generation completed but no rewind found, something went wrong
                Napier.w("Generation no longer in progress but no rewind found")
                return false
            }

            // Wait before checking again
            delay(pollInterval)
        }

        Napier.d("Timed out waiting for existing generation")
        return false
    }

    /**
     * Updates the status of a generation request.
     */
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
            Napier.d("Updated generation request $requestId to $status ${details?.let { "($it)" } ?: ""}")
        } catch (e: Exception) {
            Napier.e("Failed to update generation status", e)
        }
    }
}

/**
 * Derives activity types from a list of narrative themes.
 *
 * Pure function — exposed at file scope so it can be unit tested without standing up
 * the full use case dependency graph. Returns [ActivityType.MIXED] when no specific
 * activities can be inferred from the themes.
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

/**
 * Extension function to convert a JournalNote to RewindContent.
 *
 * TODO: Consider replacing with a proper mapper class that handles all conversions
 * between domain models and persistence models in a centralized way.
 */
private fun JournalNote.toRewindContent(): RewindContent =
    when (this) {
        is JournalNote.Text ->
            RewindContent.TextNote(
                timestamp = creationTimestamp,
                sourceId = uid,
                content = content,
            )
        else -> throw IllegalArgumentException("Unsupported note type: $this")
    }

/**
 * Extension function to convert an IndexedMedia to RewindContent.
 *
 * TODO: Consider replacing with a proper mapper class that handles all conversions
 * between domain models and persistence models in a centralized way.
 */
private fun IndexedMedia.toRewindContent(): RewindContent =
    when (this) {
        is IndexedMedia.Image ->
            RewindContent.Image(
                timestamp = timestamp,
                sourceId = uid,
                uri = uri,
                caption = caption,
            )
        is IndexedMedia.Video ->
            RewindContent.Video(
                timestamp = timestamp,
                sourceId = uid,
                uri = uri,
                caption = caption,
                duration = duration,
            )
    }
