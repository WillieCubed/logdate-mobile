package app.logdate.client.domain.notes.drafts

import app.logdate.client.media.MediaCleaner
import app.logdate.client.media.NoOpMediaCleaner
import app.logdate.client.media.storage.StoredMediaReferences
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.mediaRefOrNull
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlin.uuid.Uuid

/**
 * Use case for deleting entry drafts.
 *
 * Removes a draft before considering cleanup of media it referenced. A media path can also
 * belong to a permanent note, another draft, or a journal cover; those references retain it.
 * The platform cleaner decides whether a remaining path is exclusively disposable.
 */
class DeleteEntryDraftUseCase(
    private val entryDraftRepository: EntryDraftRepository,
    private val journalNotesRepository: JournalNotesRepository,
    private val mediaCleaner: MediaCleaner = NoOpMediaCleaner,
    private val journalCoverUris: suspend () -> List<String?>? = { null },
    private val mediaReferences: StoredMediaReferences = StoredMediaReferences.Unchanged,
) {
    /**
     * Deletes the draft with the given ID.
     *
     * @param draftId The ID of the draft to delete
     */
    suspend operator fun invoke(draftId: Uuid) {
        val draft =
            try {
                entryDraftRepository.getDraft(draftId).first().getOrNull()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Napier.w("Could not read draft media before deletion", error)
                null
            }
        entryDraftRepository.deleteDraft(draftId)
        if (draft == null) return

        try {
            val candidates = draft.collectMediaPaths().distinct()
            if (candidates.isEmpty()) return
            val remainingDrafts = entryDraftRepository.getDrafts().first().filterNot { it.id == draftId }
            val covers = journalCoverUris() ?: return
            val candidateSpellings = candidates.associateWith(mediaReferences::storedReference)
            val stillPublished = journalNotesRepository.notesReferencingMediaPaths((candidates + candidateSpellings.values).toSet())
            val publishedReferences = stillPublished.map(mediaReferences::storedReference).toSet()
            val otherReferences =
                (remainingDrafts.flatMap(EntryDraft::collectMediaPaths) + covers.filterNotNull())
                    .map(mediaReferences::storedReference)
                    .toSet()
            mediaCleaner.deleteAll(
                candidates.filter { path ->
                    val stored = candidateSpellings.getValue(path)
                    stored !in publishedReferences && stored !in otherReferences
                },
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            Napier.w("Failed to clean up media for draft $draftId: ${e.message}")
        }
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
 * Collects all paths the draft names, including the fallback recording checkpoint.
 */
private fun EntryDraft.collectMediaPaths(): List<String> =
    notes.mapNotNull { note -> note.mediaRefOrNull() } +
        pendingMedia.flatMap { pending -> listOfNotNull(pending.filePath, pending.fallbackFilePath) }
