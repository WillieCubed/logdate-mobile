package app.logdate.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.core.data.user.UserStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    userStateRepository: UserStateRepository,
) : ViewModel() {
    val uiState: StateFlow<LaunchAppUiState> = userStateRepository.userData.map {
        LaunchAppUiState.Loaded(it.isOnboarded)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LaunchAppUiState.Loading
    )
}