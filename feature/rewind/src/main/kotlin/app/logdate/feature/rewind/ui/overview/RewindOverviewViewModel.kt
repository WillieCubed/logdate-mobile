package app.logdate.feature.rewind.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.feature.rewind.data.RewindMessageGenerator
import app.logdate.feature.rewind.domain.GetPastRewindsUseCase
import app.logdate.feature.rewind.domain.GetWeekRewindUseCase
import app.logdate.feature.rewind.domain.RewindQueryResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class RewindOverviewViewModel @Inject constructor(
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