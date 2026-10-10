package app.logdate.client.data.notes

import androidx.room.Room
import app.logdate.client.data.fakes.FakeJournalRepository
import app.logdate.client.data.fakes.FakeSyncMetadataService
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.media.storage.StoredMediaReferences
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.mediaRefOrNull
import app.logdate.client.sync.RoomSyncTransactionManager
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Notes store one spelling of every media file, so deciding whether a file is still in use can
 * never be fooled by another spelling of the same file.
 */
class StoredMediaReferencePersistenceTest {
    private lateinit var file: Path
    private lateinit var database: LogDateDatabase
    private lateinit var repository: OfflineFirstJournalNotesRepository
    private lateinit var metadata: FakeSyncMetadataService
    private val mediaManager = InMemoryMediaManager()
    private val created = Instant.parse("2026-01-02T03:04:05Z")

    /** Every spelling of `/install/media/<name>` is stored as `logdate-media://library/<name>`. */
    private val references =
        StoredMediaReferences { reference ->
            val name =
                listOf("file:///install/media/", "file:/install/media/", "/install/media/")
                    .firstOrNull(reference::startsWith)
                    ?.let(reference::removePrefix)
            if (name == null) reference else "logdate-media://library/$name"
        }

    @BeforeTest
    fun setup() {
        file = Files.createTempFile("logdate-media-references-", ".db")
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
                mediaManager = mediaManager,
                transactionManager = RoomSyncTransactionManager(database),
                syncMetadataService = metadata,
                mediaReferences = references,
            )
    }

    @AfterTest
    fun tearDown() {
        database.close()
        Files.deleteIfExists(file)
    }

    @Test
    fun `new notes store the media reference in its canonical spelling`() =
        runTest {
            val image = image("file:///install/media/beach.jpg")
            val audio = audio("/install/media/walk.m4a")
            repository.create(image)
            repository.create(audio)

            assertEquals("logdate-media://library/beach.jpg", repository.getNoteById(image.uid)?.mediaRefOrNull())
            assertEquals("logdate-media://library/walk.m4a", repository.getNoteById(audio.uid)?.mediaRefOrNull())
        }

    @Test
    fun `notes from sync and media repairs store the canonical spelling`() =
        runTest {
            val image = image("file:/install/media/synced.jpg")
            repository.createFromSync(image)
            assertEquals("logdate-media://library/synced.jpg", repository.getNoteById(image.uid)?.mediaRefOrNull())

            repository.updateMediaRef(image.uid, "/install/media/repaired.jpg")
            assertEquals("logdate-media://library/repaired.jpg", repository.getNoteById(image.uid)?.mediaRefOrNull())
        }

    @Test
    fun `replaying a note with another spelling of its media is not a conflict`() =
        runTest {
            val image = image("/install/media/beach.jpg")
            repository.create(image)

            assertEquals(image.uid, repository.create(image.copy(mediaRef = "file:///install/media/beach.jpg")))
        }

    @Test
    fun `replaying a create over a row not yet migrated is not a conflict`() =
        runTest {
            val legacy = image("file:///install/media/beach.jpg")
            database.imageNoteDao().addNote(legacy.toEntity())

            assertEquals(legacy.uid, repository.create(legacy))
            assertEquals("file:///install/media/beach.jpg", contentUriOf(legacy.uid))
        }

    @Test
    fun `a media ref update made after the migration read is not overwritten`() =
        runTest {
            val dao = database.imageNoteDao()
            val legacy = image("file:///install/media/old.jpg")
            dao.addNote(legacy.toEntity())
            dao.updateContentUri(legacy.uid, "logdate-media://library/repaired.jpg")

            dao.updateContentUriIfUnchanged(legacy.uid, "file:///install/media/old.jpg", "logdate-media://library/old.jpg")

            assertEquals("logdate-media://library/repaired.jpg", contentUriOf(legacy.uid))
        }

    @Test
    fun `a file is reported in use under any spelling`() =
        runTest {
            repository.create(image("/install/media/beach.jpg"))

            val referenced =
                repository.notesReferencingMediaPaths(
                    setOf("file:///install/media/beach.jpg", "/install/media/beach.jpg", "/install/media/other.jpg"),
                )

            assertEquals(setOf("file:///install/media/beach.jpg", "/install/media/beach.jpg"), referenced)
        }

    @Test
    fun `deleting a note keeps a file another note stores under an older spelling`() =
        runTest {
            val legacy = image("/install/media/shared.jpg")
            database.imageNoteDao().addNote(legacy.toEntity())
            val current = image("file:///install/media/shared.jpg")
            repository.create(current)

            repository.remove(legacy)

            assertTrue(mediaManager.deletedOwnedMediaUris.isEmpty(), "Deleted ${mediaManager.deletedOwnedMediaUris}")
        }

    @Test
    fun `migration rewrites legacy local file references and leaves everything else`() =
        runTest {
            val dao = database.imageNoteDao()
            val legacy = image("file:///install/media/old.jpg")
            val elsewhere = image("/elsewhere/photo.jpg")
            val photos = image("ph://ABC/L0/001")
            val current = image("logdate-media://library/new.jpg")
            listOf(legacy, elsewhere, photos, current).forEach { dao.addNote(it.toEntity()) }
            metadata.clearPending()

            val rewritten =
                StoredMediaReferenceMigration(dao, database.audioNoteDao(), database.videoNoteDao(), references).run()

            assertEquals(1, rewritten)
            assertEquals("logdate-media://library/old.jpg", contentUriOf(legacy.uid))
            assertEquals("/elsewhere/photo.jpg", contentUriOf(elsewhere.uid))
            assertEquals("ph://ABC/L0/001", contentUriOf(photos.uid))
            assertEquals("logdate-media://library/new.jpg", contentUriOf(current.uid))
            assertEquals(0, metadata.getPendingCount(), "Rewriting a local reference must not queue an upload")
            assertEquals(0, StoredMediaReferenceMigration(dao, database.audioNoteDao(), database.videoNoteDao(), references).run())
        }

    @Test
    fun `the launcher runs the migration in the background`() =
        runTest {
            val legacy = image("file:///install/media/old.jpg")
            database.imageNoteDao().addNote(legacy.toEntity())
            val migration =
                StoredMediaReferenceMigration(database.imageNoteDao(), database.audioNoteDao(), database.videoNoteDao(), references)

            StoredMediaReferenceMigrationLauncher(migration, scope = this).start().join()

            assertEquals("logdate-media://library/old.jpg", contentUriOf(legacy.uid))
        }

    private suspend fun contentUriOf(uid: Uuid): String? = repository.getNoteById(uid)?.mediaRefOrNull()

    private fun image(mediaRef: String) = JournalNote.Image(creationTimestamp = created, lastUpdated = created, mediaRef = mediaRef)

    private fun audio(mediaRef: String) = JournalNote.Audio(creationTimestamp = created, lastUpdated = created, mediaRef = mediaRef)
}
