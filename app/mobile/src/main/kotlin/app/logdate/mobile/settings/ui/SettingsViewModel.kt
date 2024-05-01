package app.logdate.mobile.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.core.data.user.UserStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A view model for accessing and updating the user's settings.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userStateRepository: UserStateRepository,
) : ViewModel() {
    /**
     * The current state of the user's settings.
     */
    val userDataState: StateFlow<SettingsUiState> = userStateRepository.userData.map { userData ->
        SettingsUiState.Loaded(userData)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SettingsUiState.Loading,
    )

    /**
     * Triggers an app-wide reset.
     */
    fun reset() {
        viewModelScope.launch {
            userStateRepository.setIsOnboardingComplete(false)
        }
    }

    /**
     * Sets the security level for the app.
     */
    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userStateRepository.setBiometricEnabled(enabled)
        }
    }
}