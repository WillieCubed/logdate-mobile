package app.logdate.client.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.logdate.client.database.migrations.MIGRATION_48_49
import app.logdate.client.database.migrations.MIGRATION_49_50
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Validates the combined sync migration without dropping data added in database version 48. */
class DownloadInboxMigrationJvmTest {
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
    fun `room adds durable sync tables while retaining version 48 data`() {
        helper.createDatabase(48).use { connection ->
            connection.execSQL(
                "INSERT INTO pending_uploads (ownerId, serverOrigin, entityType, entityId, operation, createdAt, retryCount) " +
                    "VALUES ('owner', 'server', 'DRAFT', 'draft', 'DELETE', 10, 7)",
            )
            connection.execSQL(
                "INSERT INTO text_notes (uid, content, lastUpdated, created, syncVersion) " +
                    "VALUES ('note-1', 'Written before time zones were recorded', 1710000000000, 1710000000000, 0)",
            )
            connection.execSQL(
                "INSERT INTO location_activity " +
                    "(id, user_id, device_id, timestamp, recorded_at, activity_type, transition_type) " +
                    "VALUES ('activity-1', 'user-1', 'device-1', 100, 101, 'WALKING', 'ENTER')",
            )
            connection.execSQL(
                "INSERT INTO history_records " +
                    "(ownerId, origin, id, recordType, deviceId, deviceVersion, serverVersion, deleted, dirty) " +
                    "VALUES ('owner', 'server', 'history-1', 'NOTE', 'device-1', 1, 2, 0, 1)",
            )
        }

        helper.runMigrationsAndValidate(50, listOf(MIGRATION_48_49, MIGRATION_49_50)).use { connection ->
            connection
                .prepare(
                    "SELECT operation, retryCount, expectedServerVersion, operationId FROM pending_uploads WHERE entityId = 'draft'",
                ).use { row ->
                    assertTrue(row.step())
                    assertEquals("DELETE", row.getText(0))
                    assertEquals(7L, row.getLong(1))
                    assertTrue(row.isNull(2))
                    assertTrue(row.getText(3).isNotBlank())
                }
            connection.prepare("SELECT content, time_zone_id FROM text_notes WHERE uid = 'note-1'").use { row ->
                assertTrue(row.step(), "the note is gone after the migration")
                assertEquals("Written before time zones were recorded", row.getText(0))
                assertTrue(row.isNull(1), "an existing note should have no time zone")
            }
            connection.prepare("SELECT id FROM location_activity WHERE id = 'activity-1'").use { row ->
                assertTrue(row.step(), "existing location activity is gone after the migration")
            }
            connection.prepare("SELECT id FROM history_records WHERE id = 'history-1'").use { row ->
                assertTrue(row.step(), "existing history record is gone after the migration")
            }
            connection.prepare("SELECT COUNT(*) FROM sync_download_inbox").use { row ->
                assertTrue(row.step())
                assertEquals(0L, row.getLong(0))
            }
            connection.prepare("SELECT COUNT(*) FROM sync_download_checkpoints").use { row ->
                assertTrue(row.step())
                assertEquals(0L, row.getLong(0))
            }
        }
    }
}
