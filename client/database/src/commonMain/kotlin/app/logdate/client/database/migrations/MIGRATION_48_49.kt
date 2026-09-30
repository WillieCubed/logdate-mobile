@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_48_49 =
    object : Migration(48, 49) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE image_notes ADD COLUMN presentation TEXT NOT NULL DEFAULT 'EdgeToEdge'")
        }
    }
