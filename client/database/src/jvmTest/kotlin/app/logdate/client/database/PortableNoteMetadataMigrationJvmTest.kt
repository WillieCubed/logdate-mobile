package app.logdate.client.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.logdate.client.database.migrations.MIGRATION_57_58
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortableNoteMetadataMigrationJvmTest {
    private val file = Files.createTempFile("logdate-portable-note-migration-", ".db")
    private val helper =
        MigrationTestHelper(
            Path.of("schemas"),
            file,
            BundledSQLiteDriver(),
            LogDateDatabase::class,
            { LogDateDatabaseConstructor.initialize() },
            emptyList(),
        )

    @AfterTest
    fun close() {
        listOf(
            file,
            file.resolveSibling("${file.fileName}-wal"),
            file.resolveSibling("${file.fileName}-shm"),
        ).forEach(Files::deleteIfExists)
    }

    @Test
    fun repeatedMigrationRetainsExistingCanonicalBytesAndRawAudioText() {
        helper.createDatabase(58).use { connection ->
            connection.execSQL(
                "INSERT INTO audio_notes (uid,contentUri,durationMs,created,lastUpdated,syncVersion,location_metadata,transcription) " +
                    "VALUES ('audio','logdate-media://audio',4000,1,2,3,'{preserve these exact bytes}','  original text  ')",
            )
            repeat(2) { MIGRATION_57_58.migrate(connection) }
            connection.prepare("SELECT location_metadata,transcription,syncVersion FROM audio_notes WHERE uid='audio'").use { row ->
                assertTrue(row.step())
                assertEquals("{preserve these exact bytes}", row.getText(0))
                assertEquals("  original text  ", row.getText(1))
                assertEquals(3L, row.getLong(2))
            }
        }
    }

    @Test
    fun migrationPreservesEveryNoteAndStartsWithUnknownCanonicalLocation() {
        helper.createDatabase(57).use { connection ->
            connection.execSQL(
                "INSERT INTO text_notes (uid,content,created,lastUpdated,syncVersion,latitude,longitude) VALUES ('text','Keep text',1,2,3,36.1,-115.2)",
            )
            connection.execSQL(
                "INSERT INTO image_notes (uid,contentUri,created,lastUpdated,syncVersion) VALUES ('image','logdate-media://image',1,2,3)",
            )
            connection.execSQL(
                "INSERT INTO video_notes (uid,contentUri,durationMs,created,lastUpdated,syncVersion) VALUES ('video','logdate-media://video',12345,1,2,3)",
            )
            connection.execSQL(
                "INSERT INTO audio_notes (uid,contentUri,durationMs,created,lastUpdated,syncVersion) VALUES ('audio','logdate-media://audio',4000,1,2,3)",
            )
        }
        helper.runMigrationsAndValidate(58, listOf(MIGRATION_57_58)).use { connection ->
            listOf("text_notes", "image_notes", "video_notes", "audio_notes").forEach { table ->
                connection.prepare("SELECT COUNT(*), MIN(syncVersion) FROM `$table` WHERE location_metadata IS NULL").use { row ->
                    assertTrue(row.step())
                    assertEquals(1L, row.getLong(0))
                    assertEquals(3L, row.getLong(1))
                }
            }
            connection.prepare("SELECT contentUri,durationMs,transcription FROM audio_notes WHERE uid='audio'").use { row ->
                assertTrue(row.step())
                assertEquals("logdate-media://audio", row.getText(0))
                assertEquals(4000L, row.getLong(1))
                assertEquals("", row.getText(2))
            }
            connection.prepare("SELECT content,latitude,longitude FROM text_notes WHERE uid='text'").use { row ->
                assertTrue(row.step())
                assertEquals("Keep text", row.getText(0))
                assertEquals(36.1, row.getDouble(1))
                assertEquals(-115.2, row.getDouble(2))
            }
        }
    }
}
