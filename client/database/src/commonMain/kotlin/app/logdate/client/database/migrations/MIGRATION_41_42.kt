@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Adds view-tracking columns to the rewinds table so the app can distinguish
 * a brand-new rewind from one the user has already watched.
 */
val MIGRATION_41_42 =
    object : Migration(41, 42) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE rewinds ADD COLUMN is_viewed INTEGER NOT NULL DEFAULT 0",
            )
            connection.execSQL(
                "ALTER TABLE rewinds ADD COLUMN first_viewed_at INTEGER DEFAULT NULL",
            )
            connection.execSQL(
                "ALTER TABLE rewinds ADD COLUMN view_count INTEGER NOT NULL DEFAULT 0",
            )
        }
    }
