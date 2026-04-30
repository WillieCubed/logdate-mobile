package app.logdate.client.domain.notes.drafts

import app.logdate.client.media.MediaCleaner
import app.logdate.client.media.NoOpMediaCleaner
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalNote
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlin.uuid.Uuid

/**
 * Use case for deleting entry drafts.
 *
 * Before removing the draft from storage, every media file referenced by the
 * draft (Ready audio mediaRefs in [EntryDraft.notes] and pending recordings in
 * [EntryDraft.pendingMedia]) is deleted via [mediaCleaner]. This stops orphan
 * files from accumulating under `filesDir/audio_notes/` after a discard.
 *
 * Read-only entries that happen to be loaded into the editor are NOT affected:
 * the use case only deletes the draft — its associated [JournalNote.Audio]
 * records were created at autosave time, never copied from a persisted entry.
 */
class DeleteEntryDraftUseCase(
    private val entryDraftRepository: EntryDraftRepository,
    private val mediaCleaner: MediaCleaner = NoOpMediaCleaner,
) {
    /**
     * Deletes the draft with the given ID.
     *
     * @param draftId The ID of the draft to delete
     */
    suspend operator fun invoke(draftId: Uuid) {
        try {
            val draft = entryDraftRepository.getDraft(draftId).first().getOrNull()
            if (draft != null) {
                mediaCleaner.deleteAll(draft.collectMediaPaths())
            }
        } catch (e: Exception) {
            // Cleanup is best-effort — a failure here must not block deletion of
            // the draft itself, since the draft is the user-visible entity.
            Napier.w("Failed to clean up media for draft $draftId: ${e.message}")
        }
        entryDraftRepository.deleteDraft(draftId)
    }
}

/**
 * Collects every filesystem path the draft owns. Pending media may have a null
 * [app.logdate.client.repository.journals.PendingMediaRecord.filePath]; those
 * entries are skipped because there is nothing to delete.
 */
private fun EntryDraft.collectMediaPaths(): List<String> =
    notes.mapNotNull { note -> (note as? JournalNote.Audio)?.mediaRef } +
        pendingMedia.mapNotNull { it.filePath }
