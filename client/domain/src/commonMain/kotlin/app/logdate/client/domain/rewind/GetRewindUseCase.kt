package app.logdate.client.domain.rewind

import app.logdate.client.repository.rewind.RewindRepository
import app.logdate.client.repository.rewind.RewindGenerationManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import io.github.aakira.napier.Napier

/**
 * A use case that retrieves or constructs a Rewind for a given time period or by ID.
 * 
 * This use case implements temporal logic to automatically generate rewinds on-demand
 * for current and past time periods, while preventing anticipation of future rewinds.
 */
class GetRewindUseCase(
    private val rewindRepository: RewindRepository,
    private val generationManager: RewindGenerationManager,
    private val generateBasicRewindUseCase: GenerateBasicRewindUseCase,
) {
    /**
     * Retrieves a Rewind for a given time period with temporal logic and instant UX.
     * 
     * This method implements the following behavior:
     * - Future dates: Return NotReady (prevents anticipation)
     * - Current/Past dates with existing rewind: Return Success immediately
     * - Current/Past dates without rewind: Return Generating immediately and trigger background generation
     *
     * This provides an "instant" UX where users can immediately navigate to rewind detail
     * views with loading states while generation happens in the background.
     *
     * @param params Information used to retrieve the Rewind.
     */
    operator fun invoke(params: RewindParams): Flow<RewindQueryResult> {
        val now = Clock.System.now()
        
        // Check if this is a future rewind (prevent anticipation)
        if (params.end > now) {
            Napier.d("Rewind requested for future period, returning NotReady")
            return flow { emit(RewindQueryResult.NotReady) }
        }
        
        // Check if generation is already in progress and get rewind
        return channelFlow {
            if (generationManager.isGenerationInProgress(params.start, params.end)) {
                Napier.d("Generation already in progress for period")
                send(RewindQueryResult.Generating)
                return@channelFlow
            }

            Napier.d("Checking for existing rewind for period ${params.start} to ${params.end}")

            // Collect the repository flow to check if rewind exists
            rewindRepository.getRewindBetween(params.start, params.end).collect { rewind ->
                if (rewind != null) {
                    Napier.d("Found existing rewind for period")
                    send(RewindQueryResult.Success(rewind))
                } else {
                    // No rewind exists for this current/past period - trigger instant generation
                    Napier.d("No rewind exists for period, triggering instant generation")

                    // Immediately emit Generating state for instant UX
                    send(RewindQueryResult.Generating)

                    // Trigger generation in background for seamless experience
                    launch {
                        try {
                            val result = generateBasicRewindUseCase(params.start, params.end)
                            when (result) {
                                is GenerateBasicRewindResult.Success -> {
                                    Napier.i("Successfully generated rewind for period - UI will auto-update")
                                }
                                is GenerateBasicRewindResult.AlreadyInProgress -> {
                                    Napier.d("Generation already in progress - will continue")
                                }
                                is GenerateBasicRewindResult.NoContent -> {
                                    Napier.w("No content available for rewind period")
                                }
                                is GenerateBasicRewindResult.Error -> {
                                    Napier.e("Failed to generate rewind: ${result.error}", result.exception)
                                }
                            }
                        } catch (e: Exception) {
                            Napier.e("Error during background rewind generation", e)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Retrieves a Rewind by its unique identifier.
     *
     * @param rewindId The unique identifier of the rewind to retrieve.
     */
    operator fun invoke(rewindId: kotlin.uuid.Uuid): Flow<app.logdate.shared.model.Rewind> {
        return rewindRepository.getRewind(rewindId)
    }
}

/**
 * Request parameters for fetching a [Rewind].
 */
data class RewindParams(
    /**
     * The start of the time period for the Rewind.
     */
    val start: Instant,
    /**
     * The end of the time period for the Rewind.
     */
    val end: Instant,
)
