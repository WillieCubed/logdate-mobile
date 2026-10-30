package app.logdate.client.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.logdate.client.database.migrations.MIGRATION_18_19
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Validates migration 19, which rebuilds the note, journal and link tables with sync metadata. */
class SyncMetadataMigrationJvmTest {
    private val databaseFile = Files.createTempFile("logdate-migration", ".db")

    private val helper =
        MigrationTestHelper(
            Path.of("schemas"),
            databaseFile,
            BundledSQLiteDriver(),
            LogDateDatabase::class,
            { LogDateDatabaseConstructor.initialize() },
            emptyList(),
        )

    @AfterTest
    fun tearDown() {
        Files.deleteIfExists(databaseFile)
        Files.deleteIfExists(databaseFile.resolveSibling("${databaseFile.fileName}-wal"))
        Files.deleteIfExists(databaseFile.resolveSibling("${databaseFile.fileName}-shm"))
    }

    @Test
    fun `every note, journal and link from version 18 is kept`() {
        helper.createDatabase(18).use { connection -> connection.seedVersion18() }

        helper.runMigrationsAndValidate(19, listOf(MIGRATION_18_19)).use { connection ->
            assertEquals(1L, connection.count("text_notes WHERE content = 'Morning walk' AND syncVersion = 0"))
            assertEquals(1L, connection.count("voice_notes WHERE contentUri = 'file:///voice.m4a' AND durationMs = 4200"))
            assertEquals(1L, connection.count("video_notes WHERE contentUri = 'file:///clip.mp4'"))
            assertEquals(1L, connection.count("journal_content_links WHERE journal_id = 'journal-1' AND content_id = 'note-1'"))
        }
    }

    @Test
    fun `an install that never had the audio, video or link tables migrates the rest`() {
        helper.createDatabase(18).use { connection ->
            connection.seedVersion18()
            connection.execSQL("DROP TABLE voice_notes")
            connection.execSQL("DROP TABLE video_notes")
            connection.execSQL("DROP TABLE journal_content_links")

            MIGRATION_18_19.migrate(connection)

            assertEquals(1L, connection.count("text_notes WHERE content = 'Morning walk'"))
            assertFalse(connection.hasTable("voice_notes_new"), "a half-built voice notes table was left behind")
            assertFalse(connection.hasTable("video_notes_new"), "a half-built video notes table was left behind")
            assertFalse(connection.hasTable("journal_content_links_new"), "a half-built link table was left behind")
        }
    }

    @Test
    fun `a table that exists but cannot be copied stops the migration`() {
        helper.createDatabase(18).use { connection ->
            connection.seedVersion18()
            connection.execSQL("DROP TABLE voice_notes")
            connection.execSQL("CREATE TABLE voice_notes (uid TEXT NOT NULL PRIMARY KEY)")
            connection.execSQL("INSERT INTO voice_notes (uid) VALUES ('voice-1')")

            assertFailsWith<Exception> { MIGRATION_18_19.migrate(connection) }
            assertTrue(connection.hasTable("voice_notes"))
        }
    }

    private fun SQLiteConnection.seedVersion18() {
        execSQL(
            "INSERT INTO text_notes (content, uid, lastUpdated, created, syncVersion) " +
                "VALUES ('Morning walk', 'note-1', 1710000000001, 1710000000000, 3)",
        )
        execSQL(
            "INSERT INTO voice_notes (contentUri, durationMs, uid, lastUpdated, created, syncVersion) " +
                "VALUES ('file:///voice.m4a', 4200, 'voice-1', 1710000000001, 1710000000000, 0)",
        )
        execSQL(
            "INSERT INTO video_notes (contentUri, durationMs, uid, lastUpdated, created) " +
                "VALUES ('file:///clip.mp4', 1000, 'video-1', 1710000000001, 1710000000000)",
        )
        execSQL(
            "INSERT INTO journals (id, title, description, created, lastUpdated) " +
                "VALUES ('journal-1', 'Daily', '', 1710000000000, 1710000000000)",
        )
        execSQL("INSERT INTO journal_content_links (journal_id, content_id) VALUES ('journal-1', 'note-1')")
    }

    private fun SQLiteConnection.count(fromWhere: String): Long =
        prepare("SELECT COUNT(*) FROM $fromWhere").use { row ->
            assertTrue(row.step())
            row.getLong(0)
        }

    private fun SQLiteConnection.hasTable(name: String): Boolean =
        prepare("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?").use { row ->
            row.bindText(1, name)
            row.step()
        }
}
