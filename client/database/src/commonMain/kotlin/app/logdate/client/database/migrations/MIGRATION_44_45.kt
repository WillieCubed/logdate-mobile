@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Adds an all-day flag to events so all-day calendar events render date-only instead of
 * being shown at a spurious local time. Existing rows default to a timed event (0).
 */
val MIGRATION_44_45 =
    object : Migration(44, 45) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE events ADD COLUMN is_all_day INTEGER NOT NULL DEFAULT 0")
        }
    }
