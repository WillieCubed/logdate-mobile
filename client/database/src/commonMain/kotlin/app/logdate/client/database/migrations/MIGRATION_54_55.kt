@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Adds what the person was doing at capture, while activity awareness was on, to every note table and
 * to marked moments. Existing rows stay null: their activity was never recorded.
 *
 * Only adds nullable columns, so no table is rebuilt or renamed and the full-text triggers on the note
 * tables are untouched. A table that already has the column is left as it is, as in [MIGRATION_53_54].
 */
val MIGRATION_54_55 =
    object : Migration(54, 55) {
        override fun migrate(connection: SQLiteConnection) {
            mapOf(
                "text_notes" to "captured_during",
                "image_notes" to "captured_during",
                "video_notes" to "captured_during",
                "audio_notes" to "captured_during",
                "marked_moments" to "capturedDuring",
            ).forEach { (table, column) ->
                if (connection.hasColumn(table, column)) return@forEach
                connection.execSQL("ALTER TABLE $table ADD COLUMN $column TEXT")
            }
        }
    }
