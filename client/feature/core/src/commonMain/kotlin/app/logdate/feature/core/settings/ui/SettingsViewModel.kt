package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.journals.ExportableJournalContentRepository
import app.logdate.client.repository.user.UserStateRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * A view model for accessing and updating the user's settings.
 */
class SettingsViewModel(
    private val userStateRepository: UserStateRepository,
    private val exportableJournalContentRepository: ExportableJournalContentRepository,
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

    fun exportContent() {
        viewModelScope.launch {
            val timestamp = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .format()
            val filename = "export_$timestamp.json"
            exportableJournalContentRepository.exportContentToFile(filename)
        }
    }
}

// Helper extension function to format LocalDateTime in a consistent way across platforms
private fun LocalDateTime.format(): String = "${year.toString().padStart(4, '0')}-" +
        "${monthNumber.toString().padStart(2, '0')}-" +
        "${dayOfMonth.toString().padStart(2, '0')}T" +
        "${hour.toString().padStart(2, '0')}:" +
        "${minute.toString().padStart(2, '0')}:" +
        second.toString().padStart(2, '0')