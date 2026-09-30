package app.logdate.client.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.logdate.client.database.migrations.MIGRATION_47_48
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocationEvidenceMigrationJvmTest {
    @Test
    fun oldSamplesSurviveWithUnknownActivityAndTimeZone() {
        BundledSQLiteDriver().open(":memory:").use { connection ->
            connection.execSQL(
                "CREATE TABLE location_logs (sample_id TEXT NOT NULL PRIMARY KEY, timestamp INTEGER NOT NULL, " +
                    "user_id TEXT NOT NULL, device_id TEXT NOT NULL, logged_at INTEGER NOT NULL)",
            )
            connection.execSQL("INSERT INTO location_logs VALUES ('existing', 1000, 'user', 'device', 2000)")
            MIGRATION_47_48.migrate(connection)
            connection.prepare("SELECT timestamp, activity_type, time_zone_id FROM location_logs").use { row ->
                assertTrue(row.step())
                assertEquals(1000, row.getLong(0))
                assertTrue(row.isNull(1))
                assertTrue(row.isNull(2))
            }
            connection.execSQL(
                "INSERT INTO location_activity (id, user_id, device_id, timestamp, recorded_at, " +
                    "activity_type, transition_type, time_zone_id) " +
                    "VALUES ('event', 'user', 'device', 1000, 2000, 'ON_BICYCLE', 'ENTER', 'America/Los_Angeles')",
            )
            connection.prepare("SELECT activity_type FROM location_activity").use { row ->
                assertTrue(row.step())
                assertEquals("ON_BICYCLE", row.getText(0))
            }
        }
    }
}
