package app.logdate.wear.presentation.mood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.presentation.common.SaveFeedback
import app.logdate.wear.sync.WearDataLayerClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.Uuid

enum class MoodOption(val emoji: String, val label: String, val tag: String) {
    GREAT("\uD83D\uDE04", "Great", "great"),
    GOOD("\uD83D\uDE42", "Good", "good"),
    OK("\uD83D\uDE10", "OK", "ok"),
    SAD("\uD83D\uDE14", "Sad", "sad"),
    ROUGH("\uD83D\uDE30", "Rough", "rough"),
}

enum class MoodCheckInStep {
    SELECT_MOOD,
    VOICE_PROMPT,
    SAVED,
}

data class MoodCheckInUiState(
    val step: MoodCheckInStep = MoodCheckInStep.SELECT_MOOD,
    val selectedMood: MoodOption? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val saveFeedback: SaveFeedback? = null,
)

sealed interface MoodCheckInEvent {
    data object NavigateBack : MoodCheckInEvent
    data object NavigateToVoiceNote : MoodCheckInEvent
}

class MoodCheckInViewModel(
    private val notesRepository: JournalNotesRepository,
    private val dataLayerClient: WearDataLayerClient,
) : ViewModel() {

    companion object {
        private const val SAVED_DISPLAY_MS = 800L
    }

    private val _uiState = MutableStateFlow(MoodCheckInUiState())
    val uiState: StateFlow<MoodCheckInUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MoodCheckInEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<MoodCheckInEvent> = _events.asSharedFlow()

    fun selectMood(mood: MoodOption) {
        _uiState.update {
            it.copy(
                selectedMood = mood,
                step = MoodCheckInStep.VOICE_PROMPT,
            )
        }
    }

    fun skipVoiceAttachment() {
        val mood = _uiState.value.selectedMood ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            try {
                val now = Clock.System.now()
                val note = JournalNote.Text(
                    content = "#mood:${mood.tag} Feeling ${mood.label.lowercase()}",
                    uid = Uuid.random(),
                    creationTimestamp = now,
                    lastUpdated = now,
                )
                notesRepository.create(note)

                val feedback = if (dataLayerClient.isPhoneConnected()) {
                    SaveFeedback.SYNCING_TO_PHONE
                } else {
                    SaveFeedback.SAVED_LOCALLY
                }

                _uiState.update {
                    it.copy(
                        step = MoodCheckInStep.SAVED,
                        isSaving = false,
                        isSaved = true,
                        saveFeedback = feedback,
                    )
                }

                Napier.d("Mood check-in saved: ${mood.tag}")

                delay(SAVED_DISPLAY_MS)
                _events.emit(MoodCheckInEvent.NavigateBack)
            } catch (e: Exception) {
                Napier.e("Failed to save mood check-in", e)
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun attachVoice() {
        _events.tryEmit(MoodCheckInEvent.NavigateToVoiceNote)
    }
}
