package app.logdate.feature.journals.ui.creation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.feature.journals.navigation.JournalCreationRoute
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

class JournalCreationViewModel(
    private val journalRepository: JournalRepository,
    private val notesRepository: JournalNotesRepository,
    private val journalContentRepository: JournalContentRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val routeData = savedStateHandle.toRoute<JournalCreationRoute>()

    private val backingUiState =
        MutableStateFlow(
            JournalCreationUiState(
                title = routeData.journalTitle,
            ),
        )
    val uiState: StateFlow<JournalCreationUiState> = backingUiState

    /**
     * Recent notes available for adding to the new journal.
     */
    val recentNotes: StateFlow<List<RecentNoteItem>> =
        notesRepository
            .observeRecentNotes(limit = 30)
            .map { notes ->
                notes.map { note ->
                    RecentNoteItem(
                        id = note.uid,
                        textPreview =
                            when (note) {
                                is JournalNote.Text -> note.content.take(100)
                                is JournalNote.Image -> note.caption
                                is JournalNote.Video -> note.caption
                                is JournalNote.Audio -> null
                            },
                        type =
                            when (note) {
                                is JournalNote.Text -> NotePreviewType.TEXT
                                is JournalNote.Image -> NotePreviewType.IMAGE
                                is JournalNote.Video -> NotePreviewType.VIDEO
                                is JournalNote.Audio -> NotePreviewType.AUDIO
                            },
                        timestamp = note.creationTimestamp,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addMediaUris(uris: List<String>) {
        backingUiState.update { state ->
            state.copy(selectedMediaUris = state.selectedMediaUris + uris)
        }
    }

    fun removeMediaUri(uri: String) {
        backingUiState.update { state ->
            state.copy(selectedMediaUris = state.selectedMediaUris - uri)
        }
    }

    fun toggleNoteSelection(noteId: Uuid) {
        backingUiState.update { state ->
            val current = state.selectedNoteIds
            val updated = if (noteId in current) current - noteId else current + noteId
            state.copy(selectedNoteIds = updated)
        }
    }

    fun createJournal(data: NewJournalRequest) {
        viewModelScope.launch {
            val journalId =
                journalRepository.create(
                    Journal(
                        title = data.title,
                        description = data.contentDescription,
                    ),
                )

            // Add selected notes to the new journal
            val selectedIds = backingUiState.value.selectedNoteIds
            for (noteId in selectedIds) {
                journalContentRepository.addContentToJournal(
                    contentId = noteId,
                    journalId = journalId,
                )
            }

            backingUiState.update { currentState ->
                currentState.copy(created = true, journalId = journalId)
            }
        }
    }
}

data class JournalCreationUiState(
    val created: Boolean = false,
    val journalId: Uuid? = null,
    val title: String = "",
    val selectedNoteIds: Set<Uuid> = emptySet(),
    val selectedMediaUris: List<String> = emptyList(),
)

/**
 * A recent note available for selection during journal creation.
 */
data class RecentNoteItem(
    val id: Uuid,
    val textPreview: String?,
    val type: NotePreviewType,
    val timestamp: kotlin.time.Instant,
)

enum class NotePreviewType {
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
}
