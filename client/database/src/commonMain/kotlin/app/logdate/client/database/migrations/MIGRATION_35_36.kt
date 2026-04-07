@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Adds metadata column to the rewinds table.
 *
 * `metadata` stores JSON-serialized intelligence metadata (activities, location, milestones, people).
 */
val MIGRATION_35_36 =
    object : Migration(35, 36) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE rewinds ADD COLUMN metadata TEXT DEFAULT NULL")
        }
    }
