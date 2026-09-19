@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Adds the IANA time zone a note was captured in to every note table. Existing notes stay null:
 * the zone they were written in was never recorded and cannot be recovered.
 */
val MIGRATION_46_47 =
    object : Migration(46, 47) {
        override fun migrate(connection: SQLiteConnection) {
            listOf("text_notes", "image_notes", "video_notes", "audio_notes").forEach { table ->
                connection.execSQL("ALTER TABLE $table ADD COLUMN time_zone_id TEXT")
            }
        }
    }
