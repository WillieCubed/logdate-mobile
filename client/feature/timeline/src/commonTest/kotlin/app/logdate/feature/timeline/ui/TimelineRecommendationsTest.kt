package app.logdate.feature.timeline.ui

import app.logdate.ui.timeline.HomeTimelineUiState
import app.logdate.ui.timeline.TimelineDayUiState
import app.logdate.ui.timeline.TimelineSuggestionBlock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineRecommendationsTest {
    @Test
    fun `recommendation cancellation still cancels its collector`() =
        runTest {
            val collector =
                backgroundScope.launch {
                    flowOf(HomeTimelineUiState())
                        .withOptionalTimelineSuggestion(
                            flow { throw CancellationException("Screen closed") },
                        ).collect {}
                }
            runCurrent()
            assertTrue(collector.isCancelled)
        }

    @Test
    fun `a pending recommendation cannot hide the restored timeline`() =
        runTest {
            val timeline = HomeTimelineUiState(items = listOf(TimelineDayUiState(summary = "Retained journal")))
            val observed = mutableListOf<HomeTimelineUiState>()
            backgroundScope.launch {
                flowOf(timeline).withOptionalTimelineSuggestion(flow { awaitCancellation() }).collect { observed += it }
            }
            runCurrent()
            assertEquals(listOf(timeline), observed)
        }

    @Test
    fun `failed recommendations leave subsequent local edits visible`() =
        runTest {
            val original = HomeTimelineUiState(items = listOf(TimelineDayUiState(summary = "Retained journal")))
            val timeline = MutableStateFlow(original)
            val observed = mutableListOf<HomeTimelineUiState>()
            backgroundScope.launch {
                timeline.withOptionalTimelineSuggestion(flow { error("Optional lookup failed") }).collect { observed += it }
            }
            runCurrent()
            val edited = original.copy(items = listOf(TimelineDayUiState(summary = "Edited offline")))
            timeline.value = edited
            runCurrent()
            assertEquals(edited, observed.lastOrNull())
        }

    @Test
    fun `late recommendations preserve the visible restored entries`() =
        runTest {
            val timeline = HomeTimelineUiState(items = listOf(TimelineDayUiState(summary = "Retained journal")))
            val suggestions = MutableStateFlow<TimelineSuggestionBlock?>(null)
            val observed = mutableListOf<HomeTimelineUiState>()
            backgroundScope.launch {
                flowOf(timeline).withOptionalTimelineSuggestion(suggestions).collect { observed += it }
            }
            runCurrent()
            suggestions.value = TimelineSuggestionBlock.MorningStart
            runCurrent()
            assertEquals(timeline.copy(timelineSuggestion = TimelineSuggestionBlock.MorningStart), observed.last())
        }
}
