package app.logdate.feature.journals.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.notes.RemoveNoteUseCase
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.NoteLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * ViewModel that loads a note and exposes a type-safe viewer state.
 */
class NoteViewerViewModel(
    private val noteId: Uuid,
    private val notesRepository: JournalNotesRepository,
    private val removeNoteUseCase: RemoveNoteUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<NoteViewerUiState>(NoteViewerUiState.Loading)
    val uiState: StateFlow<NoteViewerUiState> = _uiState.asStateFlow()

    init {
        observeNote()
    }

    private fun observeNote() {
        viewModelScope.launch {
            notesRepository.allNotesObserved
                .catch { error ->
                    _uiState.value = NoteViewerUiState.Error(
                        "Failed to load note: ${error.message}"
                    )
                }
                .collect { notes ->
                    val note = notes.find { it.uid == noteId }
                    if (note == null) {
                        _uiState.value = NoteViewerUiState.Error("Note not found")
                    } else {
                        updateForNote(note)
                    }
                }
        }
    }

    private fun updateForNote(note: JournalNote) {
        val shared = NoteViewerShared(
            noteId = note.uid,
            createdAt = note.creationTimestamp,
            lastUpdated = note.lastUpdated,
            location = note.location,
        )
        when (note) {
            is JournalNote.Text -> {
                _uiState.value = NoteViewerUiState.TextContent(shared, note.content)
            }
            is JournalNote.Image -> {
                _uiState.value = NoteViewerUiState.ImageContent(shared, note.mediaRef)
            }
            is JournalNote.Video -> {
                _uiState.value = NoteViewerUiState.VideoContent(shared, note.mediaRef)
            }
            is JournalNote.Audio -> {
                _uiState.value = NoteViewerUiState.AudioContent(
                    shared = shared,
                    mediaRef = note.mediaRef,
                    durationMs = note.durationMs,
                )
            }
        }
    }

    /**
     * Deletion request for the current note.
     */
    fun deleteNote(onDeleted: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                removeNoteUseCase(noteId)
            }.onSuccess {
                onDeleted()
            }.onFailure { error ->
                _uiState.value = NoteViewerUiState.Error("Failed to delete note: ${error.message}")
            }
        }
    }
}

/**
 * Shared metadata for note viewer presentations.
 */
data class NoteViewerShared(
    val noteId: Uuid,
    val createdAt: Instant,
    val lastUpdated: Instant,
    val location: NoteLocation?,
)

/**
 * Type-safe UI state for note viewing.
 */
sealed interface NoteViewerUiState {
    data object Loading : NoteViewerUiState
    data class Error(val message: String) : NoteViewerUiState

    /**
     * Base contract for content-bearing viewer states.
     */
    sealed interface Content : NoteViewerUiState {
        val shared: NoteViewerShared
    }

    /**
     * Content state for text notes.
     */
    data class TextContent(
        override val shared: NoteViewerShared,
        val text: String,
    ) : Content

    /**
     * Content state for image notes.
     */
    data class ImageContent(
        override val shared: NoteViewerShared,
        val mediaRef: String,
    ) : Content

    /**
     * Content state for video notes.
     */
    data class VideoContent(
        override val shared: NoteViewerShared,
        val mediaRef: String,
    ) : Content

    /**
     * Content state for audio notes.
     */
    data class AudioContent(
        override val shared: NoteViewerShared,
        val mediaRef: String,
        val durationMs: Long,
    ) : Content
}
