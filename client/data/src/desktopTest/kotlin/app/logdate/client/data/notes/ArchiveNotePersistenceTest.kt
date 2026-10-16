package app.logdate.client.data.notes

import androidx.room.Room
import app.logdate.client.data.fakes.FakeJournalRepository
import app.logdate.client.data.fakes.FakeSyncMetadataService
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.entities.JournalEntity
import app.logdate.client.database.entities.journals.JournalContentEntityLink
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.RoomSyncTransactionManager
import app.logdate.client.sync.metadata.DatabaseSyncMetadataService
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.shared.config.DefaultLogDateConfigRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ArchiveNotePersistenceTest {
    private val directory = Files.createTempDirectory("logdate-archive-note-")
    private val database = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(directory.resolve("notes.db").toString()))
    private val metadata = FakeSyncMetadataService()
    private val repository =
        OfflineFirstJournalNotesRepository(
            textNoteDao = database.textNoteDao(),
            imageNoteDao = database.imageNoteDao(),
            audioNoteDao = database.audioNoteDao(),
            videoNoteDao = database.videoNoteDao(),
            journalContentDao = database.journalContentDao(),
            journalRepository = FakeJournalRepository(),
            mediaCaptionDao = database.mediaCaptionDao(),
            transactionManager = RoomSyncTransactionManager(database),
            syncMetadataService = metadata,
        )
    private val now = Instant.parse("2026-01-02T03:04:05Z")

    @AfterTest
    fun close() {
        database.close()
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `metadata replacement preserves original bytes and memberships and can clear captions`() =
        runTest {
            val file = directory.resolve("photo.jpg")
            val bytes = byteArrayOf(1, 2, 3, 4)
            Files.write(file, bytes)
            val note = JournalNote.Image(creationTimestamp = now, lastUpdated = now, mediaRef = file.toString(), caption = "Caption")
            repository.create(note)
            val journalId = Uuid.random()
            database.journalDao().create(JournalEntity(journalId, "Local journal", "", now, now))
            database.journalContentDao().addContentToJournal(JournalContentEntityLink(journalId, note.uid))
            metadata.clearPending()
            val expected = repository.getNoteById(note.uid) as JournalNote.Image

            assertTrue(repository.compareAndSetNoteForRestore(expected, expected.copy(caption = "", lastUpdated = now + 1.seconds)))

            assertEquals("", (repository.getNoteById(note.uid) as JournalNote.Image).caption)
            assertTrue(Files.readAllBytes(file).contentEquals(bytes))
            assertEquals(listOf(note.uid), database.journalContentDao().getContentForJournal(journalId).first())
            assertEquals(PendingOperation.UPDATE, metadata.getPendingUploads(EntityType.NOTE).single().operation)
        }

    @Test
    fun `stale recovery snapshot cannot overwrite a concurrent edit or recreate a deleted note`() =
        runTest {
            val note = JournalNote.Text(creationTimestamp = now, lastUpdated = now, content = "Original")
            repository.create(note)
            val expected = repository.getNoteById(note.uid)!!
            assertTrue(repository.compareAndSetNoteForRestore(expected, note.copy(content = "Local edit", lastUpdated = now + 1.seconds)))
            assertFalse(repository.compareAndSetNoteForRestore(expected, note.copy(content = "Archive")))
            assertEquals("Local edit", (repository.getNoteById(note.uid) as JournalNote.Text).content)
            metadata.clearPending()
            repository.removeById(note.uid)
            assertFalse(repository.compareAndSetNoteForRestore(expected, note))
            assertFalse(repository.compareAndSetNoteForRestore(null, note))
            assertEquals(PendingOperation.DELETE, metadata.getPendingUploads(EntityType.NOTE).single().operation)
        }

    @Test
    fun `missing original can be retained without a media index`() =
        runTest {
            val note =
                JournalNote.Video(
                    creationTimestamp = now,
                    lastUpdated = now,
                    mediaRef = "logdate-unavailable://archive/original",
                    caption = "Keep caption",
                )
            assertTrue(repository.compareAndSetNoteForRestore(null, note))
            assertEquals(note, repository.getNoteById(note.uid))
            assertEquals(PendingOperation.CREATE, metadata.getPendingUploads(EntityType.NOTE).single().operation)
        }

    @Test
    fun `Room recovery cannot deadlock behind the metadata service mutex`() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val provider =
                object : CanonicalOwnerProvider {
                    override suspend fun getCanonicalOwnerId(): String {
                        entered.complete(Unit)
                        release.await()
                        return "owner"
                    }

                    override suspend fun hasBoundOwner() = true
                }
            val metadata =
                DatabaseSyncMetadataService(
                    database.syncMetadataDao(),
                    DefaultLogDateConfigRepository(initialBackendUrl = "https://example.test"),
                    provider,
                )
            val scope = JournalMergeScope("owner", "https://example.test")
            val scoped =
                OfflineFirstJournalNotesRepository(
                    database.textNoteDao(),
                    database.imageNoteDao(),
                    database.audioNoteDao(),
                    database.videoNoteDao(),
                    database.journalContentDao(),
                    FakeJournalRepository(),
                    database.mediaCaptionDao(),
                    transactionManager = RoomSyncTransactionManager(database),
                    syncMetadataService = metadata,
                    currentRecoveryScope = { scope },
                )
            val blocking = async { metadata.getPendingUploads(EntityType.NOTE) }
            entered.await()
            try {
                val note = JournalNote.Text(creationTimestamp = now, lastUpdated = now, content = "Recovered without lock inversion")
                assertTrue(withContext(Dispatchers.IO) { withTimeout(5_000) { scoped.compareAndSetNoteForRestore(null, note, scope) } })
                assertFalse(blocking.isCompleted)
                assertEquals(
                    "CREATE",
                    database.syncMetadataDao().getPending(scope.ownerId, scope.serverOrigin, "NOTE", note.uid.toString())?.operation,
                )
            } finally {
                release.complete(Unit)
                blocking.await()
            }
        }

    @Test
    fun `account change inside persistence rolls back both note and captured outbox mutation`() =
        runTest {
            val scope = JournalMergeScope("owner-a", "https://one.example")
            val provider =
                object : CanonicalOwnerProvider {
                    override suspend fun getCanonicalOwnerId() = scope.ownerId

                    override suspend fun hasBoundOwner() = true
                }
            val metadata =
                DatabaseSyncMetadataService(
                    database.syncMetadataDao(),
                    DefaultLogDateConfigRepository(initialBackendUrl = scope.serverOrigin),
                    provider,
                )
            var reads = 0
            val scoped =
                OfflineFirstJournalNotesRepository(
                    database.textNoteDao(),
                    database.imageNoteDao(),
                    database.audioNoteDao(),
                    database.videoNoteDao(),
                    database.journalContentDao(),
                    FakeJournalRepository(),
                    database.mediaCaptionDao(),
                    transactionManager = RoomSyncTransactionManager(database),
                    syncMetadataService = metadata,
                    currentRecoveryScope = { if (++reads > 1) scope.copy(ownerId = "owner-b") else scope },
                )
            val note = JournalNote.Text(creationTimestamp = now, lastUpdated = now, content = "Must not cross owners")
            kotlin.test.assertFailsWith<IllegalStateException> { scoped.compareAndSetNoteForRestore(null, note, scope) }
            assertEquals(null, scoped.getNoteById(note.uid))
            assertEquals(null, database.syncMetadataDao().getPending(scope.ownerId, scope.serverOrigin, "NOTE", note.uid.toString()))
        }

    @Test
    fun `a committed note receipt prevents resurrection after deletion has settled`() =
        runTest {
            val scope = JournalMergeScope("receipt-owner", "https://example.test")
            val provider =
                object : CanonicalOwnerProvider {
                    override suspend fun getCanonicalOwnerId() = scope.ownerId

                    override suspend fun hasBoundOwner() = true
                }
            val metadata =
                DatabaseSyncMetadataService(
                    database.syncMetadataDao(),
                    DefaultLogDateConfigRepository(initialBackendUrl = scope.serverOrigin),
                    provider,
                )
            val scoped =
                OfflineFirstJournalNotesRepository(
                    database.textNoteDao(),
                    database.imageNoteDao(),
                    database.audioNoteDao(),
                    database.videoNoteDao(),
                    database.journalContentDao(),
                    FakeJournalRepository(),
                    database.mediaCaptionDao(),
                    transactionManager = RoomSyncTransactionManager(database),
                    syncMetadataService = metadata,
                    currentRecoveryScope = { scope },
                    recoveryReceipts =
                        app.logdate.client.data.journals
                            .RoomArchiveRecoveryReceipts(database.downloadInboxDao()),
                )
            val note = JournalNote.Text(creationTimestamp = now, lastUpdated = now, content = "Imported once")
            val recoveryId = Uuid.random().toString()
            assertTrue(scoped.compareAndSetNoteForRestore(null, note, scope, recoveryId))
            metadata.clearPending()
            scoped.removeById(note.uid)
            metadata.clearPending()
            assertFalse(scoped.compareAndSetNoteForRestore(null, note, scope, recoveryId))
            assertEquals(null, scoped.getNoteById(note.uid))
            assertEquals(0, database.downloadInboxDao().count(scope.ownerId, scope.serverOrigin))
        }
}
