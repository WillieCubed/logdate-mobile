@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

private const val CAPTURED_ON_COLUMN = "captured_on"

/**
 * Adds the device each note was captured on to every note table. Existing notes stay null: the device
 * was never recorded, and a note that came from the watch before this version cannot be told apart
 * from one written on the phone.
 *
 * A table that already has the column is left as it is, because SQLite cannot add a column twice and
 * a database reopened with a lowered version marker (as the journal merge persistence test does)
 * would otherwise fail the whole upgrade.
 */
val MIGRATION_53_54 =
    object : Migration(53, 54) {
        override fun migrate(connection: SQLiteConnection) {
            listOf("text_notes", "image_notes", "video_notes", "audio_notes").forEach { table ->
                if (connection.hasColumn(table, CAPTURED_ON_COLUMN)) return@forEach
                connection.execSQL("ALTER TABLE $table ADD COLUMN $CAPTURED_ON_COLUMN TEXT")
            }
        }
    }

internal fun SQLiteConnection.hasColumn(
    table: String,
    column: String,
): Boolean =
    prepare("PRAGMA table_info($table)").use { statement ->
        while (statement.step()) {
            if (statement.getText(1) == column) return true
        }
        false
    }
