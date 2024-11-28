package app.logdate.feature.rewind.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.logdate.client.repository.rewind.RewindRepository
import app.logdate.feature.rewind.navigation.RewindDetailRoute
import app.logdate.shared.model.Rewind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn


class RewindDetailViewModel(
    repository: RewindRepository, // TODO: Use UseCase instead
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val rewindData = savedStateHandle.toRoute<RewindDetailRoute>()

    private val rewindUiState: Flow<Rewind> = repository.getRewind(rewindData.id)

    val uiState: StateFlow<RewindDetailUiState> =
        rewindUiState
            .map {
                // TODO: Populate panels
                RewindDetailUiState.Success()
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                RewindDetailUiState.Loading
            )
}
