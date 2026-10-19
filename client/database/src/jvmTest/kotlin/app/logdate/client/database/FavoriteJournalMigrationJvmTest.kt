package app.logdate.client.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.logdate.client.database.migrations.MIGRATION_56_57
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Validates migration 57, which adds a journal's favorite flag and changes nothing else. */
class FavoriteJournalMigrationJvmTest {
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
    fun `every journal from version 56 is kept and starts as not a favorite`() {
        helper.createDatabase(56).use { connection ->
            connection.execSQL(
                "INSERT INTO journals (id, title, description, created, lastUpdated, syncVersion, coverImageUri) " +
                    "VALUES ('journal-1', 'Daily', 'Every day', 1710000000000, 1710000000001, 4, 'logdate-media://covers/a.jpg')",
            )
            connection.execSQL(
                "INSERT INTO journals (id, title, description, created, lastUpdated, syncVersion) " +
                    "VALUES ('journal-2', 'Trips', '', 1710000000000, 1710000000000, 0)",
            )
        }

        helper.runMigrationsAndValidate(57, listOf(MIGRATION_56_57)).use { connection ->
            connection.prepare("SELECT title, syncVersion, coverImageUri, isFavorited FROM journals WHERE id = 'journal-1'").use { row ->
                assertTrue(row.step(), "the journal is gone after the migration")
                assertEquals("Daily", row.getText(0))
                assertEquals(4L, row.getLong(1))
                assertEquals("logdate-media://covers/a.jpg", row.getText(2))
                assertEquals(0L, row.getLong(3))
            }
            connection.prepare("SELECT COUNT(*) FROM journals WHERE isFavorited = 0").use { row ->
                assertTrue(row.step())
                assertEquals(2L, row.getLong(0))
            }
        }
    }

    @Test
    fun `a journal added after the migration can be a favorite`() {
        helper.createDatabase(56).close()

        helper.runMigrationsAndValidate(57, listOf(MIGRATION_56_57)).use { connection ->
            connection.execSQL(
                "INSERT INTO journals (id, title, description, created, lastUpdated, syncVersion, isFavorited) " +
                    "VALUES ('journal-3', 'Best of', '', 1710000000000, 1710000000000, 0, 1)",
            )
            connection.prepare("SELECT isFavorited FROM journals WHERE id = 'journal-3'").use { row ->
                assertTrue(row.step())
                assertEquals(1L, row.getLong(0))
            }
        }
    }
}
