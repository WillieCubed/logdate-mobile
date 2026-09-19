package app.logdate.client.database

import androidx.room.Room
import app.logdate.client.database.entities.AudioNoteEntity
import app.logdate.client.database.entities.ImageNoteEntity
import app.logdate.client.database.entities.TextNoteEntity
import app.logdate.client.database.entities.VideoNoteEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Verifies the creation-time queries each note table exposes for streak calculation, which
 * read only the `created` column instead of loading whole notes.
 */
class NoteCreatedTimestampsJvmTest {
    private var database: LogDateDatabase? = null
    private var databasePath = Files.createTempFile("logdate-created-timestamps", ".db")

    @AfterTest
    fun tearDown() {
        database?.close()
        database = null

        Files.deleteIfExists(databasePath)
        Files.deleteIfExists(databasePath.resolveSibling("${databasePath.fileName}-wal"))
        Files.deleteIfExists(databasePath.resolveSibling("${databasePath.fileName}-shm"))
    }

    @Test
    fun `each note table reports the creation time of its notes`() =
        runTest {
            val database = openDatabase()

            database.textNoteDao().addNote(
                TextNoteEntity(content = "Morning pages", created = at(1_000), lastUpdated = at(9_000)),
            )
            database.imageNoteDao().addNote(
                ImageNoteEntity(contentUri = "photo.jpg", created = at(2_000), lastUpdated = at(9_000)),
            )
            database.audioNoteDao().addNote(
                AudioNoteEntity(contentUri = "memo.m4a", created = at(3_000), lastUpdated = at(9_000)),
            )
            database.videoNoteDao().addNote(
                VideoNoteEntity(contentUri = "clip.mp4", created = at(4_000), lastUpdated = at(9_000)),
            )

            assertEquals(listOf(1_000L), database.textNoteDao().observeCreatedTimestamps().first())
            assertEquals(listOf(2_000L), database.imageNoteDao().observeCreatedTimestamps().first())
            assertEquals(listOf(3_000L), database.audioNoteDao().observeCreatedTimestamps().first())
            assertEquals(listOf(4_000L), database.videoNoteDao().observeCreatedTimestamps().first())
        }

    @Test
    fun `removing a note removes its creation time`() =
        runTest {
            val database = openDatabase()
            val note = TextNoteEntity(content = "Draft thought", created = at(1_000), lastUpdated = at(1_000))
            database.textNoteDao().addNote(note)

            database.textNoteDao().removeNote(note.uid)

            assertEquals(emptyList(), database.textNoteDao().observeCreatedTimestamps().first())
        }

    private fun at(millis: Long) = Instant.fromEpochMilliseconds(millis)

    private fun openDatabase(): LogDateDatabase {
        database?.close()
        Files.deleteIfExists(databasePath)
        databasePath = Files.createTempFile("logdate-created-timestamps", ".db")
        Files.deleteIfExists(databasePath)

        return getRoomDatabase(
            builder = Room.databaseBuilder<LogDateDatabase>(databasePath.absolutePathString()),
            destroyTablesOnUpgrade = true,
        ).also { database = it }
    }
}
