@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_27_28 =
    object : Migration(27, 28) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS media_captions (
                    noteId TEXT NOT NULL,
                    caption TEXT NOT NULL,
                    PRIMARY KEY(noteId)
                )
                """.trimIndent(),
            )

            // Drop caption column from image_notes if it exists (from prior dev builds).
            // Migrate any non-empty captions to the new table first.
            val hasCaptionColumn =
                run {
                    val cursor = connection.prepare("PRAGMA table_info(image_notes)")
                    try {
                        generateSequence { if (cursor.step()) cursor.getText(1) else null }
                            .any { it == "caption" }
                    } finally {
                        cursor.close()
                    }
                }

            if (hasCaptionColumn) {
                connection.execSQL(
                    """
                    INSERT OR IGNORE INTO media_captions (noteId, caption)
                    SELECT uid, caption FROM image_notes
                    WHERE caption IS NOT NULL AND caption != ''
                    """.trimIndent(),
                )
                connection.execSQL("ALTER TABLE image_notes DROP COLUMN caption")
            }
        }
    }
