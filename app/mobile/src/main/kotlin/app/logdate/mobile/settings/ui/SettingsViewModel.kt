package app.logdate.mobile.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.core.data.user.UserStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userStateRepository: UserStateRepository,
) : ViewModel() {
    /**
     * Triggers an app-wide reset.
     */
    fun reset() {
        viewModelScope.launch {
            userStateRepository.setIsOnboardingComplete(false)
        }
    }
}