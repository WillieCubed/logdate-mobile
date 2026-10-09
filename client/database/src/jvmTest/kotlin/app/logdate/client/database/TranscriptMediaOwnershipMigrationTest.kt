package app.logdate.client.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.logdate.client.database.migrations.MIGRATION_50_51
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranscriptMediaOwnershipMigrationTest {
    @Test
    fun `migration binds retained transcripts to their audio without changing text or revision`() {
        val file = Files.createTempFile("logdate-transcript-migration-", ".db")
        val helper =
            MigrationTestHelper(
                Path.of("schemas"),
                file,
                BundledSQLiteDriver(),
                LogDateDatabase::class,
                { LogDateDatabaseConstructor.initialize() },
                emptyList(),
            )
        try {
            helper.createDatabase(50).use { connection ->
                connection.execSQL(
                    """INSERT INTO audio_notes (uid, contentUri, durationMs, lastUpdated, created, syncVersion)
                       VALUES ('note-1', 'retained.m4a', 4500, 1000, 1000, 0)""",
                )
                connection.execSQL(
                    """INSERT INTO transcriptions (id, noteId, text, revision, isCloudEnhanced, speakerCount, status, created, lastUpdated)
                       VALUES ('transcript-1', 'note-1', 'Retained words', 8, 0, 0, 'COMPLETED', 1000, 1000)""",
                )
            }
            helper.runMigrationsAndValidate(51, listOf(MIGRATION_50_51)).use { connection ->
                connection.prepare("SELECT text, revision, mediaUri, mediaDurationMs FROM transcriptions").use { row ->
                    assertTrue(row.step())
                    assertEquals("Retained words", row.getText(0))
                    assertEquals(8L, row.getLong(1))
                    assertEquals("retained.m4a", row.getText(2))
                    assertEquals(4500L, row.getLong(3))
                }
            }
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(file.resolveSibling("${file.fileName}-wal"))
            Files.deleteIfExists(file.resolveSibling("${file.fileName}-shm"))
        }
    }
}
