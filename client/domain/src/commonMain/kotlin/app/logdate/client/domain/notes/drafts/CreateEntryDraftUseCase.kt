package app.logdate.client.domain.notes.drafts

import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.PendingMediaRecord
import kotlin.uuid.Uuid

/**
 * Use case for creating entry drafts.
 */
class CreateEntryDraftUseCase(
    private val entryDraftRepository: EntryDraftRepository,
) {
    /**
     * Creates a new draft with content from a list of journal notes.
     * Returns the ID of the new draft.
     */
    suspend operator fun invoke(notes: List<JournalNote>): Uuid = entryDraftRepository.createDraft(notes)

    /** Creates a complete draft snapshot at a stable identity. */
    suspend operator fun invoke(
        draftId: Uuid,
        notes: List<JournalNote>,
        pendingMedia: List<PendingMediaRecord> = emptyList(),
        selectedJournalIds: List<Uuid> = emptyList(),
    ): Uuid =
        entryDraftRepository.createDraft(
            uid = draftId,
            notes = notes,
            pendingMedia = pendingMedia,
            selectedJournalIds = selectedJournalIds,
        )

    /**
     * Creates a new draft with content from a single journal note.
     * Returns the ID of the new draft.
     */
    suspend operator fun invoke(note: JournalNote): Uuid = entryDraftRepository.createDraft(listOf(note))
}
