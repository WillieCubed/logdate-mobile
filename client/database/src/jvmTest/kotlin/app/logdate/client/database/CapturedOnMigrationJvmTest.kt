package app.logdate.client.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.logdate.client.database.migrations.MIGRATION_53_54
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the 53 to 54 migration through Room's own schema validation on the exported schemas, the same
 * check Room makes when the app opens an upgraded database. Every note survives and none claims a
 * capture device it never recorded.
 */
class CapturedOnMigrationJvmTest {
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
    fun `room accepts the migrated schema and existing notes have no capture device`() {
        helper.createDatabase(53).use { connection ->
            connection.execSQL(
                "INSERT INTO text_notes (uid, content, lastUpdated, created, syncVersion) " +
                    "VALUES ('text-1', 'Walk', 1710000000000, 1710000000000, 0)",
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
        }

        helper.runMigrationsAndValidate(54, listOf(MIGRATION_53_54)).use { connection ->
            listOf("text_notes" to "text-1", "image_notes" to "image-1", "video_notes" to "video-1", "audio_notes" to "audio-1")
                .forEach { (table, uid) ->
                    connection.prepare("SELECT uid, captured_on FROM $table WHERE uid = '$uid'").use { row ->
                        assertTrue(row.step(), "$uid is gone after the migration")
                        assertEquals(uid, row.getText(0))
                        assertTrue(row.isNull(1), "$uid should have no capture device")
                    }
                }
        }
    }

    @Test
    fun `every existing row keeps its values and a new column accepts a device`() {
        helper.createDatabase(53).use { connection ->
            repeat(3) { index ->
                connection.execSQL(
                    "INSERT INTO text_notes (uid, content, lastUpdated, created, syncVersion, latitude, longitude, time_zone_id) " +
                        "VALUES ('text-$index', 'Walk $index', 1710000000000, 1700000000000, $index, 37.5, -122.25, 'America/Los_Angeles')",
                )
            }
            connection.execSQL(
                "INSERT INTO image_notes (uid, contentUri, lastUpdated, created, syncVersion, presentation) " +
                    "VALUES ('image-1', 'photo.jpg', 1710000000000, 1700000000000, 2, 'Framed')",
            )
            connection.execSQL(
                "INSERT INTO audio_notes (uid, contentUri, durationMs, lastUpdated, created, syncVersion, time_zone_id) " +
                    "VALUES ('audio-1', 'voice.m4a', 4000, 1710000000000, 1700000000000, 5, 'Europe/Paris')",
            )
        }

        helper.runMigrationsAndValidate(54, listOf(MIGRATION_53_54)).use { connection ->
            connection.prepare("SELECT COUNT(*) FROM text_notes").use { row ->
                assertTrue(row.step())
                assertEquals(3L, row.getLong(0))
            }
            connection
                .prepare("SELECT content, syncVersion, latitude, longitude, time_zone_id FROM text_notes WHERE uid = 'text-2'")
                .use { row ->
                    assertTrue(row.step(), "text-2 is gone after the migration")
                    assertEquals("Walk 2", row.getText(0))
                    assertEquals(2L, row.getLong(1))
                    assertEquals(37.5, row.getDouble(2))
                    assertEquals(-122.25, row.getDouble(3))
                    assertEquals("America/Los_Angeles", row.getText(4))
                }
            connection.prepare("SELECT presentation, syncVersion FROM image_notes WHERE uid = 'image-1'").use { row ->
                assertTrue(row.step(), "image-1 is gone after the migration")
                assertEquals("Framed", row.getText(0))
                assertEquals(2L, row.getLong(1))
            }
            connection.prepare("SELECT durationMs, time_zone_id FROM audio_notes WHERE uid = 'audio-1'").use { row ->
                assertTrue(row.step(), "audio-1 is gone after the migration")
                assertEquals(4000L, row.getLong(0))
                assertEquals("Europe/Paris", row.getText(1))
            }

            connection.execSQL("UPDATE audio_notes SET captured_on = 'watch' WHERE uid = 'audio-1'")
            connection.prepare("SELECT captured_on FROM audio_notes WHERE uid = 'audio-1'").use { row ->
                assertTrue(row.step())
                assertEquals("watch", row.getText(0))
            }
        }
    }

    @Test
    fun `running the migration again on a table that has the column changes nothing`() {
        helper.createDatabase(53).use { connection ->
            connection.execSQL(
                "INSERT INTO text_notes (uid, content, lastUpdated, created, syncVersion) " +
                    "VALUES ('text-1', 'Walk', 1710000000000, 1710000000000, 0)",
            )
            MIGRATION_53_54.migrate(connection)
            connection.execSQL("UPDATE text_notes SET captured_on = 'watch' WHERE uid = 'text-1'")
        }

        helper.runMigrationsAndValidate(54, listOf(MIGRATION_53_54)).use { connection ->
            connection.prepare("SELECT content, captured_on FROM text_notes WHERE uid = 'text-1'").use { row ->
                assertTrue(row.step(), "text-1 is gone after the second run")
                assertEquals("Walk", row.getText(0))
                assertEquals("watch", row.getText(1))
            }
        }
    }
}
