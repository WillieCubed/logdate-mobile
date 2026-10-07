package app.logdate.client.data.notes

import androidx.room.Room
import app.logdate.client.data.fakes.FakeJournalRepository
import app.logdate.client.data.fakes.FakeSyncMetadataService
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.RoomSyncTransactionManager
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class DuplicateNotePersistenceTest {
    private lateinit var file: Path
    private lateinit var database: LogDateDatabase
    private lateinit var repository: OfflineFirstJournalNotesRepository
    private lateinit var metadata: FakeSyncMetadataService
    private val created = Instant.parse("2026-01-02T03:04:05.123456Z")

    @BeforeTest
    fun setup() {
        file = Files.createTempFile("logdate-duplicate-note-", ".db")
        database = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(file.toString()))
        metadata = FakeSyncMetadataService()
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
    fun `conflicting duplicate text fails visibly and preserves the stored note`() =
        runTest {
            val original = JournalNote.Text(creationTimestamp = created, lastUpdated = created, content = "Saved text")
            repository.create(original)
            val stored = repository.getNoteById(original.uid)
            metadata.clearPending()

            val failure =
                assertFailsWith<IllegalStateException> {
                    repository.create(original.copy(content = "Edited text", lastUpdated = created + 1.seconds))
                }

            assertTrue(failure.message.orEmpty().contains("already exists"))
            assertTrue(failure.message.orEmpty().contains("different content"))
            assertEquals(stored, repository.getNoteById(original.uid))
            assertEquals(0, metadata.getPendingCount(), "Failed duplicate create must not queue a successful upload")
        }

    @Test
    fun `conflicting duplicate media captions cannot change existing sidecars`() =
        runTest {
            val image =
                JournalNote.Image(
                    creationTimestamp = created,
                    lastUpdated = created,
                    mediaRef = "/media/station.jpg",
                    caption = "Original image caption",
                )
            val audio =
                JournalNote.Audio(
                    creationTimestamp = created,
                    lastUpdated = created,
                    mediaRef = "/media/train.m4a",
                    caption = "Original audio caption",
                )
            val video =
                JournalNote.Video(
                    creationTimestamp = created,
                    lastUpdated = created,
                    mediaRef = "/media/trip.mp4",
                    caption = "Original video caption",
                )
            listOf(image, audio, video).forEach { repository.create(it) }
            val originals = listOf(image, audio, video).associate { it.uid to repository.getNoteById(it.uid) }
            metadata.clearPending()

            listOf(image.copy(caption = ""), audio.copy(caption = ""), video.copy(caption = "Edited caption")).forEach { edited ->
                assertFailsWith<IllegalStateException> { repository.create(edited) }
                assertEquals(originals.getValue(edited.uid), repository.getNoteById(edited.uid))
            }

            assertEquals(0, metadata.getPendingCount())
        }

    @Test
    fun `identical create replay accepts fresh bookkeeping without changing persisted content`() =
        runTest {
            val original = JournalNote.Text(creationTimestamp = created, lastUpdated = created, content = "Saved text")
            repository.create(original)
            val stored = repository.getNoteById(original.uid)
            metadata.clearPending()

            val id = repository.create(original.copy(lastUpdated = created + 1.seconds, syncVersion = 4))

            assertEquals(original.uid, id)
            assertEquals(stored, repository.getNoteById(original.uid))
        }
}
