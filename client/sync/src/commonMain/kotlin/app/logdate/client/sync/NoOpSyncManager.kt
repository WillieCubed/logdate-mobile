package app.logdate.client.sync

import app.logdate.client.sync.metadata.SyncDeadLetterRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * No-op sync manager for environments where sync is unavailable.
 */
object NoOpSyncManager : SyncManager {
    private val noOpResult = SyncResult(success = true)
    private val noOpStatus =
        SyncStatus(
            isEnabled = false,
            lastSyncTime = null,
            pendingUploads = 0,
            isSyncing = false,
            hasErrors = false,
            lastError = null,
        )
    private val _syncStatusFlow = MutableStateFlow(noOpStatus)
    override val syncStatusFlow: StateFlow<SyncStatus> = _syncStatusFlow.asStateFlow()

    override fun sync(startNow: Boolean) {
        // No-op.
    }

    override suspend fun uploadPendingChanges(): SyncResult = noOpResult

    override suspend fun downloadRemoteChanges(): SyncResult = noOpResult

    override suspend fun syncContent(): SyncResult = noOpResult

    override suspend fun syncJournals(): SyncResult = noOpResult

    override suspend fun syncAssociations(): SyncResult = noOpResult

    override suspend fun syncDrafts(): SyncResult = noOpResult

    override suspend fun fullSync(): SyncResult = noOpResult

    override suspend fun getSyncStatus(): SyncStatus = noOpStatus

    override fun observeDeadLetters(): Flow<List<SyncDeadLetterRecord>> = flowOf(emptyList())

    override suspend fun retryDeadLetter(id: String) {
        // No-op.
    }

    override suspend fun discardDeadLetter(id: String) {
        // No-op.
    }
}
