package app.logdate.client.data.restore

import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.SerializableTextBlock

/** Projects only legacy draft fields that the Apple editor can preserve exactly. */
internal object NativeArchive3EntryDrafts {
    fun convert(
        drafts: List<EntryDraft>,
        ownerBound: Boolean,
    ): List<EditorDraft> {
        check(!ownerBound || drafts.isEmpty()) { "Unscoped entry drafts cannot be attributed to an account" }
        check(drafts.map { it.id }.distinct().size == drafts.size) { "Duplicate entry draft identities" }
        return drafts.map { draft ->
            check(
                draft.pendingMedia.isEmpty() &&
                    draft.visitContext == null &&
                    draft.markId == null &&
                    draft.editBase == null &&
                    draft.quietSave == null &&
                    draft.recordingOwners.isEmpty(),
            ) { "Entry draft has state outside the Apple editor" }
            check(draft.selectedJournalIds.distinct().size == draft.selectedJournalIds.size) { "Duplicate draft journal membership" }
            check(
                draft.notes
                    .map { it.uid }
                    .distinct()
                    .size == draft.notes.size,
            ) { "Duplicate draft block identities" }
            EditorDraft(
                id = draft.id,
                blocks =
                    draft.notes.map { note ->
                        check(note is JournalNote.Text) { "Entry draft has an unsupported block" }
                        check(
                            note.lastUpdated == note.creationTimestamp &&
                                note.syncVersion == 0L &&
                                note.location == null &&
                                note.timeZoneId == null &&
                                note.capturedOn == null &&
                                note.capturedDuring == null,
                        ) { "Entry draft block has state outside the Apple editor" }
                        SerializableTextBlock(note.uid, note.creationTimestamp, content = note.content)
                    },
                selectedJournalIds = draft.selectedJournalIds,
                createdAt = draft.createdAt,
                lastModifiedAt = draft.updatedAt,
            )
        }
    }
}
