package app.logdate.client.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.logdate.client.database.migrations.MIGRATION_59_60
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Validates migration 60, which indexes notes and indexed media by time and changes no rows. */
class TimeIndexMigrationJvmTest {
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
    fun `every note from version 59 is kept`() {
        helper.createDatabase(59).use { connection ->
            connection.execSQL(
                "INSERT INTO text_notes (content, uid, lastUpdated, created, syncVersion) " +
                    "VALUES ('Morning walk', 'note-1', 1710000000001, 1710000000000, 2)",
            )
        }

        helper.runMigrationsAndValidate(60, listOf(MIGRATION_59_60)).use { connection ->
            connection.prepare("SELECT content, created, syncVersion FROM text_notes WHERE uid = 'note-1'").use { row ->
                assertTrue(row.step(), "the note is gone after the migration")
                assertEquals("Morning walk", row.getText(0))
                assertEquals(1710000000000L, row.getLong(1))
                assertEquals(2L, row.getLong(2))
            }
        }
    }

    @Test
    fun `newest-first note and media reads use the time index`() {
        helper.createDatabase(59).close()

        helper.runMigrationsAndValidate(60, listOf(MIGRATION_59_60)).use { connection ->
            for (table in listOf("text_notes", "image_notes", "audio_notes", "video_notes")) {
                val plan = connection.queryPlan("SELECT * FROM $table ORDER BY created DESC LIMIT 20")
                assertTrue("index_${table}_created" in plan, "$table pages are sorted without the index: $plan")
            }
            for (table in listOf("indexed_media_images", "indexed_media_videos")) {
                val plan = connection.queryPlan("SELECT * FROM $table WHERE timestamp >= 0 AND timestamp < 10 ORDER BY timestamp ASC")
                assertTrue("index_${table}_timestamp" in plan, "$table ranges scan the table: $plan")
            }
        }
    }

    private fun SQLiteConnection.queryPlan(sql: String): String =
        prepare("EXPLAIN QUERY PLAN $sql").use { row ->
            buildString {
                while (row.step()) appendLine(row.getText(3))
            }
        }
}
