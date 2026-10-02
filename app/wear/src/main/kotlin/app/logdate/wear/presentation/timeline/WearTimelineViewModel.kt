package app.logdate.wear.presentation.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.datalayer.WearAudioRequestPaths
import app.logdate.wear.playback.AudioOutputState
import app.logdate.wear.playback.WearVoiceNotePlayer
import app.logdate.wear.sync.WearDataLayerClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    data class Preparing(
        val noteId: Uuid,
    ) : WearPlaybackUiState

    data class Active(
        val noteId: Uuid,
        val progress: Float,
        val durationMs: Long,
        val isPaused: Boolean = false,
    ) : WearPlaybackUiState

    data class BlockedOutput(
        val noteId: Uuid,
    ) : WearPlaybackUiState

    data class Error(
        val noteId: Uuid,
    ) : WearPlaybackUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class WearTimelineViewModel(
    private val notesRepository: JournalNotesRepository,
    playerFactory: (CoroutineScope) -> WearVoiceNotePlayer,
    private val dataLayerClient: WearDataLayerClient,
) : ViewModel() {
    private val player = playerFactory(viewModelScope)

    val uiState: StateFlow<WearTimelineUiState> =
        notesRepository
            .observeRecentNotes()
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
            }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val playbackState: StateFlow<WearPlaybackUiState> = player.state

    val audioOutputState: StateFlow<AudioOutputState> = player.outputState

    init {
        viewModelScope.launch {
            val requested = dataLayerClient.sendMessage(WearAudioRequestPaths.SYNC_REQUEST_PATH)
            if (!requested) {
                Napier.w { "Failed to request phone note sync from Wear timeline" }
            }
        }
    }

    /** Plays [note], or stops it when it is the note already playing or being prepared. */
    fun toggleNote(note: JournalNote.Audio) {
        val current = player.state.value
        val isThisNote = current.noteIdOrNull() == note.uid
        if (isThisNote && (current is WearPlaybackUiState.Active || current is WearPlaybackUiState.Preparing)) {
            player.stop()
            return
        }
        player.play(note)
    }

    fun stopPlayback() = player.stop()

    fun openBluetoothSettings() = player.openBluetoothSettings()

    fun selectDay(date: LocalDate) {
        _selectedDate.value = date
    }

    fun clearSelection() {
        _selectedDate.value = null
    }

    override fun onCleared() {
        player.stop()
        super.onCleared()
    }

    private fun groupNotesIntoDays(notes: List<JournalNote>): WearTimelineUiState {
        if (notes.isEmpty()) {
            return WearTimelineUiState(days = emptyList(), isLoading = false)
        }

        val timezone = TimeZone.currentSystemDefault()
        val grouped =
            notes.groupBy { note ->
                note.creationTimestamp.toLocalDateTime(timezone).date
            }

        val days =
            grouped
                .map { (date, dayNotes) ->
                    WearTimelineDayUiState(
                        date = date,
                        entryCount = dayNotes.size,
                        latestMood = extractMood(dayNotes),
                        previewText = extractPreview(dayNotes),
                    )
                }.sortedByDescending { it.date }

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
        val firstText =
            notes.firstOrNull { it is JournalNote.Text } as? JournalNote.Text
                ?: return null
        return firstText.content.take(50)
    }
}

private fun WearPlaybackUiState.noteIdOrNull(): Uuid? =
    when (this) {
        is WearPlaybackUiState.Active -> noteId
        is WearPlaybackUiState.BlockedOutput -> noteId
        is WearPlaybackUiState.Error -> noteId
        is WearPlaybackUiState.Preparing -> noteId
        WearPlaybackUiState.Idle -> null
    }
