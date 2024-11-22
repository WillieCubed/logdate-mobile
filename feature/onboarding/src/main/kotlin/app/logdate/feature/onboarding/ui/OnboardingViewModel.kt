package app.logdate.feature.onboarding.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.core.billing.model.BackupPlanOption
import app.logdate.core.data.notes.JournalNote
import app.logdate.core.data.notes.JournalNotesRepository
import app.logdate.core.data.user.UserStateRepository
import app.logdate.core.notifications.Notifier
import app.logdate.core.notifications.SystemNotification
import app.logdate.feature.onboarding.editor.AudioEntryRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import javax.inject.Inject

/**
 * A view model for the onboarding flow.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val journalNotesRepository: JournalNotesRepository,
    private val audioRecorder: AudioEntryRecorder, // TODO: Find a memory-safe solution for this
    private val userStateRepository: UserStateRepository,
    private val notifier: Notifier,
) : ViewModel() {

    private val _recorderState = MutableStateFlow(AudioRecorderUiState())
    private val _uiState = MutableStateFlow(OnboardingUiState())

    val uiState: StateFlow<OnboardingUiState> =
        _uiState.combine(_recorderState) { uiState, recorderState ->
            OnboardingUiState(
                entrySubmitted = uiState.entrySubmitted,
                planOption = uiState.planOption,
                newEntryData = uiState.newEntryData.copy(
                    recorderState = recorderState,
                )
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), OnboardingUiState())

    /**
     * Creates a new journal entry.
     */
    fun addEntry(newEntryData: NewEntryData) {
        viewModelScope.launch {
            // TODO: Support non-text entry types
            journalNotesRepository.create(
                JournalNote.Text(
                    uid = "",
                    content = newEntryData.textContent,
                    creationTimestamp = newEntryData.timestamp,
                    lastUpdated = newEntryData.timestamp,
                )
            )
            _uiState.update {
                it.copy(entrySubmitted = true)
            }
            Log.d("OnboardingViewModel", "Successfully added entry: $newEntryData")
        }
    }

    /**
     * Sends a test notification to the user.
     *
     * This should be used to confirm how users will be notified for journaling reminders.
     */
    fun sendTestNotification() {
        viewModelScope.launch {
            notifier.sendNotification(SystemNotification(
                label = "",
                bodyContent = "It's time to write.",
            ))
        }
    }

    fun updateBirthday(birthday: Instant) {
        viewModelScope.launch {
            userStateRepository.setBirthday(birthday)
        }
    }

    /**
     * Selects a backup plan option.
     */
    fun selectPlan(option: BackupPlanOption) {
        _uiState.update {
            it.copy(planOption = option)
        }
    }

    /**
     * Starts recording audio.
     */
    fun startRecordingAudio(filename: String) {
        audioRecorder.startRecording(filename)
        _uiState.update {
            it.copy(
                newEntryData = it.newEntryData.copy(
                    recorderState = AudioRecorderUiState(isRecording = true)
                )
            )
        }
    }

    /**
     * Stops recording audio.
     */
    fun stopRecordingAudio() {
        audioRecorder.stopRecording()
        _uiState.update {
            it.copy(
                newEntryData = it.newEntryData.copy(
                    recorderState = AudioRecorderUiState(isRecording = false)
                )
            )
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
        audioRecorder.cancelRecording()
    }
}

