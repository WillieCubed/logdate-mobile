package app.logdate.feature.journals.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.sharing.SharingLauncher
import app.logdate.feature.journals.navigation.JournalDetailsRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * ViewModel for interacting with journal details
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JournalDetailViewModel(
    private val repository: JournalRepository,
    private val sharingLauncher: SharingLauncher,
    journalContentRepository: JournalContentRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Simple MutableStateFlow to store the journal ID
    private val _journalId = MutableStateFlow<Uuid?>(null)
    
    // Current sort order preference
    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST_FIRST)

    // Try to initialize from route if possible
    init {
        try {
            val routeData = savedStateHandle.toRoute<JournalDetailsRoute>()
            routeData.journalId?.let { idString ->
                try {
                    _journalId.value = Uuid.parse(idString)
                } catch (e: Exception) {
                    // Invalid UUID in route, leave as null
                }
            }
        } catch (e: Exception) {
            // No route data, leave as null
        }
    }

    // Journal contents based on the current journal ID
    private val _journalContents = _journalId
        .filterNotNull()
        .flatMapLatest { journalId ->
            journalContentRepository.observeContentForJournal(journalId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI state combines journal data with contents and sort order
    val uiState: StateFlow<JournalDetailUiState> = _journalId
        .flatMapLatest { journalId ->
            if (journalId == null) {
                return@flatMapLatest flowOf(JournalDetailUiState.Loading)
            }
            
            repository.observeJournalById(journalId)
                .combine(_journalContents) { journal, notes ->
                    journal to notes
                }
                .combine(_sortOrder) { journalToNotes, sortOrder ->
                    Triple(journalToNotes.first, journalToNotes.second, sortOrder)
                }
                .map { (journal, notes, sortOrder) ->
                    val displayData = notes.map { it.toDisplayData() }
                    
                    // Sort based on the current sort order
                    val sortedEntries = when (sortOrder) {
                        SortOrder.NEWEST_FIRST -> displayData.sortedByDescending { it.timestamp }
                        SortOrder.OLDEST_FIRST -> displayData.sortedBy { it.timestamp }
                    }
                    
                    JournalDetailUiState.Success(
                        journal.id,
                        journal.title,
                        sortedEntries,
                        sortOrder
                    )
                }
        }
        .catch { e ->
            emit(JournalDetailUiState.Error(
                "error",
                "Could not load this journal: ${e.message}"
            ))
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            JournalDetailUiState.Loading
        )
        
    /**
     * Toggles the sort order between newest first and oldest first
     */
    fun toggleSortOrder() {
        _sortOrder.update { currentOrder ->
            when (currentOrder) {
                SortOrder.NEWEST_FIRST -> SortOrder.OLDEST_FIRST
                SortOrder.OLDEST_FIRST -> SortOrder.NEWEST_FIRST
            }
        }
    }
        
    /**
     * Converts a JournalNote to an EntryDisplayData
     */
    private fun JournalNote.toDisplayData(): EntryDisplayData {
        val content = when (this) {
            is JournalNote.Text -> content
            is JournalNote.Image -> "Image"
            is JournalNote.Video -> "Video"
            is JournalNote.Audio -> "Audio"
        }
        return EntryDisplayData(uid, content, creationTimestamp)
    }

    /**
     * Sets the selected journal ID.
     * This is the primary way to set which journal to display.
     */
    fun setSelectedJournalId(id: Uuid) {
        _journalId.value = id
    }

    /**
     * Shares the current journal.
     */
    fun shareCurrentJournal() {
        viewModelScope.launch {
            _journalId.value?.let { journalId ->
                sharingLauncher.shareJournalToInstagram(journalId)
            }
        }
    }

    /**
     * Deletes the current journal and triggers the onDelete callback.
     */
    fun deleteJournal(onDelete: () -> Unit) {
        viewModelScope.launch {
            _journalId.value?.let { journalId ->
                repository.delete(journalId)
                onDelete()
            }
        }
    }
}