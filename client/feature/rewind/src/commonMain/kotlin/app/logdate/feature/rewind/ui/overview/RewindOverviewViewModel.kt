package app.logdate.feature.rewind.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.intelligence.rewind.RewindMessageGenerator
import app.logdate.client.domain.rewind.GetPastRewindsUseCase
import app.logdate.client.domain.rewind.GetWeekRewindUseCase
import app.logdate.client.domain.rewind.RewindQueryResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class RewindOverviewViewModel (
    getWeekRewindUseCase: GetWeekRewindUseCase,
    getPastRewindsUseCase: GetPastRewindsUseCase,
    rewindMessageGenerator: RewindMessageGenerator,
) : ViewModel() {
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

    private val rewindUiState: StateFlow<RewindOverviewScreenUiState> = getWeekRewindUseCase()
        .combine(pastRewinds) { rewind, pastRewinds ->
            when (rewind) {
                is RewindQueryResult.Success -> {
                    RewindOverviewScreenUiState.Loaded(
                        pastRewinds = pastRewinds,
                        mostRecentRewind = FocusRewindUiState(
                            rewindId = rewind.rewind.uid,
                            message = rewindMessageGenerator.generateMessage(true),
                            rewindAvailable = true,
                        ),
                    )
                }

                RewindQueryResult.NotReady -> {
                    RewindOverviewScreenUiState.NotReady(
                        pastRewinds = pastRewinds,
                    )
                }

                else -> RewindOverviewScreenUiState.Loading
            }
        }
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = RewindOverviewScreenUiState.Loading,
        )

    val uiState: StateFlow<RewindOverviewScreenUiState> = rewindUiState
}