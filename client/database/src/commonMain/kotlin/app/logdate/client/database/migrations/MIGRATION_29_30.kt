@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_29_30 =
    object : Migration(29, 30) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS health_snapshots (
                    id TEXT NOT NULL PRIMARY KEY,
                    note_id TEXT,
                    heart_rate_bpm INTEGER,
                    heart_rate_variability_ms REAL,
                    step_count INTEGER,
                    stress_level REAL,
                    cumulative_calories REAL,
                    timestamp INTEGER NOT NULL,
                    source TEXT NOT NULL
                )
                """.trimIndent(),
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_health_snapshots_note_id ON health_snapshots(note_id)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_health_snapshots_timestamp ON health_snapshots(timestamp)",
            )
        }
    }
