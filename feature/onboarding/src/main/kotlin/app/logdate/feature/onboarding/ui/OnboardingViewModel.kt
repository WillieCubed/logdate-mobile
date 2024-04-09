package app.logdate.feature.onboarding.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.core.billing.model.BackupPlanOption
import app.logdate.core.data.notes.JournalNote
import app.logdate.core.data.notes.JournalNotesRepository
import app.logdate.core.data.user.UserStateRepository
import app.logdate.feature.onboarding.editor.AudioEntryRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val journalNotesRepository: JournalNotesRepository,
    private val audioRecorder: AudioEntryRecorder, // TODO: Find a memory-safe solution for this
    private val userStateRepository: UserStateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())

    val uiState: StateFlow<OnboardingUiState> = _uiState

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
                    creationTimestamp = newEntryData.recordedTimestamp,
                    lastUpdated = newEntryData.recordedTimestamp,
                )
            )
            _uiState.update {
                it.copy(entrySubmitted = true)
            }
            Log.d("OnboardingViewModel", "Successfully added entry: $newEntryData")
        }
    }

    fun selectPlan(option: BackupPlanOption) {
        _uiState.update {
            it.copy(planOption = option)
        }
    }

    fun startRecordingAudio(filename: String) {
        audioRecorder.startRecording(filename)
        _uiState.update {
            it.copy(newEntryData = it.newEntryData.copy(isRecordingAudio = true))
        }
    }

    fun stopRecordingAudio() {
        audioRecorder.stopRecording()
        _uiState.update {
            it.copy(newEntryData = it.newEntryData.copy(isRecordingAudio = false))
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

