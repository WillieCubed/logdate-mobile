package app.logdate.client.domain.notes.drafts

import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.PendingMediaRecord
import kotlin.uuid.Uuid

/**
 * Use case for replacing an entry draft's pending-media list.
 *
 * Pending media tracks in-flight recordings or captures whose final URI is not
 * yet available. Persisting them alongside the draft is what lets the editor
 * recover an in-progress recording on relaunch — the draft is the registry.
 */
class SetEntryDraftPendingMediaUseCase(
    private val entryDraftRepository: EntryDraftRepository,
) {
    suspend operator fun invoke(
        draftId: Uuid,
        pendingMedia: List<PendingMediaRecord>,
    ) {
        entryDraftRepository.setPendingMedia(draftId, pendingMedia)
    }
}
