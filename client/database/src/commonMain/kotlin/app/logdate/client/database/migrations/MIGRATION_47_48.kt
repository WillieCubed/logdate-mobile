@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_47_48 =
    object : Migration(47, 48) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE location_logs ADD COLUMN activity_type TEXT")
            connection.execSQL("ALTER TABLE location_logs ADD COLUMN time_zone_id TEXT")
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_location_logs_timestamp ON location_logs(timestamp)")
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_location_logs_user_id_device_id_timestamp_sample_id " +
                    "ON location_logs(user_id,device_id,timestamp,sample_id)",
            )
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS location_activity (
                    id TEXT NOT NULL PRIMARY KEY,
                    user_id TEXT NOT NULL,
                    device_id TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    recorded_at INTEGER NOT NULL,
                    activity_type TEXT NOT NULL,
                    transition_type TEXT NOT NULL,
                    time_zone_id TEXT
                )
                """.trimIndent(),
            )
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_location_activity_timestamp ON location_activity(timestamp)")
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_location_logs_user_id_device_id_logged_at_sample_id ON location_logs(user_id,device_id,logged_at,sample_id)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_location_activity_user_id_device_id_recorded_at_id ON location_activity(user_id,device_id,recorded_at,id)",
            )
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS history_records (
                    ownerId TEXT NOT NULL, origin TEXT NOT NULL, id TEXT NOT NULL,
                    recordType TEXT NOT NULL, payload TEXT, deviceId TEXT NOT NULL,
                    deviceVersion INTEGER NOT NULL, serverVersion INTEGER NOT NULL,
                    deleted INTEGER NOT NULL, dirty INTEGER NOT NULL, observedAt INTEGER,
                    PRIMARY KEY(ownerId, origin, id)
                )
                """.trimIndent(),
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_history_records_ownerId_origin_observedAt ON history_records(ownerId,origin,observedAt)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_history_records_ownerId_origin_recordType_deviceId_observedAt_id " +
                    "ON history_records(ownerId,origin,recordType,deviceId,observedAt,id)",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS history_cursors (ownerId TEXT NOT NULL, origin TEXT NOT NULL, cursor INTEGER NOT NULL, PRIMARY KEY(ownerId, origin))",
            )
        }
    }
