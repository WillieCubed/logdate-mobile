package app.logdate.client.data.journals

import androidx.room.Room
import app.logdate.client.data.fakes.FakeDraftRepository
import app.logdate.client.data.fakes.FakeRemoteJournalDataSource
import app.logdate.client.data.notes.OfflineFirstJournalNotesRepository
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.RoomSyncTransactionManager
import app.logdate.client.sync.metadata.DatabaseSyncMetadataService
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ArchiveJournalReceiptTest {
    private val directory = Files.createTempDirectory("archive-journal-receipts-")
    private val database = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(directory.resolve("journal.db").toString()))
    private val scope = JournalMergeScope("receipt-owner", "https://example.test")
    private val provider =
        object : CanonicalOwnerProvider {
            override suspend fun getCanonicalOwnerId() = scope.ownerId

            override suspend fun hasBoundOwner() = true
        }
    private val metadata =
        DatabaseSyncMetadataService(
            database.syncMetadataDao(),
            DefaultLogDateConfigRepository(initialBackendUrl = scope.serverOrigin),
            provider,
        )
    private val transactions = RoomSyncTransactionManager(database)
    private val receipts = RoomArchiveRecoveryReceipts(database.downloadInboxDao())
    private val journals =
        OfflineFirstJournalRepository(
            database.journalDao(),
            FakeRemoteJournalDataSource(),
            FakeDraftRepository(),
            syncMetadataService = metadata,
            database = database,
            currentScope = { scope },
            mergeTransactionManager = transactions,
            recoveryReceipts = receipts,
        )
    private val notes =
        OfflineFirstJournalNotesRepository(
            database.textNoteDao(),
            database.imageNoteDao(),
            database.audioNoteDao(),
            database.videoNoteDao(),
            database.journalContentDao(),
            journals,
            database.mediaCaptionDao(),
            transactionManager = transactions,
            syncMetadataService = metadata,
            currentRecoveryScope = { scope },
            recoveryReceipts = receipts,
        )
    private val content =
        OfflineFirstJournalContentRepository(
            database.journalContentDao(),
            journals,
            notes,
            metadata,
            transactions,
            recoveryReceipts = receipts,
        )
    private val at = Instant.parse("2026-01-02T03:04:05Z")

    @AfterTest
    fun close() {
        database.close()
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `a pending deletion remains respected after sync settles it`() =
        runTest {
            val journal = Journal(Uuid.random(), "Deleted")
            journals.create(journal)
            journals.delete(journal.id)
            val recovery = Uuid.random().toString()
            assertFalse(journals.compareAndSetJournalForRestore(null, journal, scope, recovery))
            metadata.clearPending()
            assertFalse(journals.compareAndSetJournalForRestore(null, journal, scope, recovery))
            assertNull(journals.getJournalById(journal.id))
            val note = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Deleted")
            notes.create(note)
            notes.removeById(note.uid)
            assertFalse(notes.compareAndSetNoteForRestore(null, note, scope, recovery))
            metadata.clearPending()
            assertFalse(notes.compareAndSetNoteForRestore(null, note, scope, recovery))
            assertNull(notes.getNoteById(note.uid))
        }

    @Test
    fun `keeping a newer journal records a decision without queuing a mutation`() =
        runTest {
            val journal = Journal(Uuid.random(), "Keep current")
            journals.create(journal)
            metadata.clearPending()
            val recovery = Uuid.random().toString()
            val current = requireNotNull(journals.getJournalById(journal.id))
            assertTrue(journals.compareAndSetJournalForRestore(current, current, scope, recovery))
            assertTrue(metadata.getPendingCount() == 0)
            journals.delete(journal.id)
            metadata.clearPending()
            assertFalse(journals.compareAndSetJournalForRestore(null, journal, scope, recovery))
            assertNull(journals.getJournalById(journal.id))
        }

    @Test
    fun `a concurrent note edit records preservation before later deletion`() =
        runTest {
            val note = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Current")
            notes.create(note)
            val recovery = Uuid.random().toString()
            assertFalse(notes.compareAndSetNoteForRestore(null, note, scope, recovery))
            notes.removeById(note.uid)
            metadata.clearPending()
            assertFalse(notes.compareAndSetNoteForRestore(null, note, scope, recovery))
            assertNull(notes.getNoteById(note.uid))
        }

    @Test
    fun `journal replay cannot recreate a deletion that has already settled`() =
        runTest {
            val journal = Journal(Uuid.random(), "Imported once")
            val recovery = Uuid.random().toString()
            assertTrue(journals.compareAndSetJournalForRestore(null, journal, scope, recovery))
            metadata.clearPending()
            journals.delete(journal.id)
            metadata.clearPending()
            assertFalse(journals.compareAndSetJournalForRestore(null, journal, scope, recovery))
            assertNull(journals.getJournalById(journal.id))
        }

    @Test
    fun `membership replay cannot undo a removal that has already settled`() =
        runTest {
            val journal = Journal(Uuid.random(), "Membership recovery")
            journals.create(journal)
            val note = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Keep outside this journal")
            notes.create(note)
            val recovery = Uuid.random().toString()
            assertTrue(content.addContentToJournalForRestore(note.uid, journal.id, scope, recovery))
            metadata.clearPending()
            content.removeContentFromJournal(note.uid, journal.id)
            metadata.clearPending()
            assertFalse(content.addContentToJournalForRestore(note.uid, journal.id, scope, recovery))
            assertTrue(content.observeJournalsForContent(note.uid).first().isEmpty())
        }

    @Test
    fun `membership replay respects removal after its original journal is merged`() =
        runTest {
            val source = Journal(Uuid.random(), "Archive journal")
            val destination = Journal(Uuid.random(), "Current journal")
            journals.create(source)
            journals.create(destination)
            val note = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Keep removed membership")
            notes.create(note)
            val recovery = Uuid.random().toString()
            assertTrue(content.addContentToJournalForRestore(note.uid, source.id, scope, recovery))
            journals.applyJournalRedirect(source.id, destination.id, scope)
            content.removeContentFromJournal(note.uid, destination.id)
            metadata.clearPending()
            assertFalse(content.addContentToJournalForRetainedRestore(note.uid, source.id, scope, recovery))
            assertTrue(content.observeJournalsForContent(note.uid).first().isEmpty())
        }

    @Test
    fun `a deleted destination does not abort remaining recovery with a foreign key error`() =
        runTest {
            val journal = Journal(Uuid.random(), "Deleted destination")
            journals.create(journal)
            val note = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Keep entry")
            notes.create(note)
            journals.delete(journal.id)
            assertFalse(content.addContentToJournalForRestore(note.uid, journal.id, scope, Uuid.random().toString()))
        }

    @Test
    fun `settled local journal and note deletions block a later retained generation`() =
        runTest {
            val journal = Journal(Uuid.random(), "Deleted after source capture")
            journals.create(journal)
            metadata.clearPending()
            journals.delete(journal.id)
            metadata.clearPending()

            assertTrue(receipts.containsLocalDeletion(scope, LocalDeletionType.JOURNAL, journal.id.toString()))
            assertFalse(
                journals.compareAndSetJournalForRetainedRestore(
                    journal.id,
                    null,
                    journal,
                    scope,
                    Uuid.random().toString(),
                ),
            )
            assertNull(journals.getJournalById(journal.id))
            assertTrue(
                journals.compareAndSetJournalForRestore(null, journal, scope, Uuid.random().toString()),
                "An explicit archive restore remains an intentional undelete",
            )

            val note = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Deleted after source capture")
            notes.create(note)
            metadata.clearPending()
            notes.removeById(note.uid)
            metadata.clearPending()

            assertTrue(receipts.containsLocalDeletion(scope, LocalDeletionType.NOTE, note.uid.toString()))
            assertFalse(
                notes.compareAndSetNoteForRetainedRestore(
                    note.uid,
                    null,
                    note,
                    scope,
                    Uuid.random().toString(),
                ),
            )
            assertNull(notes.getNoteById(note.uid))
            assertTrue(
                notes.compareAndSetNoteForRestore(null, note, scope, Uuid.random().toString()),
                "An explicit archive restore remains an intentional undelete",
            )
        }

    @Test
    fun `settled local membership removal blocks a later retained generation`() =
        runTest {
            val journal = Journal(Uuid.random(), "Membership removal")
            val note = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Stay outside")
            journals.create(journal)
            notes.create(note)
            content.addContentToJournal(note.uid, journal.id)
            metadata.clearPending()
            content.removeContentFromJournal(note.uid, journal.id)
            metadata.clearPending()
            val key =
                app.logdate.client.sync.metadata
                    .AssociationPendingKey(journal.id, note.uid)
                    .toPendingId()

            assertTrue(receipts.containsLocalDeletion(scope, LocalDeletionType.ASSOCIATION, key))
            assertFalse(
                content.addContentToJournalForRetainedRestore(
                    note.uid,
                    journal.id,
                    scope,
                    Uuid.random().toString(),
                ),
            )
            assertTrue(content.observeJournalsForContent(note.uid).first().isEmpty())
            assertTrue(
                content.addContentToJournalForRestore(note.uid, journal.id, scope, Uuid.random().toString()),
                "An explicit archive restore remains an intentional membership undelete",
            )
            assertEquals(
                journal.id,
                content
                    .observeJournalsForContent(note.uid)
                    .first()
                    .single()
                    .id,
            )
        }

    @Test
    fun `source deletion blocks retained conflict variants`() =
        runTest {
            val sourceJournal = Journal(Uuid.random(), "Deleted source")
            journals.create(sourceJournal)
            metadata.clearPending()
            journals.delete(sourceJournal.id)
            metadata.clearPending()
            val journalVariant = sourceJournal.copy(id = Uuid.random())
            assertFalse(
                journals.compareAndSetJournalForRetainedRestore(
                    sourceJournal.id,
                    null,
                    journalVariant,
                    scope,
                    Uuid.random().toString(),
                ),
            )
            assertNull(journals.getJournalById(journalVariant.id))

            val sourceNote = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Deleted source note")
            notes.create(sourceNote)
            metadata.clearPending()
            notes.removeById(sourceNote.uid)
            metadata.clearPending()
            val noteVariant = sourceNote.copy(uid = Uuid.random())
            assertFalse(
                notes.compareAndSetNoteForRetainedRestore(
                    sourceNote.uid,
                    null,
                    noteVariant,
                    scope,
                    Uuid.random().toString(),
                ),
            )
            assertNull(notes.getNoteById(noteVariant.uid))
        }

    @Test
    fun `server settled deletions also block a later retained generation`() =
        runTest {
            val journal = Journal(Uuid.random(), "Deleted remotely", created = at, lastUpdated = at)
            journals.createFromSync(journal)
            journals.deleteFromSync(journal.id)
            assertFalse(
                journals.compareAndSetJournalForRetainedRestore(
                    journal.id,
                    null,
                    journal,
                    scope,
                    Uuid.random().toString(),
                ),
            )
            journals.createFromSync(journal)
            assertEquals(journal, journals.getJournalById(journal.id))
            assertTrue(
                journals.compareAndSetJournalForRetainedRestore(
                    journal.id,
                    journal,
                    journal,
                    scope,
                    Uuid.random().toString(),
                ),
            )

            val note = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Deleted remotely")
            notes.createFromSync(note)
            notes.deleteFromSync(note.uid)
            assertFalse(
                notes.compareAndSetNoteForRetainedRestore(
                    note.uid,
                    null,
                    note,
                    scope,
                    Uuid.random().toString(),
                ),
            )
            notes.createFromSync(note)
            assertTrue(
                notes.compareAndSetNoteForRetainedRestore(
                    note.uid,
                    note,
                    note,
                    scope,
                    Uuid.random().toString(),
                ),
            )
            content.removeContentFromJournalFromSync(note.uid, journal.id)
            content.addContentToJournalFromSync(note.uid, journal.id)
            assertTrue(
                content.addContentToJournalForRetainedRestore(
                    note.uid,
                    journal.id,
                    scope,
                    Uuid.random().toString(),
                ),
            )
        }
}
