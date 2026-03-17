package app.logdate.wear.presentation.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.media.audio.AudioPlaybackManager
import app.logdate.client.media.audio.AudioPlaybackMetadata
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.playback.AudioOutputState
import app.logdate.wear.playback.WearAudioOutputMonitor
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.Uuid

data class WearTimelineDayUiState(
    val date: LocalDate,
    val entryCount: Int,
    val latestMood: String? = null,
    val previewText: String? = null,
)

data class WearTimelineUiState(
    val days: List<WearTimelineDayUiState> = emptyList(),
    val isLoading: Boolean = false,
)

data class WearDayDetailUiState(
    val date: LocalDate,
    val entries: List<JournalNote>,
)

/**
 * Playback state for voice note inline controls.
 */
sealed interface WearPlaybackUiState {
    data object Idle : WearPlaybackUiState
    data class Active(
        val noteId: Uuid,
        val progress: Float,
        val durationMs: Long,
    ) : WearPlaybackUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class WearTimelineViewModel(
    private val notesRepository: JournalNotesRepository,
    private val audioPlaybackManager: AudioPlaybackManager,
    private val audioOutputMonitor: WearAudioOutputMonitor,
) : ViewModel() {

    val uiState: StateFlow<WearTimelineUiState> =
        notesRepository.observeRecentNotes()
            .map { notes -> groupNotesIntoDays(notes) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, WearTimelineUiState())

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)

    val selectedDayState: StateFlow<WearDayDetailUiState?> =
        _selectedDate
            .flatMapLatest { date ->
                if (date == null) {
                    flowOf(null)
                } else {
                    notesRepository.observeNotesForDay(date).map { notes ->
                        WearDayDetailUiState(date = date, entries = notes)
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _playbackState = MutableStateFlow<WearPlaybackUiState>(WearPlaybackUiState.Idle)
    val playbackState: StateFlow<WearPlaybackUiState> = _playbackState

    val audioOutputState: StateFlow<AudioOutputState> = audioOutputMonitor.outputState

    /**
     * Toggles playback: plays the note if idle or a different note is active,
     * stops if this note is already playing.
     */
    fun toggleNote(note: JournalNote.Audio) {
        val current = _playbackState.value
        if (current is WearPlaybackUiState.Active && current.noteId == note.uid) {
            stopPlayback()
            return
        }

        if (audioOutputMonitor.outputState.value is AudioOutputState.Unavailable) {
            Napier.w { "No audio output available, cannot play note" }
            return
        }

        // Stop any other active playback first
        if (current is WearPlaybackUiState.Active) {
            audioPlaybackManager.stopPlayback()
        }

        _playbackState.value = WearPlaybackUiState.Active(
            noteId = note.uid,
            progress = 0f,
            durationMs = note.durationMs,
        )

        audioPlaybackManager.startPlayback(
            uri = note.mediaRef,
            metadata = AudioPlaybackMetadata(noteId = note.uid),
            onProgressUpdated = { progress ->
                val state = _playbackState.value
                if (state is WearPlaybackUiState.Active && state.noteId == note.uid) {
                    _playbackState.value = state.copy(progress = progress)
                }
            },
            onPlaybackCompleted = {
                _playbackState.value = WearPlaybackUiState.Idle
            },
        )
    }

    /**
     * Stops playback and resets to idle.
     */
    fun stopPlayback() {
        audioPlaybackManager.stopPlayback()
        _playbackState.value = WearPlaybackUiState.Idle
    }

    /**
     * Opens Bluetooth settings so the user can connect audio devices.
     */
    fun openBluetoothSettings() {
        audioOutputMonitor.launchBluetoothSettings()
    }

    fun selectDay(date: LocalDate) {
        _selectedDate.value = date
    }

    fun clearSelection() {
        _selectedDate.value = null
    }

    override fun onCleared() {
        super.onCleared()
        audioPlaybackManager.release()
        audioOutputMonitor.unregister()
    }

    private fun groupNotesIntoDays(notes: List<JournalNote>): WearTimelineUiState {
        if (notes.isEmpty()) {
            return WearTimelineUiState(days = emptyList(), isLoading = false)
        }

        val timezone = TimeZone.currentSystemDefault()
        val grouped = notes.groupBy { note ->
            note.creationTimestamp.toLocalDateTime(timezone).date
        }

        val days = grouped
            .map { (date, dayNotes) ->
                WearTimelineDayUiState(
                    date = date,
                    entryCount = dayNotes.size,
                    latestMood = extractMood(dayNotes),
                    previewText = extractPreview(dayNotes),
                )
            }
            .sortedByDescending { it.date }

        return WearTimelineUiState(days = days, isLoading = false)
    }

    private fun extractMood(notes: List<JournalNote>): String? {
        val moodPattern = Regex("^#mood:(\\w+)")
        for (note in notes) {
            if (note is JournalNote.Text) {
                val match = moodPattern.find(note.content)
                if (match != null) {
                    return match.groupValues[1]
                }
            }
        }
        return null
    }

    private fun extractPreview(notes: List<JournalNote>): String? {
        val firstText = notes.firstOrNull { it is JournalNote.Text } as? JournalNote.Text
            ?: return null
        return firstText.content.take(50)
    }
}
