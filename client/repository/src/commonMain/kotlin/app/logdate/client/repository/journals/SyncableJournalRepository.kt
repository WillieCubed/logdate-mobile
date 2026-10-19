package app.logdate.client.repository.journals

import app.logdate.shared.model.Journal
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Sync-aware journal repository operations that bypass local outbox tracking.
 */
interface SyncableJournalRepository : JournalRepository {
    suspend fun createFromSync(journal: Journal)

    suspend fun updateFromSync(journal: Journal)

    suspend fun deleteFromSync(journalId: Uuid)

    suspend fun updateSyncMetadata(
        journalId: Uuid,
        syncVersion: Long,
        syncedAt: Instant,
    )

    /**
     * Points journal [journalId]'s cover at [coverImageUri] only while it still stores [previous],
     * without queueing an upload. Returns whether it did. Repositories that keep no covers do nothing.
     */
    suspend fun updateCoverImageUri(
        journalId: Uuid,
        previous: String,
        coverImageUri: String,
    ): Boolean = false
}
