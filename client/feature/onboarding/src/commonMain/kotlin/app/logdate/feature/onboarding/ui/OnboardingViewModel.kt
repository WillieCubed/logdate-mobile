package app.logdate.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.billing.model.LogDateBackupPlanOption
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.user.UserStateRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Instant

/**
 * A view model for the onboarding flow.
 */
class OnboardingViewModel(
    private val journalNotesRepository: JournalNotesRepository,
    private val userStateRepository: UserStateRepository,
    private val memoriesSettingsRepository: MemoriesSettingsRepository,
    private val locationTrackingSettingsRepository: LocationTrackingSettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())

    val uiState: StateFlow<OnboardingUiState> =
        _uiState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), OnboardingUiState())

    /**
     * Whether contextual recommendations are currently enabled.
     *
     * Used by the notifications screen to adapt its messaging.
     */
    val recommendationsEnabled: StateFlow<Boolean> =
        memoriesSettingsRepository
            .observeSettings()
            .map { it.contextualRecommendationsEnabled }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

    /**
     * Creates a new journal entry.
     */
    fun addEntry(newEntryData: NewEntryData) {
        viewModelScope.launch {
            // TODO: Support non-text entry types
            journalNotesRepository.create(
                JournalNote.Text(
                    content = newEntryData.textContent,
                    creationTimestamp = newEntryData.timestamp,
                    lastUpdated = newEntryData.timestamp,
                ),
            )
            _uiState.update {
                it.copy(entrySubmitted = true)
            }
            Napier.d(
                tag = "OnboardingViewModel",
                message = "Successfully added entry: $newEntryData",
            )
        }
    }

    /**
     * Sends a test notification to the user.
     *
     * This should be used to confirm how users will be notified for journaling reminders.
     */
    fun sendTestNotification() {
        viewModelScope.launch {
//            notifier.sendNotification(SystemNotification(
//                label = "",
//                bodyContent = "It's time to write.",
//            ))
        }
    }

    fun updateBirthday(birthday: Instant) {
        viewModelScope.launch {
            userStateRepository.setBirthday(birthday)
        }
    }

    /**
     * Enables or disables contextual recommendations.
     */
    fun setRecommendationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            memoriesSettingsRepository.setContextualRecommendationsEnabled(enabled)
        }
    }

    /**
     * Enables background location tracking after the user opts in.
     */
    fun enableLocationTracking() {
        viewModelScope.launch {
            locationTrackingSettingsRepository.setBackgroundTrackingEnabled(true)
        }
    }

    /**
     * Selects a backup plan option.
     */
    fun selectPlan(option: LogDateBackupPlanOption) {
        _uiState.update {
            it.copy(planOption = option)
        }
    }

    fun capturePhoto() {
    }

    /**
     * Marks the onboarding flow as complete.
     */
    fun completeOnboarding() {
        viewModelScope.launch {
            userStateRepository.setIsOnboardingComplete(true)
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}
