@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.domain.rewind.GenerateBasicRewindResult
import app.logdate.client.domain.rewind.GenerateBasicRewindUseCase
import app.logdate.client.domain.rewind.GetPastRewindsUseCase
import app.logdate.client.domain.rewind.GetWeekRewindUseCase
import app.logdate.client.domain.rewind.RewindQueryResult
import app.logdate.client.intelligence.milestones.MilestoneKind
import app.logdate.client.intelligence.milestones.parseMilestoneSignal
import app.logdate.client.intelligence.rewind.RewindMessageGenerator
import app.logdate.shared.model.RewindContent
import app.logdate.util.getLocaleFirstDayOfWeek
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

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
    private val preferencesDataSource: LogdatePreferencesDataSource,
) : ViewModel() {
    // Tracks whether a rewind generation is in progress
    private val isGeneratingRewindState = MutableStateFlow(false)

    private val pastRewinds: StateFlow<List<RewindHistoryUiState>> =
        getPastRewindsUseCase()
            .map { pastRewinds ->
                pastRewinds.map { rewind ->
                    val milestoneSignal = parseMilestoneSignal(rewind.metadata?.milestones?.firstOrNull())
                    RewindHistoryUiState(
                        uid = rewind.uid,
                        title = rewind.title,
                        label = rewind.label,
                        startDate = rewind.startDate.toLocalDateTime(TimeZone.currentSystemDefault()).date,
                        endDate = rewind.endDate.toLocalDateTime(TimeZone.currentSystemDefault()).date,
                        milestone =
                            milestoneSignal?.let {
                                MilestoneSummaryUiState(
                                    kind =
                                        when (it.kind) {
                                            MilestoneKind.LOCATION_CHANGE -> MilestoneKindUiState.LOCATION_CHANGE
                                        },
                                    summary = it.summary,
                                )
                            },
                    )
                }
            }.stateIn(
                viewModelScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = emptyList(),
            )

    private val rewindUiState =
        combine(
            getWeekRewindUseCase(),
            pastRewinds,
            isGeneratingRewindState,
        ) { rewindResult, pastRewinds, isGenerating ->
            when (rewindResult) {
                is RewindQueryResult.Success -> {
                    val rewind = rewindResult.rewind
                    val photoCount = rewind.content.count { it is RewindContent.Image }
                    val textCount = rewind.content.count { it is RewindContent.TextNote }
                    RewindOverviewScreenUiState.Ready(
                        pastRewinds = pastRewinds,
                        mostRecentRewind =
                            RewindPreviewUiState(
                                rewindId = rewind.uid,
                                message =
                                    rewindMessageGenerator.generateContextualMessage(
                                        rewindAvailable = true,
                                        photoCount = photoCount,
                                        textCount = textCount,
                                    ),
                                label = rewind.label,
                                title = rewind.title,
                                start =
                                    rewind.startDate
                                        .toLocalDateTime(TimeZone.currentSystemDefault())
                                        .date,
                                end =
                                    rewind.endDate
                                        .toLocalDateTime(TimeZone.currentSystemDefault())
                                        .date,
                                rewindAvailable = rewind.content.isNotEmpty(),
                                // TODO: Add activity states based on rewind content
                            ),
                    )
                }

                RewindQueryResult.NotReady -> {
                    if (!isGenerating && !hasRecentlyAttemptedGeneration) {
                        generateLastWeekRewind()
                    }

                    RewindOverviewScreenUiState.NotReady(
                        pastRewinds = pastRewinds,
                        isGeneratingRewind = isGenerating,
                    )
                }

                RewindQueryResult.Generating -> {
                    RewindOverviewScreenUiState.NotReady(
                        pastRewinds = pastRewinds,
                        isGeneratingRewind = true,
                    )
                }

                RewindQueryResult.NoneAvailable -> {
                    RewindOverviewScreenUiState.NotReady(
                        pastRewinds = pastRewinds,
                        isGeneratingRewind = false,
                    )
                }
            }
        }.stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = RewindOverviewScreenUiState.Loading,
        )

    val uiState: StateFlow<RewindOverviewScreenUiState> = rewindUiState

    // Track when we last attempted generation to avoid spamming generation requests
    private val generationCooldown = 60.seconds // 1 minute cooldown between auto-generation attempts

    // Use a time in the distant past for initial value
    private var lastGenerationAttempt: Instant = Clock.System.now() - (generationCooldown * 2)

    private val hasRecentlyAttemptedGeneration: Boolean
        get() {
            val now = Clock.System.now()
            return (now - lastGenerationAttempt) < generationCooldown
        }

    /**
     * Generates a rewind for the previous complete week.
     *
     * Uses the same week boundary calculation as [GetWeekRewindUseCase] to ensure
     * the generated rewind is found by subsequent queries.
     */
    fun generateLastWeekRewind() {
        if (isGeneratingRewindState.value) {
            Napier.d("Rewind generation already in progress, skipping request")
            return
        }

        viewModelScope.launch {
            isGeneratingRewindState.update { true }
            lastGenerationAttempt = Clock.System.now()

            try {
                // Previous complete week boundaries, aligned to GetWeekRewindUseCase
                val timezone = TimeZone.currentSystemDefault()
                val today = Clock.System.todayIn(timezone)
                val weekStartDay = preferencesDataSource.getFirstDayOfWeek() ?: getLocaleFirstDayOfWeek()
                val startOfThisWeek =
                    today.minus(
                        (today.dayOfWeek.ordinal - weekStartDay.ordinal + 7) % 7,
                        DateTimeUnit.DAY,
                    )
                val startTime = startOfThisWeek.minus(7, DateTimeUnit.DAY).atStartOfDayIn(timezone)
                val endTime = startOfThisWeek.atStartOfDayIn(timezone)

                Napier.i("Generating rewind for previous week: $startTime to $endTime")

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
                isGeneratingRewindState.update { false }
            }
        }
    }

    /**
     * Manually triggers rewind generation for the previous week.
     *
     * This method can be called from UI events like a refresh button or pull-to-refresh.
     * It ignores the cooldown period used for automatic generation.
     */
    fun forceGenerateLastWeekRewind() {
        lastGenerationAttempt = Clock.System.now() - (generationCooldown * 2)
        generateLastWeekRewind()
    }
}
