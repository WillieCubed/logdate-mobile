@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Retains note-local locations and original audio text without manufacturing missing metadata. */
val MIGRATION_57_58 =
    object : Migration(57, 58) {
        override fun migrate(connection: SQLiteConnection) {
            for (table in listOf("text_notes", "image_notes", "audio_notes", "video_notes")) {
                if (!connection.hasColumn(table, "location_metadata")) {
                    connection.execSQL("ALTER TABLE $table ADD COLUMN location_metadata TEXT")
                }
            }
            if (!connection.hasColumn("audio_notes", "transcription")) {
                connection.execSQL("ALTER TABLE audio_notes ADD COLUMN transcription TEXT NOT NULL DEFAULT ''")
            }
        }
    }
