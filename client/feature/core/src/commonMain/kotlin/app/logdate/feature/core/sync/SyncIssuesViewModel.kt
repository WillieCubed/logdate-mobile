package app.logdate.feature.core.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.metadata.SyncDeadLetterRecord
import app.logdate.shared.model.textContent
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

class SyncIssuesViewModel(
    private val syncManager: SyncManager,
    private val journalRepository: JournalRepository,
    private val journalNotesRepository: JournalNotesRepository,
) : ViewModel() {
    private val _retryFeedback = MutableStateFlow<SyncIssueRetryFeedback?>(null)
    val retryFeedback: StateFlow<SyncIssueRetryFeedback?> = _retryFeedback
    val labels: StateFlow<Map<String, String>> =
        syncManager
            .observeDeadLetters()
            .map { records ->
                records
                    .mapNotNull { record ->
                        val label =
                            runCatching { resolveLabel(record) }
                                .onFailure { Napier.w("Could not read a local label for a sync issue", it) }
                                .getOrNull()
                        label?.let { record.id to it }
                    }.toMap()
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap(),
            )

    private suspend fun resolveLabel(record: SyncDeadLetterRecord): String? {
        val id = runCatching { Uuid.parse(record.entityId) }.getOrNull() ?: return null
        return when (record.entityType.uppercase()) {
            "JOURNAL" -> journalRepository.getJournalById(id)?.title?.toPreviewLabelOrNull()
            "DRAFT" -> journalRepository.getDraft(id)?.textContent()?.toPreviewLabelOrNull()
            "NOTE" -> journalNotesRepository.getNoteById(id)?.explicitLabelOrNull()
            else -> null
        }
    }

    val records: StateFlow<List<SyncDeadLetterRecord>> =
        syncManager
            .observeDeadLetters()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /**
     * Entries still waiting to upload. Dead-lettering takes nine failed attempts, so a queue that
     * is stuck but has not exhausted its retries would otherwise render as "everything is synced"
     * while nothing has actually been backed up.
     */
    val pendingCount: StateFlow<Int> =
        syncManager.syncStatusFlow
            .map { it.pendingUploads }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0,
            )

    fun retry(id: String) {
        viewModelScope.launch {
            _retryFeedback.value = null
            runCatching {
                syncManager.retryDeadLetter(id)
                syncManager.requestBackup()
            }.onSuccess {
                _retryFeedback.value = SyncIssueRetryFeedback.REQUESTED
            }.onFailure { error ->
                Napier.e("Could not request a retry for a sync issue", error)
                _retryFeedback.value = SyncIssueRetryFeedback.COULD_NOT_START
            }
        }
    }

    fun discard(id: String) {
        viewModelScope.launch { syncManager.discardDeadLetter(id) }
    }
}

enum class SyncIssueRetryFeedback {
    REQUESTED,
    COULD_NOT_START,
}
