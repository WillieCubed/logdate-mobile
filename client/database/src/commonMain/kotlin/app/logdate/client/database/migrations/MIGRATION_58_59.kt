@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Distinguishes original ink documents from plain text without rewriting existing notes. */
val MIGRATION_58_59 =
    object : Migration(58, 59) {
        override fun migrate(connection: SQLiteConnection) {
            if (!connection.hasColumn("text_notes", "content_format")) {
                connection.execSQL("ALTER TABLE text_notes ADD COLUMN content_format TEXT NOT NULL DEFAULT 'text'")
            }
        }
    }
