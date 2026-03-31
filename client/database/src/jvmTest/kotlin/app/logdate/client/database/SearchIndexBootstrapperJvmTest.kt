package app.logdate.client.database

import androidx.room.Room
import app.logdate.client.database.entities.AudioNoteEntity
import app.logdate.client.database.entities.TextNoteEntity
import app.logdate.client.database.entities.TranscriptionEntity
import app.logdate.client.database.entities.TranscriptionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class SearchIndexBootstrapperJvmTest {
    private var database: LogDateDatabase? = null
    private var databasePath = Files.createTempFile("logdate-search-index", ".db")

    @AfterTest
    fun tearDown() {
        database?.close()
        database = null

        Files.deleteIfExists(databasePath)
        Files.deleteIfExists(databasePath.resolveSibling("${databasePath.fileName}-wal"))
        Files.deleteIfExists(databasePath.resolveSibling("${databasePath.fileName}-shm"))
    }

    @Test
    fun freshDatabaseIndexesInsertedTextNotes() =
        runTest {
            val database = openDatabase()
            val note =
                TextNoteEntity(
                    content = "Hiked the sunrise trail before breakfast.",
                    uid = Uuid.random(),
                    created = Instant.fromEpochMilliseconds(1_000),
                    lastUpdated = Instant.fromEpochMilliseconds(1_000),
                )

            database.textNoteDao().addNote(note)

            val results = database.searchDao().searchRanked("hik*", limit = 10)

            assertEquals(1, results.size)
            assertEquals(note.uid.toString(), results.single().uid)
            assertTrue(database.searchDao().observeGeneration().first() ?: 0L > 0L)
        }

    @Test
    fun transcriptionSearchUsesSourceAudioNoteDateAndDeletesWithSourceNote() =
        runTest {
            val database = openDatabase()
            val audioNote =
                AudioNoteEntity(
                    contentUri = "memo.m4a",
                    uid = Uuid.random(),
                    created = Instant.fromEpochMilliseconds(10_000),
                    lastUpdated = Instant.fromEpochMilliseconds(10_000),
                )
            val transcription =
                TranscriptionEntity(
                    id = Uuid.random(),
                    noteId = audioNote.uid,
                    text = "Adventures on the coastal trail",
                    status = TranscriptionStatus.COMPLETED,
                    created = Instant.fromEpochMilliseconds(20_000),
                    lastUpdated = Instant.fromEpochMilliseconds(25_000),
                )

            database.audioNoteDao().addNote(audioNote)
            database.transcriptionDao().insertTranscription(transcription)

            val initialResults = database.searchDao().searchRanked("advent*", limit = 10)

            assertEquals(1, initialResults.size)
            assertEquals(audioNote.created.toEpochMilliseconds(), initialResults.single().created)

            database.audioNoteDao().removeNote(audioNote.uid)

            val afterDelete = database.searchDao().searchRanked("advent*", limit = 10)
            assertTrue(afterDelete.isEmpty())
        }

    private fun openDatabase(): LogDateDatabase {
        database?.close()
        Files.deleteIfExists(databasePath)
        databasePath = Files.createTempFile("logdate-search-index", ".db")
        Files.deleteIfExists(databasePath)

        return getRoomDatabase(
            builder = Room.databaseBuilder<LogDateDatabase>(databasePath.absolutePathString()),
            destroyTablesOnUpgrade = true,
        ).also { database = it }
    }
}
