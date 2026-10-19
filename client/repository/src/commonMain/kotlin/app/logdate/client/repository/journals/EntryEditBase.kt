package app.logdate.client.repository.journals

import app.logdate.util.UuidSerializer
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** The version and account observed when an existing entry was opened for editing. */
@Serializable
data class EntryEditBase(
    val original: JournalNote,
    val scope: JournalMergeScope?,
    @Serializable(with = UuidSerializer::class)
    val copyNoteId: Uuid? = null,
) {
    val targetId: Uuid get() = copyNoteId ?: original.uid
}
