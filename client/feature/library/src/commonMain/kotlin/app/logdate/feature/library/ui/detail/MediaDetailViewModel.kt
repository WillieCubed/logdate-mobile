package app.logdate.feature.library.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * ViewModel for the media detail screen.
 *
 * Loads a single image or video note by its ID and exposes its content and metadata.
 */
class MediaDetailViewModel(
    private val noteId: Uuid,
    private val notesRepository: JournalNotesRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MediaDetailUiState>(MediaDetailUiState.Loading)
    val uiState: StateFlow<MediaDetailUiState> = _uiState.asStateFlow()

    init {
        observeNote()
    }

    private fun observeNote() {
        viewModelScope.launch {
            notesRepository.allNotesObserved
                .catch { error ->
                    Napier.e("Failed to load media detail", error)
                    _uiState.value = MediaDetailUiState.Error("Could not load this photo or video.")
                }.collect { notes ->
                    val note = notes.find { it.uid == noteId }
                    if (note == null) {
                        _uiState.value = MediaDetailUiState.Error("Media not found.")
                    } else {
                        updateForNote(note)
                    }
                }
        }
    }

    private fun updateForNote(note: JournalNote) {
        _uiState.value =
            when (note) {
                is JournalNote.Image ->
                    MediaDetailUiState.ImageContent(
                        noteId = note.uid,
                        mediaRef = note.mediaRef,
                        createdAt = note.creationTimestamp,
                        location = note.location,
                    )
                is JournalNote.Video ->
                    MediaDetailUiState.VideoContent(
                        noteId = note.uid,
                        mediaRef = note.mediaRef,
                        createdAt = note.creationTimestamp,
                        location = note.location,
                    )
                else -> MediaDetailUiState.Error("Not a photo or video.")
            }
    }
}
