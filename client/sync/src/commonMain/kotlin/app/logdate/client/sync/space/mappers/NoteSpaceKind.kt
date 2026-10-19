package app.logdate.client.sync.space.mappers

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.sync.conflict.ConflictResolver
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.space.SpaceKind
import app.logdate.client.sync.space.SpaceLocalRecords
import app.logdate.client.sync.space.SpaceMediaResolver
import app.logdate.client.sync.space.SpacePart
import app.logdate.client.sync.space.SpacePartChange
import app.logdate.client.sync.space.canonicalRecordKey
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Notes as a kind of the personal space: an `app.logdate.journal.entry` for every note and, for a
 * transcribed recording, an `app.logdate.journal.transcript` that belongs to the note.
 *
 * A note is its entry. A transcript that arrives, or is deleted, changes the recording's
 * transcript and leaves the rest of the note as it is; a transcript whose entry hasn't arrived
 * waits for the entry.
 *
 * @param notes the stored notes, written without queueing another upload.
 * @param resolver decides between this device's note and another device's when both changed.
 * @param media connects each note's file with the blob in the personal space.
 */
fun noteSpaceKind(
    notes: SyncableJournalNotesRepository,
    resolver: ConflictResolver<JournalNote>,
    media: SpaceMediaResolver = SpaceMediaResolver.Default,
): SpaceKind<JournalNote> {
    val entry = SpacePart.main(NoteEntryMapper(media))
    val transcript =
        SpacePart<JournalNote, NoteTranscript>(
            mapper = NoteTranscriptMapper(),
            recordsOf = { note -> (note as? JournalNote.Audio)?.transcript?.let { listOf(NoteTranscript(note.uid, it)) }.orEmpty() },
            ownerOf = { rkey -> canonicalRecordKey(rkey.substringBefore(':')) },
            rkeysOf = { id -> listOf(canonicalRecordKey(id).let { "$it:$it" }) },
        )
    return SpaceKind(
        entityType = EntityType.NOTE,
        main = entry,
        local = StoredNotes(notes, resolver),
        withSyncVersion = { note, version -> note.withSyncVersion(version) },
        others = listOf(transcript),
    ) { changes, local ->
        val entryChange = changes.of(entry).lastOrNull() as? SpacePartChange.Written
        val base = entryChange?.value ?: local ?: return@SpaceKind null
        if (base !is JournalNote.Audio) return@SpaceKind base
        when (val change = changes.of(transcript).lastOrNull()) {
            is SpacePartChange.Written -> base.copy(transcript = change.value.document)
            is SpacePartChange.Deleted -> base.copy(transcript = null)
            null -> base.copy(transcript = (local as? JournalNote.Audio)?.transcript ?: base.transcript)
        }
    }
}

private fun JournalNote.withSyncVersion(version: Long): JournalNote =
    when (this) {
        is JournalNote.Text -> copy(syncVersion = version)
        is JournalNote.Image -> copy(syncVersion = version)
        is JournalNote.Video -> copy(syncVersion = version)
        is JournalNote.Audio -> copy(syncVersion = version)
    }

private class StoredNotes(
    private val notes: SyncableJournalNotesRepository,
    override val conflictResolver: ConflictResolver<JournalNote>,
) : SpaceLocalRecords<JournalNote> {
    override suspend fun load(entityId: String): JournalNote? = Uuid.parseOrNull(entityId)?.let { notes.getNoteById(it) }

    override fun lastUpdatedOf(item: JournalNote): Instant = item.lastUpdated

    override suspend fun acknowledgeUpload(
        item: JournalNote,
        seq: Long,
        syncedAt: Instant,
    ) {
        notes.updateSyncMetadata(item, seq, syncedAt)
    }

    override suspend fun replaceLocal(item: JournalNote) {
        notes.deleteFromSync(item.uid)
        notes.createFromSync(item)
    }
}
