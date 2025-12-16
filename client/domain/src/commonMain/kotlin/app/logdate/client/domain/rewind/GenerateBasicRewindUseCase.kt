package app.logdate.client.domain.rewind

import app.logdate.client.domain.media.IndexMediaForPeriodUseCase
import app.logdate.client.domain.notes.FetchNotesForDayUseCase
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
 * Use case for generating a basic rewind for a given time period.
 * 
 * This use case coordinates the generation of a rewind by:
 * 1. First ensuring all media in the time period is properly indexed
 * 2. Fetching notes and indexed media for the period
 * 3. Organizing them into a cohesive rewind structure
 * 4. Saving the rewind to the repository
 * 
 * This serves as a proof-of-concept implementation that demonstrates
 * the core rewind generation workflow.
 */
class GenerateBasicRewindUseCase(
    private val rewindRepository: RewindRepository,
    private val generationManager: RewindGenerationManager,
    private val fetchNotesForDay: FetchNotesForDayUseCase,
    private val indexedMediaRepository: IndexedMediaRepository,
    private val indexMediaForPeriod: IndexMediaForPeriodUseCase,
    private val generateRewindTitle: GenerateRewindTitleUseCase,
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
            
            // Collect all content for the rewind
            val allContent = mutableListOf<RewindContent>()
            
            // Convert the date range to local dates for day-by-day processing
            val timezone = TimeZone.currentSystemDefault()
            var currentDate = startTime.toLocalDateTime(timezone).date
            val endDate = endTime.toLocalDateTime(timezone).date
            
            // Collect notes for each day in the period
            while (currentDate <= endDate) {
                val notesForDay = fetchNotesForDay(currentDate).firstOrNull() ?: emptyList()
                
                // TODO: Direct mapping from JournalNote to RewindContent is likely a code smell.
                // We should consider introducing a proper domain model mapper or transformation layer
                // that handles the conversion between repository and domain models more explicitly.
                allContent.addAll(notesForDay.mapNotNull { 
                    try {
                        it.toRewindContent() 
                    } catch (e: Exception) {
                        Napier.w("Failed to convert note to rewind content: ${e.message}")
                        null
                    }
                })
                
                currentDate = currentDate.plus(1, DateTimeUnit.DAY)
            }
            
            // Collect indexed media for the period
            val mediaItems = indexedMediaRepository.getForPeriod(startTime, endTime).first()
            allContent.addAll(mediaItems.map { it.toRewindContent() })
            
            // Check if we have any content
            if (allContent.isEmpty()) {
                updateGenerationStatus(
                    request.id,
                    RewindGenerationRequest.Status.FAILED,
                    "No content available for rewind"
                )
                return GenerateBasicRewindResult.NoContent
            }
            
            // Generate a title and label based on the period
            val titleInfo = generateRewindTitle(startTime, endTime)
            
            // Create the rewind
            val rewind = Rewind(
                uid = Uuid.random(),
                startDate = startTime,
                endDate = endTime,
                generationDate = Clock.System.now(),
                label = titleInfo.label,
                title = titleInfo.title,
                content = allContent
            )
            
            // Save the rewind to the repository
            rewindRepository.saveRewind(rewind)
            
            // Update generation request status
            updateGenerationStatus(
                request.id, 
                RewindGenerationRequest.Status.COMPLETED
            )
            
            Napier.d("Successfully generated rewind with ${allContent.size} content items")
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