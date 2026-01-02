package app.logdate.client.domain.rewind

import app.logdate.client.domain.media.IndexMediaForPeriodUseCase
import app.logdate.client.domain.notes.FetchNotesForDayUseCase
import app.logdate.client.intelligence.AIResult
import app.logdate.client.intelligence.entity.people.PeopleExtractor
import app.logdate.client.intelligence.narrative.RewindSequencer
import app.logdate.client.intelligence.narrative.WeekNarrativeSynthesizer
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.media.IndexedMedia
import app.logdate.client.repository.media.IndexedMediaRepository
import app.logdate.client.repository.rewind.RewindGenerationManager
import app.logdate.client.repository.rewind.RewindRepository
import app.logdate.shared.model.Rewind
import app.logdate.shared.model.RewindContent
import app.logdate.shared.model.RewindGenerationRequest
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
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
        pollInterval: Duration = DEFAULT_POLL_INTERVAL
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
        val request = try {
            generationManager.requestGeneration(startTime, endTime)
        } catch (e: Exception) {
            Napier.e("Failed to create generation request", e)
            return GenerateBasicRewindResult.Error(
                "Failed to initiate rewind generation: ${e.message ?: "Unknown error"}",
                e
            )
        }
        
        Napier.d("Created generation request: ${request.id}")
        
        try {
            // First ensure all media in the time period is indexed
            val newlyIndexedCount = indexMediaForPeriod(startTime, endTime)
            Napier.d("Indexed $newlyIndexedCount new media items")

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
                    "No content available for rewind"
                )
                return GenerateBasicRewindResult.NoContent
            }

            Napier.d("Collected ${allTextEntries.size} text entries and ${mediaItems.size} media items")

            // Extract people mentioned in entries for narrative context
            val people = allTextEntries.flatMap { entry ->
                when (val result = peopleExtractor.extractPeople(
                    documentId = entry.uid.toString(),
                    text = entry.content
                )) {
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

            // Synthesize narrative understanding of the week
            val narrativeResult = narrativeSynthesizer.synthesize(
                weekId = weekId,
                textEntries = allTextEntries,
                media = mediaItems,
                people = people,
                useCached = true
            )

            // Sequence content into narrative-driven panels
            val narrativeContent = when (narrativeResult) {
                is AIResult.Success -> {
                    Napier.d("Using AI narrative with ${narrativeResult.value.storyBeats.size} story beats")
                    rewindSequencer.sequence(
                        narrative = narrativeResult.value,
                        textEntries = allTextEntries,
                        media = mediaItems,
                        people = people
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

            // Create the rewind with narrative-driven content
            val rewind = Rewind(
                uid = Uuid.random(),
                startDate = startTime,
                endDate = endTime,
                generationDate = Clock.System.now(),
                label = titleInfo.label,
                title = titleInfo.title,
                content = narrativeContent
            )
            
            // Save the rewind to the repository
            rewindRepository.saveRewind(rewind)
            
            // Update generation request status
            updateGenerationStatus(
                request.id, 
                RewindGenerationRequest.Status.COMPLETED
            )
            
            Napier.d("Successfully generated rewind with ${narrativeContent.size} content items")
            return GenerateBasicRewindResult.Success(rewind)
        } catch (e: Exception) {
            Napier.e("Failed to generate rewind", e)
            updateGenerationStatus(
                request.id,
                RewindGenerationRequest.Status.FAILED,
                e.message
            )
            return GenerateBasicRewindResult.Error(
                "Failed to generate rewind: ${e.message ?: "Unknown error"}",
                e
            )
        }
    }
    
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
        pollInterval: Duration
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
        details: String? = null
    ) {
        try {
            // Just use the cancelGeneration method to set the status to cancelled if needed
            if (status == RewindGenerationRequest.Status.CANCELLED) {
                generationManager.cancelGeneration(requestId)
            }
            
            // Log the status update
            Napier.d("Updating generation request $requestId to $status ${details?.let{"($it)"} ?: ""}")
        } catch (e: Exception) {
            Napier.e("Failed to update generation status", e)
        }
    }
}

/**
 * Extension function to convert a JournalNote to RewindContent.
 * 
 * TODO: Consider replacing with a proper mapper class that handles all conversions
 * between domain models and persistence models in a centralized way.
 */
private fun JournalNote.toRewindContent(): RewindContent {
    return when (this) {
        is JournalNote.Text -> RewindContent.TextNote(
            timestamp = creationTimestamp,
            sourceId = uid,
            content = content
        )
        else -> throw IllegalArgumentException("Unsupported note type: $this")
    }
}

/**
 * Extension function to convert an IndexedMedia to RewindContent.
 * 
 * TODO: Consider replacing with a proper mapper class that handles all conversions
 * between domain models and persistence models in a centralized way.
 */
private fun IndexedMedia.toRewindContent(): RewindContent {
    return when (this) {
        is IndexedMedia.Image -> RewindContent.Image(
            timestamp = timestamp,
            sourceId = uid,
            uri = uri,
            caption = caption
        )
        is IndexedMedia.Video -> RewindContent.Video(
            timestamp = timestamp,
            sourceId = uid,
            uri = uri,
            caption = caption,
            duration = duration
        )
    }
}
