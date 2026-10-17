package app.logdate.client.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.logdate.client.database.migrations.MIGRATION_54_55
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the 54 to 55 migration through Room's own schema validation on the exported schemas. Every note
 * and marked moment survives with its values, and none claims an activity it never recorded.
 */
class CapturedDuringMigrationJvmTest {
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
    fun `room accepts the migrated schema and existing rows have no activity`() {
        helper.createDatabase(54).use { connection ->
            connection.execSQL(
                "INSERT INTO text_notes (uid, content, lastUpdated, created, syncVersion, captured_on) " +
                    "VALUES ('text-1', 'Walk', 1710000000000, 1710000000000, 3, 'watch')",
            )
            connection.execSQL(
                "INSERT INTO image_notes (uid, contentUri, lastUpdated, created, syncVersion) " +
                    "VALUES ('image-1', 'photo.jpg', 1710000000000, 1710000000000, 0)",
            )
            connection.execSQL(
                "INSERT INTO video_notes (uid, contentUri, lastUpdated, created, syncVersion) " +
                    "VALUES ('video-1', 'clip.mp4', 1710000000000, 1710000000000, 0)",
            )
            connection.execSQL(
                "INSERT INTO audio_notes (uid, contentUri, durationMs, lastUpdated, created, syncVersion) " +
                    "VALUES ('audio-1', 'voice.m4a', 4000, 1710000000000, 1710000000000, 0)",
            )
            connection.execSQL(
                "INSERT INTO marked_moments " +
                    "(id, capturedAt, timeZoneId, state, caption, resolvedNoteIds, originDeviceId, lastUpdated, syncVersion) " +
                    "VALUES ('mark-1', 1710000000000, 'Europe/Paris', 'OPEN', 'By the lake', '', 'watch-1', 1710000000000, 2)",
            )
        }

        helper.runMigrationsAndValidate(55, listOf(MIGRATION_54_55)).use { connection ->
            listOf("text_notes" to "text-1", "image_notes" to "image-1", "video_notes" to "video-1", "audio_notes" to "audio-1")
                .forEach { (table, uid) ->
                    connection.prepare("SELECT uid, captured_during FROM $table WHERE uid = '$uid'").use { row ->
                        assertTrue(row.step(), "$uid is gone after the migration")
                        assertEquals(uid, row.getText(0))
                        assertTrue(row.isNull(1), "$uid should have no activity")
                    }
                }
            connection.prepare("SELECT content, syncVersion, captured_on FROM text_notes WHERE uid = 'text-1'").use { row ->
                assertTrue(row.step())
                assertEquals("Walk", row.getText(0))
                assertEquals(3L, row.getLong(1))
                assertEquals("watch", row.getText(2))
            }
            connection.prepare("SELECT caption, syncVersion, capturedDuring FROM marked_moments WHERE id = 'mark-1'").use { row ->
                assertTrue(row.step(), "mark-1 is gone after the migration")
                assertEquals("By the lake", row.getText(0))
                assertEquals(2L, row.getLong(1))
                assertTrue(row.isNull(2), "mark-1 should have no activity")
            }

            connection.execSQL("UPDATE audio_notes SET captured_during = 'moving' WHERE uid = 'audio-1'")
            connection.prepare("SELECT captured_during FROM audio_notes WHERE uid = 'audio-1'").use { row ->
                assertTrue(row.step())
                assertEquals("moving", row.getText(0))
            }
        }
    }

    @Test
    fun `running the migration again on tables that have the column changes nothing`() {
        helper.createDatabase(54).use { connection ->
            connection.execSQL(
                "INSERT INTO text_notes (uid, content, lastUpdated, created, syncVersion) " +
                    "VALUES ('text-1', 'Drive', 1710000000000, 1710000000000, 0)",
            )
            MIGRATION_54_55.migrate(connection)
            connection.execSQL("UPDATE text_notes SET captured_during = 'in_vehicle' WHERE uid = 'text-1'")
        }

        helper.runMigrationsAndValidate(55, listOf(MIGRATION_54_55)).use { connection ->
            connection.prepare("SELECT content, captured_during FROM text_notes WHERE uid = 'text-1'").use { row ->
                assertTrue(row.step(), "text-1 is gone after the second run")
                assertEquals("Drive", row.getText(0))
                assertEquals("in_vehicle", row.getText(1))
            }
        }
    }
}
