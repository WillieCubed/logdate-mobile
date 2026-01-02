package app.logdate.client.repository.journals

import app.logdate.shared.model.Journal
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Sync-aware journal repository operations that bypass local outbox tracking.
 */
interface SyncableJournalRepository : JournalRepository {
    suspend fun createFromSync(journal: Journal)
    suspend fun updateFromSync(journal: Journal)
    suspend fun deleteFromSync(journalId: Uuid)
    suspend fun updateSyncMetadata(journalId: Uuid, syncVersion: Long, syncedAt: Instant)
}
