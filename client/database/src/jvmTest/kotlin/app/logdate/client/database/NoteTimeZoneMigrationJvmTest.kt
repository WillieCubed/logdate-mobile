package app.logdate.client.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.logdate.client.database.migrations.MIGRATION_46_47
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the 46 to 47 migration's SQL against stand-ins for the four note tables.
 *
 * Room's own schema validation of the migration runs in the Android device test; this covers
 * what the migration does to existing rows without needing a device.
 */
class NoteTimeZoneMigrationJvmTest {
    private val noteTables = listOf("text_notes", "image_notes", "video_notes", "audio_notes")
    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setUp() {
        connection = BundledSQLiteDriver().open(":memory:")
        noteTables.forEach { table ->
            connection.execSQL("CREATE TABLE $table (uid TEXT NOT NULL PRIMARY KEY, created INTEGER NOT NULL)")
            connection.execSQL("INSERT INTO $table (uid, created) VALUES ('note-1', 1710000000000)")
        }
    }

    @AfterTest
    fun tearDown() {
        connection.close()
    }

    @Test
    fun existingNotesKeepTheirRowsAndGetNoTimeZone() {
        MIGRATION_46_47.migrate(connection)

        noteTables.forEach { table ->
            connection.prepare("SELECT created, time_zone_id FROM $table WHERE uid = 'note-1'").use { row ->
                assertTrue(row.step(), "$table lost its row")
                assertEquals(1710000000000, row.getLong(0), "$table changed created")
                assertTrue(row.isNull(1), "$table should start without a time zone")
            }
        }
    }

    @Test
    fun theColumnStoresAnIanaZoneIdOnEveryNoteTable() {
        MIGRATION_46_47.migrate(connection)

        noteTables.forEach { table ->
            connection.execSQL("UPDATE $table SET time_zone_id = 'America/Denver' WHERE uid = 'note-1'")
            connection.prepare("SELECT time_zone_id FROM $table WHERE uid = 'note-1'").use { row ->
                assertTrue(row.step())
                assertEquals("America/Denver", row.getText(0))
            }
        }
    }
}
