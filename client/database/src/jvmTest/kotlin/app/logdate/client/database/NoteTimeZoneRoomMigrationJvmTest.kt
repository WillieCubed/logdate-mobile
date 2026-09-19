package app.logdate.client.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.logdate.client.database.migrations.MIGRATION_46_47
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the 46 to 47 migration through Room's own schema validation on the exported schemas, the same
 * check Room makes when the app opens an upgraded database. It needs no device.
 */
class NoteTimeZoneRoomMigrationJvmTest {
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
    fun `room accepts the migrated schema and existing notes get no capture time zone`() {
        helper.createDatabase(46).use { connection ->
            connection.execSQL(
                "INSERT INTO text_notes (uid, content, lastUpdated, created, syncVersion) " +
                    "VALUES ('note-1', 'Written before time zones were recorded', 1710000000000, 1710000000000, 0)",
            )
        }

        helper.runMigrationsAndValidate(47, listOf(MIGRATION_46_47)).use { connection ->
            connection.prepare("SELECT content, time_zone_id FROM text_notes WHERE uid = 'note-1'").use { row ->
                assertTrue(row.step(), "the note is gone after the migration")
                assertEquals("Written before time zones were recorded", row.getText(0))
                assertTrue(row.isNull(1), "an existing note should have no time zone")
            }
        }
    }
}
