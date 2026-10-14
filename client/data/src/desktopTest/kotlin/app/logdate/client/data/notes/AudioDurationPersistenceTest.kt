package app.logdate.client.data.notes

import androidx.room.Room
import app.logdate.client.data.fakes.FakeJournalRepository
import app.logdate.client.data.fakes.FakeSyncMetadataService
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.repository.journals.CaptureDevice
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.RoomSyncTransactionManager
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class AudioDurationPersistenceTest {
    private lateinit var file: Path
    private lateinit var database: LogDateDatabase
    private lateinit var repository: OfflineFirstJournalNotesRepository
    private lateinit var metadata: FakeSyncMetadataService
    private var rejectOutbox = false
    private val created = Instant.parse("2026-01-02T03:04:05.123456Z")

    @BeforeTest
    fun setup() {
        file = Files.createTempFile("logdate-duplicate-note-", ".db")
        database = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(file.toString()))
        metadata =
            object : FakeSyncMetadataService() {
                override suspend fun enqueuePending(
                    entityId: String,
                    entityType: EntityType,
                    operation: PendingOperation,
                ) {
                    check(!rejectOutbox) { "Outbox unavailable" }
                    super.enqueuePending(entityId, entityType, operation)
                }
            }
        repository =
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
    }

    @AfterTest
    fun tearDown() {
        database.close()
        Files.deleteIfExists(file)
    }

    @Test
    fun missingDurationIsFilledWithoutChangingOtherMetadata() =
        runTest {
            val original = audio()
            repository.create(original)
            val retained = repository.getNoteById(original.uid) as JournalNote.Audio
            metadata.clearPending()
            assertTrue(repository.updateAudioDurationIfUnknown(original.uid, original.mediaRef, 3_888))
            assertEquals(retained.copy(durationMs = 3_888), repository.getNoteById(original.uid))
            val pending = metadata.getPendingUploads(EntityType.NOTE).single()
            assertEquals(original.uid.toString(), pending.entityId)
            assertEquals(PendingOperation.UPDATE, pending.operation)
        }

    @Test
    fun staleMediaKnownDurationAndDeletedNotesAreNeverOverwritten() =
        runTest {
            val original = audio()
            repository.create(original)
            metadata.clearPending()
            assertFalse(repository.updateAudioDurationIfUnknown(original.uid, "file:///other.wav", 3_888))
            assertFalse(repository.updateAudioDurationIfUnknown(original.uid, original.mediaRef, 0))
            assertTrue(repository.updateAudioDurationIfUnknown(original.uid, original.mediaRef, 3_888))
            metadata.clearPending()
            assertFalse(repository.updateAudioDurationIfUnknown(original.uid, original.mediaRef, 9_999))
            assertEquals(3_888L, (repository.getNoteById(original.uid) as JournalNote.Audio).durationMs)
            database.audioNoteDao().removeNote(original.uid)
            assertFalse(repository.updateAudioDurationIfUnknown(original.uid, original.mediaRef, 7_000))
            assertEquals(0, metadata.getPendingCount())
        }

    @Test
    fun tombstonedAudioCannotBeRepairedOrResurrected() =
        runTest {
            val original = audio()
            repository.create(original)
            val entity = database.audioNoteDao().getNoteOneOff(original.uid)
            database.audioNoteDao().removeNote(original.uid)
            database.audioNoteDao().addNote(entity.copy(deletedAt = created))
            val retained = database.audioNoteDao().getNoteOneOff(original.uid)
            metadata.clearPending()
            assertFalse(repository.updateAudioDurationIfUnknown(original.uid, original.mediaRef, 3_888))
            assertEquals(retained, database.audioNoteDao().getNoteOneOff(original.uid))
            assertEquals(0, metadata.getPendingCount())
        }

    @Test
    fun outboxFailureRollsBackTheDurationWrite() =
        runTest {
            val original = audio()
            repository.create(original)
            metadata.clearPending()
            rejectOutbox = true
            assertFailsWith<IllegalStateException> {
                repository.updateAudioDurationIfUnknown(original.uid, original.mediaRef, 3_888)
            }
            assertEquals(0L, (repository.getNoteById(original.uid) as JournalNote.Audio).durationMs)
            assertEquals(0, metadata.getPendingCount())
        }

    private fun audio() =
        JournalNote.Audio(
            creationTimestamp = created,
            lastUpdated = created,
            mediaRef = "file:///retained/shared.wav",
            caption = "My retained caption",
            capturedOn = CaptureDevice.WATCH,
            timeZoneId = "America/Los_Angeles",
        )
}
