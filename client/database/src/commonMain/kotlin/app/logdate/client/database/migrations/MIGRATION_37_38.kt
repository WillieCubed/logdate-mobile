@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Introduces the `audio_tags` table for ambient sound detection on audio
 * notes.
 *
 * Each row is one detected sound (e.g. "Bird", "Rain") attached to an audio
 * note. Cascade-deletes with the parent note. No existing tables are altered.
 *
 * Column types match the [AudioTagEntity] declarations: Uuid columns (`id`,
 * `noteId`) are stored as TEXT — Room's Uuid type converter serializes to a
 * string. Using BLOB causes Room's compile-time schema validator to reject the
 * migration with "Migration didn't properly handle: audio_tags".
 */
val MIGRATION_37_38 =
    object : Migration(37, 38) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS audio_tags (
                    id TEXT NOT NULL PRIMARY KEY,
                    noteId TEXT NOT NULL,
                    soundName TEXT NOT NULL,
                    confidence REAL NOT NULL,
                    startMs INTEGER NOT NULL,
                    durationMs INTEGER NOT NULL,
                    created INTEGER NOT NULL,
                    FOREIGN KEY(noteId) REFERENCES audio_notes(uid) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_audio_tags_noteId ON audio_tags(noteId)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_audio_tags_soundName ON audio_tags(soundName)")
        }
    }
