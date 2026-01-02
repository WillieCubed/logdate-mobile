package app.logdate.client.repository.journals

import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Sync-aware note repository operations that bypass local outbox tracking.
 */
interface SyncableJournalNotesRepository : JournalNotesRepository {
    suspend fun createFromSync(note: JournalNote)
    suspend fun deleteFromSync(noteId: Uuid)
    suspend fun updateSyncMetadata(note: JournalNote, syncVersion: Long, syncedAt: Instant)
}
