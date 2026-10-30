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
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

class BatchNoteLookupTest {
    private val file = Files.createTempFile("logdate-batch-notes-", ".db")
    private val database = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(file.toString()))
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
            syncMetadataService = FakeSyncMetadataService(),
        )
    private val created = Instant.parse("2026-01-02T03:04:05Z")

    @AfterTest
    fun tearDown() {
        database.close()
        Files.deleteIfExists(file)
    }

    @Test
    fun `notes of every kind are found together and match single lookups`() =
        runTest {
            val text = JournalNote.Text(creationTimestamp = created, lastUpdated = created, content = "Morning walk")
            val image =
                JournalNote.Image(
                    creationTimestamp = created,
                    lastUpdated = created,
                    mediaRef = "/media/station.jpg",
                    caption = "Platform at dawn",
                )
            repository.create(text)
            repository.create(image)
            val missing = Uuid.random()

            val found = repository.getNotesByIds(setOf(text.uid, image.uid, missing))

            assertEquals(setOf(text.uid, image.uid), found.keys)
            assertEquals(repository.getNoteById(text.uid), found[text.uid])
            assertEquals(repository.getNoteById(image.uid), found[image.uid])
        }

    @Test
    fun `more notes than one query can bind are all found`() =
        runTest {
            val notes = List(1_200) { JournalNote.Text(creationTimestamp = created, lastUpdated = created, content = "Note $it") }
            notes.forEach { repository.create(it) }

            assertEquals(notes.map { it.uid }.toSet(), repository.getNotesByIds(notes.map { it.uid }.toSet()).keys)
        }
}
