package app.logdate.feature.core.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncPausedReason
import app.logdate.client.sync.SyncStatus
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.QueuedUpload
import app.logdate.client.sync.metadata.SyncMetadataService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Instant

/**
 * Backs the backup status sheet: what is waiting to upload, grouped by kind, and why it is not
 * moving if something is holding it back.
 */
class SyncStatusViewModel(
    private val syncManager: SyncManager,
    syncMetadataService: SyncMetadataService,
    private val sessionStorage: SessionStorage,
) : ViewModel() {
    private val queueReadFailed = MutableStateFlow(false)

    val uiState: StateFlow<SyncStatusUiState> =
        combine(
            syncManager.syncStatusFlow,
            syncMetadataService
                .observePendingUploads()
                .catch { error ->
                    Napier.e("Could not read the backup queue", error)
                    queueReadFailed.value = true
                    emit(emptyList())
                },
            syncManager.observeDeadLetters().map { it.size },
            queueReadFailed,
        ) { status, queue, failedCount, readFailed ->
            buildSyncStatusUiState(status, queue, failedCount, readFailed)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = SyncStatusUiState(),
        )

    private val _feedback = MutableStateFlow<SyncStatusFeedback?>(null)
    val feedback: StateFlow<SyncStatusFeedback?> = _feedback.asStateFlow()

    /**
     * Asks the platform to back up now, and says so. Before this, tapping the timeline's backup
     * indicator queued a sync with nothing on screen changing, which read as the tap being ignored.
     */
    fun syncNow() {
        viewModelScope.launch {
            if (sessionStorage.getSession() == null) {
                _feedback.value = SyncStatusFeedback.NeedsAccount
                return@launch
            }
            runCatching { syncManager.sync(startNow = true) }
                .onSuccess { _feedback.value = SyncStatusFeedback.Requested }
                .onFailure { error ->
                    Napier.e("Could not start a backup", error)
                    _feedback.value = SyncStatusFeedback.CouldNotStart
                }
        }
    }
}

/** What the sheet says after "Back up now" is tapped. */
enum class SyncStatusFeedback {
    Requested,
    NeedsAccount,
    CouldNotStart,
}

data class SyncStatusUiState(
    val isSyncing: Boolean = false,
    val completedInRun: Int = 0,
    val totalForRun: Int? = null,
    val pendingCount: Int = 0,
    val pausedReason: SyncPausedReason? = null,
    val lastSyncTime: Instant? = null,
    /** The last attempt failed and will be retried. */
    val lastAttemptFailed: Boolean = false,
    val groups: List<QueuedGroup> = emptyList(),
    /** Items that used up their retries and need a decision in Sync issues. */
    val failedCount: Int = 0,
    /** The queue itself could not be read, so [groups] is not a real answer. */
    val queueUnavailable: Boolean = false,
)

/** Everything of one [kind] waiting to upload. [kind] is null for types this build doesn't know. */
data class QueuedGroup(
    val kind: EntityType?,
    val count: Int,
    /** How many of [count] have already failed at least once and are waiting to retry. */
    val retrying: Int,
)

internal fun buildSyncStatusUiState(
    status: SyncStatus,
    queue: List<QueuedUpload>,
    failedCount: Int,
    queueUnavailable: Boolean,
): SyncStatusUiState =
    SyncStatusUiState(
        isSyncing = status.isSyncing,
        completedInRun = status.completedInRun,
        totalForRun = status.totalForRun,
        // The live queue is the source of truth for what is waiting; the status snapshot's count
        // only refreshes on sync transitions.
        pendingCount = if (queueUnavailable) status.pendingUploads else queue.size,
        pausedReason = status.pausedReason,
        lastSyncTime = status.lastSyncTime,
        lastAttemptFailed = status.lastError != null,
        groups =
            queue
                .groupBy { it.entityType }
                .map { (kind, items) -> QueuedGroup(kind, items.size, items.count { it.retryCount > 0 }) }
                .sortedByDescending { it.count },
        failedCount = failedCount,
        queueUnavailable = queueUnavailable,
    )
