package app.logdate.feature.rewind.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.logdate.core.data.rewind.RewindRepository
import app.logdate.feature.rewind.navigation.RewindDetailRoute
import app.logdate.model.Rewind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class RewindDetailViewModel @Inject constructor(
    repository: RewindRepository, // TODO: Use UseCase instead
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val rewindData = savedStateHandle.toRoute<RewindDetailRoute>()

    private val rewindUiState: Flow<Rewind> = repository.getRewind(rewindData.id)

    @OptIn(ExperimentalCoroutinesApi::class)
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
