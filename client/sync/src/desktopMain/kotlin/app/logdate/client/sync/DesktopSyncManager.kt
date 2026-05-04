package app.logdate.client.sync

import app.logdate.client.sync.metadata.SyncDeadLetterRecord
import kotlinx.coroutines.flow.Flow

/**
 * A platform-specific implementation of [SyncManager] for Desktop.
 *
 * This delegates to DefaultSyncManager for core functionality and adds
 * any desktop-specific sync behaviors if needed.
 */
class DesktopSyncManager(
    private val defaultSyncManager: DefaultSyncManager,
) : SyncManager {
    override fun sync(startNow: Boolean) {
        defaultSyncManager.sync(startNow)
    }

    override suspend fun uploadPendingChanges(): SyncResult = defaultSyncManager.uploadPendingChanges()

    override suspend fun downloadRemoteChanges(): SyncResult = defaultSyncManager.downloadRemoteChanges()

    override suspend fun syncContent(): SyncResult = defaultSyncManager.syncContent()

    override suspend fun syncJournals(): SyncResult = defaultSyncManager.syncJournals()

    override suspend fun syncAssociations(): SyncResult = defaultSyncManager.syncAssociations()

    override suspend fun syncDrafts(): SyncResult = defaultSyncManager.syncDrafts()

    override suspend fun fullSync(): SyncResult = defaultSyncManager.fullSync()

    override suspend fun getSyncStatus(): SyncStatus = defaultSyncManager.getSyncStatus()

    override fun observeDeadLetters(): Flow<List<SyncDeadLetterRecord>> = defaultSyncManager.observeDeadLetters()

    override suspend fun retryDeadLetter(id: String) = defaultSyncManager.retryDeadLetter(id)

    override suspend fun discardDeadLetter(id: String) = defaultSyncManager.discardDeadLetter(id)

    override val syncStatusFlow = defaultSyncManager.syncStatusFlow
}
