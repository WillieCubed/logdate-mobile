package app.logdate.client.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.logdate.client.database.migrations.MIGRATION_48_49
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the 48 to 49 migration through Room's own schema validation on the exported schemas, the same
 * check Room makes when the app opens an upgraded database. It needs no device.
 */
class PhotoPresentationMigrationJvmTest {
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
    }

    @Test
    fun `room preserves existing photos when adding presentation`() {
        helper.createDatabase(48).use { connection ->
            connection.execSQL(
                "INSERT INTO image_notes (uid, contentUri, lastUpdated, created, syncVersion) " +
                    "VALUES ('note-1', 'photo.jpg', 1710000000000, 1710000000000, 0)",
            )
        }

        helper.runMigrationsAndValidate(49, listOf(MIGRATION_48_49)).use { connection ->
            connection.prepare("SELECT contentUri, presentation FROM image_notes WHERE uid = 'note-1'").use { row ->
                assertTrue(row.step(), "the note is gone after the migration")
                assertEquals("photo.jpg", row.getText(0))
                assertEquals("EdgeToEdge", row.getText(1))
            }
        }
    }
}
