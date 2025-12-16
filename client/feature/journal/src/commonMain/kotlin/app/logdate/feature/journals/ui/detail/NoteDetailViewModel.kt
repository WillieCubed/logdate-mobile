package app.logdate.feature.journals.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.logdate.client.domain.notes.RemoveNoteUseCase
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.feature.journals.navigation.NoteDetailRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * ViewModel for viewing and managing note details
 */
class NoteDetailViewModel(
    private val notesRepository: JournalNotesRepository,
    private val removeNoteUseCase: RemoveNoteUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<NoteDetailRoute>()
    private val noteId = Uuid.parse(route.noteId)
    private val journalId = Uuid.parse(route.journalId)

    private val _uiState = MutableStateFlow<NoteDetailUiState>(NoteDetailUiState.Loading)
    val uiState: StateFlow<NoteDetailUiState> = _uiState.asStateFlow()

    init {
        loadNote()
    }

    private fun loadNote() {
        viewModelScope.launch {
            notesRepository.allNotesObserved
                .map { notes -> notes.find { it.uid == noteId } }
                .catch { error ->
                    _uiState.value = NoteDetailUiState.Error("Failed to load note: ${error.message}")
                }
                .collect { note ->
                    if (note != null) {
                        _uiState.value = NoteDetailUiState.Success(note)
                    } else {
                        _uiState.value = NoteDetailUiState.Error("Note not found")
                    }
                }
        }
    }

    /**
     * Deletes the current note and calls the callback when complete
     */
    fun deleteNote(onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                removeNoteUseCase(noteId)
                onDeleted()
            } catch (error: Exception) {
                _uiState.value = NoteDetailUiState.Error("Failed to delete note: ${error.message}")
            }
        }
    }
}

/**
 * UI state for the note detail screen
 */
sealed interface NoteDetailUiState {
    data object Loading : NoteDetailUiState
    data class Success(val note: JournalNote) : NoteDetailUiState
    data class Error(val message: String) : NoteDetailUiState
}