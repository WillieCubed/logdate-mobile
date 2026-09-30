package app.logdate.client.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.logdate.client.database.migrations.MIGRATION_47_48
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryRecordMigrationTest {
    @Test
    fun `migration adds isolated history outbox without removing legacy observations`() {
        BundledSQLiteDriver().open(":memory:").use { connection ->
            connection.execSQL(
                "CREATE TABLE location_logs (sample_id TEXT, timestamp INTEGER, user_id TEXT, device_id TEXT, logged_at INTEGER)",
            )
            connection.execSQL("INSERT INTO location_logs VALUES ('legacy', 1, 'owner', 'device', 1)")
            MIGRATION_47_48.migrate(connection)
            connection.execSQL(
                "INSERT INTO history_records VALUES ('alice', 'origin', 'id', 'place', 'private', 'phone', 1, 0, 0, 1, NULL)",
            )
            connection.execSQL(
                "INSERT INTO history_records VALUES ('bob', 'origin', 'id', 'place', 'different', 'phone', 1, 0, 0, 1, NULL)",
            )
            connection.prepare("SELECT payload FROM history_records WHERE ownerId = 'alice'").use {
                assertTrue(it.step())
                assertEquals("private", it.getText(0))
            }
            connection.prepare("SELECT sample_id FROM location_logs").use {
                assertTrue(it.step())
                assertEquals("legacy", it.getText(0))
            }
        }
    }
}
