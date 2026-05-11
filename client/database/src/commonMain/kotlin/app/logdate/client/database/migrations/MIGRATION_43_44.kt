@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Adds timestamped transcript segments as a query-friendly index for search
 * snippets, playback seeking, and future speaker-aware transcript views.
 */
val MIGRATION_43_44 =
    object : Migration(43, 44) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS transcription_segments (
                    noteId TEXT NOT NULL,
                    segmentId TEXT NOT NULL,
                    text TEXT NOT NULL,
                    startMs INTEGER NOT NULL,
                    endMs INTEGER NOT NULL,
                    speakerId TEXT,
                    confidence REAL,
                    source TEXT NOT NULL,
                    isFinal INTEGER NOT NULL,
                    revision INTEGER NOT NULL,
                    PRIMARY KEY(noteId, segmentId),
                    FOREIGN KEY(noteId) REFERENCES audio_notes(uid) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_transcription_segments_noteId ON transcription_segments(noteId)")
            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_transcription_segments_noteId_startMs
                ON transcription_segments(noteId, startMs)
                """.trimIndent(),
            )
        }
    }
