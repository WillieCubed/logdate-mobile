package app.logdate.client.repository.journals

import kotlin.uuid.Uuid

/**
 * Sync-aware association operations that bypass local outbox tracking.
 */
interface SyncableJournalContentRepository : JournalContentRepository {
    suspend fun addContentToJournalFromSync(contentId: Uuid, journalId: Uuid)
    suspend fun removeContentFromJournalFromSync(contentId: Uuid, journalId: Uuid)
}
