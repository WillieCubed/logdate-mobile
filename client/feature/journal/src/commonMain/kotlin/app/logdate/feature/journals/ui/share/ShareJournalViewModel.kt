package app.logdate.feature.journals.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.sharing.SharingLauncher
import app.logdate.client.sharing.ShareTheme
import app.logdate.shared.model.Journal
import app.logdate.util.toReadableDateShort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.uuid.Uuid

/**
 * ViewModel for the journal sharing screen.
 *
 * Manages loading journal data and providing sharing functionality.
 *
 * @param journalRepository Repository for accessing journal data
 * @param sharingLauncher Platform-specific implementation to handle sharing
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShareJournalViewModel(
    private val journalRepository: JournalRepository,
    private val sharingLauncher: SharingLauncher,
) : ViewModel() {
    private val _error = MutableStateFlow<String?>(null)
    
    /**
     * Error state for handling sharing failures
     */
    val error: StateFlow<String?> = _error
    
    // Journal ID to share
    private val _journalId = MutableStateFlow<Uuid?>(null)

    /**
     * UI state for the share journal screen
     */
    val uiState: StateFlow<ShareJournalUiState> = _journalId
        .filterNotNull()
        .flatMapLatest { journalId ->
            journalRepository.observeJournalById(journalId)
                .map { journal ->
                    ShareJournalUiState.Success(
                        journal = journal,
                        lastUpdatedDisplay = "Last updated ${journal.lastUpdated.toReadableDateShort()}"
                    ) as ShareJournalUiState
                }
        }
        .catch { error ->
            _error.value = error.message
            emit(ShareJournalUiState.Error)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ShareJournalUiState.Loading
        )
        
    /**
     * Sets the journal ID to be shared
     *
     * @param id The UUID of the journal to share
     */
    fun setJournalId(id: Uuid) {
        _journalId.value = id
    }

    /**
     * Share the journal to Instagram as a story
     *
     * @param journal The journal to share
     */
    fun shareToInstagram(journal: Journal) {
        try {
            sharingLauncher.shareJournalToInstagram(journal.id, ShareTheme.Light)
        } catch (e: Exception) {
            _error.value = "Failed to share to Instagram: ${e.message}"
        }
    }

    /**
     * Share the journal through the system share sheet
     *
     * @param journal The journal to share
     */
    fun shareJournal(journal: Journal) {
        try {
            sharingLauncher.shareJournalLink(journal.id)
        } catch (e: Exception) {
            _error.value = "Failed to share journal: ${e.message}"
        }
    }
}

/**
 * UI state for the share journal screen
 */
sealed interface ShareJournalUiState {
    /**
     * Loading state while journal data is being fetched
     */
    object Loading : ShareJournalUiState
    
    /**
     * Error state when journal data couldn't be loaded
     */
    object Error : ShareJournalUiState
    
    /**
     * Success state when journal data is loaded and ready to display
     *
     * @param journal The journal to be shared
     * @param lastUpdatedDisplay Formatted string showing when the journal was last updated
     */
    data class Success(
        val journal: Journal,
        val lastUpdatedDisplay: String,
    ) : ShareJournalUiState
}