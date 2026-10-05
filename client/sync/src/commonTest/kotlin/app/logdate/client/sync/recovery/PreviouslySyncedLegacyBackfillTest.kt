package app.logdate.client.sync.recovery

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.InMemoryKeyValueStorage
import app.logdate.client.sync.LegacyLocalBackfill
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.KeyValueFirstSyncEnqueueStore
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.test.FakeJournalContentRepository
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.FakeJournalRepository
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.shared.model.Journal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class PreviouslySyncedLegacyBackfillTest {
    @Test
    fun `previously synced local entries and journals missing from the current cloud are recovered`() =
        runTest { verifyBackfill(previouslyCompleted = false) }

    @Test
    fun `upgrading a completed legacy sweep revisits records excluded by the old zero version filter`() =
        runTest { verifyBackfill(previouslyCompleted = true) }

    private suspend fun verifyBackfill(previouslyCompleted: Boolean) {
        val scope = DownloadScope("owner", "origin")
        val db = DownloadInboxTest.Database()
        val inbox = DownloadInbox(db, db, { scope }, { 1L })
        val backing = InMemoryKeyValueStorage()
        val storage =
            object : KeyValueStorage by backing {
                override suspend fun getBoolean(
                    key: String,
                    defaultValue: Boolean,
                ): Boolean = backing.getString(key)?.toBooleanStrictOrNull() ?: defaultValue

                override suspend fun putBoolean(
                    key: String,
                    value: Boolean,
                ) = backing.putString(key, value.toString())
            }
        if (previouslyCompleted) {
            storage.putBoolean("sync_local_backfill_v1_NOTE_5:owner:6:origin", true)
            storage.putBoolean("sync_local_backfill_v1_JOURNAL_5:owner:6:origin", true)
        }
        val marker = KeyValueFirstSyncEnqueueStore(storage)
        marker.markAuditedLegacyScope(scope.owner, scope.origin)
        val date = Instant.fromEpochMilliseconds(123)
        val note = JournalNote.Text(Uuid.random(), date, date, "Original entry", syncVersion = 73)
        val journal =
            Journal(title = "Original journal", description = "Original description", created = date, lastUpdated = date, syncVersion = 81)
        val cloudNote = note.copy(uid = Uuid.random())
        val removedNote = note.copy(uid = Uuid.random())
        val pendingNote = note.copy(uid = Uuid.random())
        val cloudJournal = journal.copy(id = Uuid.random())
        val removedJournal = journal.copy(id = Uuid.random())
        val pendingJournal = journal.copy(id = Uuid.random())
        val notes =
            FakeJournalNotesRepository().apply {
                listOf(note, cloudNote, removedNote, pendingNote).forEach { create(it) }
            }
        val journals =
            FakeJournalRepository().apply {
                listOf(journal, cloudJournal, removedJournal, pendingJournal).forEach { create(it) }
            }
        inbox.stage(
            "NOTE",
            500,
            listOf(WireDownload(cloudNote.uid.toString(), 100, false, ""), WireDownload(removedNote.uid.toString(), 101, true, "")),
        )
        inbox.stage(
            "JOURNAL",
            500,
            listOf(WireDownload(cloudJournal.id.toString(), 100, false, ""), WireDownload(removedJournal.id.toString(), 101, true, "")),
        )
        val metadata = fakeSyncMetadataService()
        metadata.enqueuePending(pendingNote.uid.toString(), EntityType.NOTE, PendingOperation.DELETE)
        metadata.enqueuePending(pendingJournal.id.toString(), EntityType.JOURNAL, PendingOperation.UPDATE)
        val backfill = LegacyLocalBackfill(inbox, marker, journals, notes, FakeJournalContentRepository(), metadata)

        backfill.enqueueRecordsIfNeeded(EntityType.NOTE)
        backfill.enqueueRecordsIfNeeded(EntityType.JOURNAL)

        val noteWork = metadata.getPendingUploads(EntityType.NOTE).associate { it.entityId to it.operation }
        val journalWork = metadata.getPendingUploads(EntityType.JOURNAL).associate { it.entityId to it.operation }
        assertEquals(mapOf(note.uid.toString() to PendingOperation.CREATE, pendingNote.uid.toString() to PendingOperation.DELETE), noteWork)
        assertEquals(
            mapOf(journal.id.toString() to PendingOperation.CREATE, pendingJournal.id.toString() to PendingOperation.UPDATE),
            journalWork,
        )
        assertEquals(note, notes.getNoteById(note.uid))
        assertEquals(journal, journals.getJournalById(journal.id))
        assertTrue(marker.hasEnqueuedLocalScope(scope.owner, scope.origin, EntityType.NOTE))
        assertTrue(marker.hasEnqueuedLocalScope(scope.owner, scope.origin, EntityType.JOURNAL))
        val queued = metadata.enqueuePendingCalls.size
        backfill.enqueueRecordsIfNeeded(EntityType.NOTE)
        backfill.enqueueRecordsIfNeeded(EntityType.JOURNAL)
        assertEquals(queued, metadata.enqueuePendingCalls.size)
    }
}
