package app.logdate.feature.timeline.ui

import app.logdate.ui.timeline.HomeTimelineUiState
import app.logdate.ui.timeline.TimelineSuggestionBlock
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart

internal fun Flow<HomeTimelineUiState>.withOptionalTimelineSuggestion(
    suggestions: Flow<TimelineSuggestionBlock?>,
): Flow<HomeTimelineUiState> =
    combine(
        suggestions.onStart { emit(null) }.catch { failure ->
            if (failure is CancellationException) throw failure
            Napier.w("Timeline recommendations are unavailable; retained entries remain accessible")
            emit(null)
        },
    ) { state, suggestion -> state.copy(timelineSuggestion = suggestion) }
