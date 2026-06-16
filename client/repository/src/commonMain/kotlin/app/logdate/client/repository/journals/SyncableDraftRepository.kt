package app.logdate.client.repository.journals

import app.logdate.shared.model.EditorDraft
import kotlin.uuid.Uuid

/**
 * Repository operations used when applying draft changes that already came from sync.
 *
 * These methods must not enqueue another outbound sync operation; otherwise downloading a remote
 * draft would immediately re-upload the same draft and can create an infinite sync loop.
 */
interface SyncableDraftRepository {
    suspend fun saveDraftFromSync(draft: EditorDraft)

    suspend fun deleteDraftFromSync(id: Uuid)
}
