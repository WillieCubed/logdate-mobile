@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_25_26 =
    object : Migration(25, 26) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS location_logs_new (
                    sample_id TEXT NOT NULL,
                    user_id TEXT NOT NULL,
                    device_id TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    logged_at INTEGER NOT NULL,
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    altitude REAL NOT NULL,
                    confidence REAL NOT NULL,
                    is_genuine INTEGER NOT NULL,
                    capture_pipeline TEXT NOT NULL,
                    capture_source TEXT NOT NULL,
                    accuracy_meters REAL,
                    speed_meters_per_second REAL,
                    bearing_degrees REAL,
                    is_mock INTEGER NOT NULL,
                    PRIMARY KEY(sample_id)
                )
                """.trimIndent(),
            )

            connection.execSQL(
                """
                INSERT INTO location_logs_new (
                    sample_id,
                    user_id,
                    device_id,
                    timestamp,
                    logged_at,
                    latitude,
                    longitude,
                    altitude,
                    confidence,
                    is_genuine,
                    capture_pipeline,
                    capture_source,
                    accuracy_meters,
                    speed_meters_per_second,
                    bearing_degrees,
                    is_mock
                )
                SELECT
                    user_id || ':' || device_id || ':' || timestamp,
                    user_id,
                    device_id,
                    timestamp,
                    timestamp,
                    latitude,
                    longitude,
                    altitude,
                    confidence,
                    is_genuine,
                    'LEGACY',
                    'BACKGROUND_PERIODIC',
                    NULL,
                    NULL,
                    NULL,
                    0
                FROM location_logs
                """.trimIndent(),
            )
            connection.execSQL("DROP TABLE location_logs")
            connection.execSQL("ALTER TABLE location_logs_new RENAME TO location_logs")
        }
    }
