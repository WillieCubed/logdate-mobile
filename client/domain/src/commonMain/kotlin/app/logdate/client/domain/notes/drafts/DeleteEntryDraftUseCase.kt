package app.logdate.client.domain.notes.drafts

import app.logdate.client.media.MediaCleaner
import app.logdate.client.media.NoOpMediaCleaner
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlin.uuid.Uuid

/**
 * Use case for deleting entry drafts.
 *
 * Before removing the draft from storage, every media file referenced by the
 * draft (Ready audio mediaRefs in [EntryDraft.notes] and pending recordings in
 * [EntryDraft.pendingMedia]) is deleted via [mediaCleaner] -- *unless* a permanent
 * note still references that same path. This stops orphan files from
 * accumulating under `filesDir/audio_notes/` after a discard, without being able
 * to destroy a recording a permanent note now depends on: a save flow that calls
 * this discard path right after publishing (the exact mistake that once deleted
 * months of real recordings — see [deleteAfterPublish]) now finds nothing left
 * to delete for that path instead of deleting it out from under the new note.
 *
 * Read-only entries that happen to be loaded into the editor are NOT affected:
 * the use case only deletes the draft — its associated [JournalNote.Audio]
 * records were created at autosave time, never copied from a persisted entry.
 */
class DeleteEntryDraftUseCase(
    private val entryDraftRepository: EntryDraftRepository,
    private val journalNotesRepository: JournalNotesRepository,
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
                val ownedPaths = draft.collectMediaPaths()
                val stillPublished = pathsStillReferencedByNotes(ownedPaths)
                mediaCleaner.deleteAll(ownedPaths - stillPublished)
            }
        } catch (e: Exception) {
            // Cleanup is best-effort — a failure here must not block deletion of
            // the draft itself, since the draft is the user-visible entity.
            Napier.w("Failed to clean up media for draft $draftId: ${e.message}")
        }
        entryDraftRepository.deleteDraft(draftId)
    }

    private suspend fun pathsStillReferencedByNotes(paths: List<String>): Set<String> {
        if (paths.isEmpty()) return emptySet()
        val candidates = paths.toSet()
        return journalNotesRepository.allNotesObserved
            .first()
            .mapNotNull { it.mediaRefOrNull() }
            .filterTo(mutableSetOf()) { it in candidates }
    }

    /**
     * Removes a draft that has already been promoted to permanent notes.
     *
     * Published media is deliberately retained because the permanent note now
     * owns the same path. Reusing the discard path here would delete the user's
     * recording immediately after saving it.
     */
    suspend fun deleteAfterPublish(draftId: Uuid) {
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

private fun JournalNote.mediaRefOrNull(): String? =
    when (this) {
        is JournalNote.Audio -> mediaRef
        is JournalNote.Image -> mediaRef
        is JournalNote.Video -> mediaRef
        else -> null
    }
