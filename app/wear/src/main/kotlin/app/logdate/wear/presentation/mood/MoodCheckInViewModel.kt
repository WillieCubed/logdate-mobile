package app.logdate.wear.presentation.mood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.SystemCaptureTimeZone
import app.logdate.wear.capture.Feeling
import app.logdate.wear.capture.MomentNoteCodec
import app.logdate.wear.capture.WatchCaptureWriteGuard
import app.logdate.wear.location.WearLocationCaptureCoordinator
import app.logdate.wear.presentation.common.SaveFeedback
import app.logdate.wear.sync.WearDataLayerClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
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

enum class MoodCheckInStep {
    SELECT_MOOD,
    VOICE_PROMPT,
    SAVED,
}

data class MoodCheckInUiState(
    val step: MoodCheckInStep = MoodCheckInStep.SELECT_MOOD,
    val selectedMood: Feeling? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val saveFailed: Boolean = false,
    val saveFeedback: SaveFeedback? = null,
)

sealed interface MoodCheckInEvent {
    data object NavigateBack : MoodCheckInEvent

    data object NavigateToVoiceNote : MoodCheckInEvent
}

class MoodCheckInViewModel(
    private val notesRepository: JournalNotesRepository,
    private val dataLayerClient: WearDataLayerClient,
    private val locationCaptureCoordinator: WearLocationCaptureCoordinator,
    private val captureGuard: WatchCaptureWriteGuard = WatchCaptureWriteGuard.Unrestricted,
) : ViewModel() {
    companion object {
        private const val SAVED_DISPLAY_MS = 800L
    }

    private val _uiState = MutableStateFlow(MoodCheckInUiState())
    val uiState: StateFlow<MoodCheckInUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MoodCheckInEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<MoodCheckInEvent> = _events.asSharedFlow()

    fun selectMood(mood: Feeling) {
        _uiState.update {
            it.copy(
                selectedMood = mood,
                step = MoodCheckInStep.VOICE_PROMPT,
            )
        }
    }

    fun skipVoiceAttachment() {
        val mood = _uiState.value.selectedMood ?: return
        if (_uiState.value.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveFailed = false) }
            val feedback = saveMood(mood)
            if (feedback == null) {
                _uiState.update { it.copy(isSaving = false, saveFailed = true) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    step = MoodCheckInStep.SAVED,
                    isSaving = false,
                    isSaved = true,
                    saveFeedback = feedback,
                )
            }
            delay(SAVED_DISPLAY_MS)
            _events.emit(MoodCheckInEvent.NavigateBack)
        }
    }

    /** Saves the mood, then opens the recorder so a voice note can follow it. */
    fun attachVoice() {
        val mood = _uiState.value.selectedMood ?: return
        if (_uiState.value.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveFailed = false) }
            val feedback = saveMood(mood)
            _uiState.update { it.copy(isSaving = false, saveFailed = feedback == null) }
            if (feedback == null) return@launch
            _events.emit(MoodCheckInEvent.NavigateToVoiceNote)
        }
    }

    /** Saves [mood] as a check-in note. Returns where it went, or null when it could not be saved. */
    private suspend fun saveMood(mood: Feeling): SaveFeedback? =
        try {
            val now = Clock.System.now()
            val noteLocation = locationCaptureCoordinator.captureForJournalEntry()
            captureGuard.write {
                notesRepository.create(
                    JournalNote.Text(
                        content = MomentNoteCodec.encode(mood),
                        uid = Uuid.random(),
                        creationTimestamp = now,
                        lastUpdated = now,
                        location = noteLocation,
                        timeZoneId = SystemCaptureTimeZone.currentTimeZoneId(),
                    ),
                )
            }
            Napier.d("Mood check-in saved: ${mood.tag}")
            if (dataLayerClient.isPhoneConnected()) SaveFeedback.SYNCING_TO_PHONE else SaveFeedback.SAVED_LOCALLY
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Failed to save mood check-in", e)
            null
        }
}
