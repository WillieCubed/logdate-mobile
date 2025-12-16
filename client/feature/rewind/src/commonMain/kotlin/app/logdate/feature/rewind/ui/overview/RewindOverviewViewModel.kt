package app.logdate.feature.rewind.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.rewind.GenerateBasicRewindResult
import app.logdate.client.domain.rewind.GenerateBasicRewindUseCase
import app.logdate.client.domain.rewind.GetPastRewindsUseCase
import app.logdate.client.domain.rewind.GetWeekRewindUseCase
import app.logdate.client.domain.rewind.RewindQueryResult
import app.logdate.client.intelligence.rewind.RewindMessageGenerator
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime

/**
 * ViewModel managing the state and data flow for the Rewind overview screen.
 * 
 * This ViewModel orchestrates data from multiple domain use cases to provide a unified
 * UI state for the floating rewind cards interface. It handles both current week and
 * historical rewind data, ensuring smooth state transitions and proper error handling.
 * 
 * ## Data Sources:
 * - **Current Week**: `GetWeekRewindUseCase` for the most recent rewind status
 * - **Historical Data**: `GetPastRewindsUseCase` for previously completed rewinds
 * - **Messaging**: `RewindMessageGenerator` for AI-generated descriptive content
 * - **Generation**: `GenerateBasicRewindUseCase` for creating rewinds on demand
 * 
 * ## State Management:
 * Combines multiple data streams using `Flow.combine()` to create a single UI state
 * that gracefully handles all possible data availability scenarios:
 * - Loading: No data available yet
 * - NotReady: Past rewinds loaded, current week still processing
 * - Ready: All data available including current week
 * 
 * ## Architecture Benefits:
 * - **Reactive**: Automatically updates UI when underlying data changes
 * - **Error Resilient**: Handles partial data availability gracefully
 * - **Performance**: Uses StateFlow with WhileSubscribed for lifecycle awareness
 * - **Testable**: Clear separation of concerns and dependency injection
 * 
 * @param getWeekRewindUseCase Use case for fetching current week rewind status
 * @param getPastRewindsUseCase Use case for fetching historical rewind data
 * @param rewindMessageGenerator Service for generating AI-powered rewind descriptions
 * @param generateBasicRewindUseCase Use case for generating basic rewinds on demand
 */
class RewindOverviewViewModel(
    private val getWeekRewindUseCase: GetWeekRewindUseCase,
    private val getPastRewindsUseCase: GetPastRewindsUseCase,
    private val rewindMessageGenerator: RewindMessageGenerator,
    private val generateBasicRewindUseCase: GenerateBasicRewindUseCase,
) : ViewModel() {
    
    // Tracks whether a rewind generation is in progress
    private val _isGeneratingRewind = MutableStateFlow(false)
    
    private val pastRewinds: StateFlow<List<RewindHistoryUiState>> = getPastRewindsUseCase()
        .map { pastRewinds ->
            pastRewinds.map { rewind ->
                RewindHistoryUiState(
                    uid = rewind.uid,
                    title = rewind.title,
                )
            }
        }
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = emptyList(),
        )

    private val rewindUiState = combine(
        getWeekRewindUseCase(),
        pastRewinds,
        _isGeneratingRewind
    ) { rewindResult, pastRewinds, isGenerating ->
        when (rewindResult) {
            is RewindQueryResult.Success -> {
                RewindOverviewScreenUiState.Ready(
                    pastRewinds = pastRewinds,
                    mostRecentRewind = RewindPreviewUiState(
                        rewindId = rewindResult.rewind.uid,
                        message = rewindMessageGenerator.generateMessage(true),
                        label = rewindResult.rewind.label,
                        title = rewindResult.rewind.title,
                        // Use the startDate and endDate directly from the Rewind model
                        start = rewindResult.rewind.startDate.toLocalDateTime(TimeZone.currentSystemDefault()).date,
                        end = rewindResult.rewind.endDate.toLocalDateTime(TimeZone.currentSystemDefault()).date,
                        // If there's content, indicate rewind is available
                        rewindAvailable = rewindResult.rewind.content.isNotEmpty(),
                        // TODO: Add activity states based on rewind content
                    )
                )
            }

            RewindQueryResult.NotReady -> {
                // Check if we need to generate a basic rewind
                if (!isGenerating && !hasRecentlyAttemptedGeneration) {
                    // Automatically trigger rewind generation if not already in progress
                    generateCurrentWeekRewind()
                }
                
                RewindOverviewScreenUiState.NotReady(
                    pastRewinds = pastRewinds,
                    isGeneratingRewind = isGenerating
                )
            }
            
            RewindQueryResult.Generating -> {
                // Handle the Generating state - show NotReady UI with a loading indicator
                RewindOverviewScreenUiState.NotReady(
                    pastRewinds = pastRewinds,
                    isGeneratingRewind = true
                )
            }

            else -> RewindOverviewScreenUiState.Loading
        }
    }.stateIn(
        viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = RewindOverviewScreenUiState.Loading
        )

    val uiState: StateFlow<RewindOverviewScreenUiState> = rewindUiState
    
    // Track when we last attempted generation to avoid spamming generation requests
    private val GENERATION_COOLDOWN = 60.seconds // 1 minute cooldown between auto-generation attempts
    
    // Use a time in the distant past for initial value
    private var lastGenerationAttempt: Instant = Clock.System.now() - (GENERATION_COOLDOWN * 2)
    
    private val hasRecentlyAttemptedGeneration: Boolean
        get() {
            val now = Clock.System.now()
            return (now - lastGenerationAttempt) < GENERATION_COOLDOWN
        }
    
    /**
     * Generates a rewind for the current week.
     * 
     * This method calculates the current week's date range and uses the GenerateBasicRewindUseCase
     * to create a rewind containing relevant notes and media from this period. The resulting
     * rewind is saved to the repository and will be automatically picked up by the UI state flow.
     */
    fun generateCurrentWeekRewind() {
        // Don't generate if already in progress
        if (_isGeneratingRewind.value) {
            Napier.d("Rewind generation already in progress, skipping request")
            return
        }
        
        viewModelScope.launch {
            // Mark as generating
            _isGeneratingRewind.update { true }
            lastGenerationAttempt = Clock.System.now()
            
            try {
                // Calculate time range for the past week
                val endTime = Clock.System.now()
                // 7 days, using proper Duration API
                val oneWeek = 7.days
                val startTime = endTime - oneWeek
                
                Napier.i("Generating rewind for period: $startTime to $endTime")
                
                // Generate the rewind
                when (val result = generateBasicRewindUseCase(startTime, endTime)) {
                    is GenerateBasicRewindResult.Success -> {
                        Napier.i("Successfully generated rewind: ${result.rewind.uid}")
                    }
                    is GenerateBasicRewindResult.AlreadyInProgress -> {
                        Napier.d("Rewind generation already in progress")
                    }
                    is GenerateBasicRewindResult.NoContent -> {
                        Napier.w("No content available for rewind")
                    }
                    is GenerateBasicRewindResult.Error -> {
                        Napier.e("Failed to generate rewind: ${result.error}", result.exception)
                    }
                }
            } catch (e: Exception) {
                Napier.e("Error generating rewind", e)
            } finally {
                // Mark as no longer generating
                _isGeneratingRewind.update { false }
            }
        }
    }
    
    /**
     * Manually triggers rewind generation for the current week.
     * 
     * This method can be called from UI events like a refresh button or pull-to-refresh.
     * It ignores the cooldown period used for automatic generation.
     */
    fun forceGenerateCurrentWeekRewind() {
        // Reset the last attempt time to allow generation regardless of cooldown
        lastGenerationAttempt = Clock.System.now() - (GENERATION_COOLDOWN * 2)
        generateCurrentWeekRewind()
    }
}