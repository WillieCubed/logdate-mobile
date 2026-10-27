package app.logdate.client.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.logdate.client.database.migrations.MIGRATION_58_59
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HandwritingNoteMigrationTest {
    @Test
    fun retainedTextRemainsPlainTextAfterAddingInkFormat() {
        val file = Files.createTempFile("logdate-ink-migration-", ".db")
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
            helper.createDatabase(58).use { connection ->
                connection.execSQL(
                    """INSERT INTO text_notes (uid, content, lastUpdated, created, syncVersion)
                       VALUES ('retained', 'Keep these words', 2000, 1000, 7)""",
                )
            }
            helper.runMigrationsAndValidate(59, listOf(MIGRATION_58_59)).use { connection ->
                val query = "SELECT content, content_format, created, syncVersion FROM text_notes WHERE uid = 'retained'"
                connection.prepare(query).use { row ->
                    assertTrue(row.step())
                    assertEquals("Keep these words", row.getText(0))
                    assertEquals("text", row.getText(1))
                    assertEquals(1000L, row.getLong(2))
                    assertEquals(7L, row.getLong(3))
                }
            }
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(file.resolveSibling("${file.fileName}-wal"))
            Files.deleteIfExists(file.resolveSibling("${file.fileName}-shm"))
        }
    }
}
