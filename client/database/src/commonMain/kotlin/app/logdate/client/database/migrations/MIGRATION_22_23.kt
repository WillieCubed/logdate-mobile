@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Migration 22 -> 23: Rename voice_notes to audio_notes and enforce durationMs.
 *
 * This migration:
 * - Creates the audio_notes table with non-null durationMs
 * - Copies existing voice_notes data with durationMs defaulting to 0
 * - Rewrites the transcriptions table to reference audio_notes
 */
val MIGRATION_22_23 =
    object : Migration(22, 23) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS audio_notes (
                    contentUri TEXT NOT NULL,
                    durationMs INTEGER NOT NULL DEFAULT 0,
                    uid TEXT NOT NULL,
                    lastUpdated INTEGER NOT NULL,
                    created INTEGER NOT NULL,
                    syncVersion INTEGER NOT NULL,
                    lastSynced INTEGER,
                    deletedAt INTEGER,
                    latitude REAL,
                    longitude REAL,
                    altitude REAL,
                    location_accuracy REAL,
                    place_id TEXT,
                    PRIMARY KEY(uid),
                    FOREIGN KEY(place_id) REFERENCES places(id) ON DELETE SET NULL
                )
                """.trimIndent(),
            )

            connection.execSQL(
                """
                INSERT INTO audio_notes (
                    contentUri,
                    durationMs,
                    uid,
                    lastUpdated,
                    created,
                    syncVersion,
                    lastSynced,
                    deletedAt,
                    latitude,
                    longitude,
                    altitude,
                    location_accuracy,
                    place_id
                )
                SELECT
                    contentUri,
                    COALESCE(durationMs, 0),
                    uid,
                    lastUpdated,
                    created,
                    syncVersion,
                    lastSynced,
                    deletedAt,
                    latitude,
                    longitude,
                    altitude,
                    location_accuracy,
                    place_id
                FROM voice_notes
                """.trimIndent(),
            )

            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS transcriptions_new (
                    noteId TEXT NOT NULL,
                    text TEXT,
                    status TEXT NOT NULL,
                    errorMessage TEXT,
                    created INTEGER NOT NULL,
                    lastUpdated INTEGER NOT NULL,
                    id TEXT NOT NULL,
                    PRIMARY KEY(id),
                    FOREIGN KEY(noteId) REFERENCES audio_notes(uid) ON DELETE CASCADE
                )
                """.trimIndent(),
            )

            connection.execSQL(
                """
                INSERT INTO transcriptions_new (
                    noteId,
                    text,
                    status,
                    errorMessage,
                    created,
                    lastUpdated,
                    id
                )
                SELECT
                    noteId,
                    text,
                    status,
                    errorMessage,
                    created,
                    lastUpdated,
                    id
                FROM transcriptions
                """.trimIndent(),
            )

            connection.execSQL("DROP TABLE transcriptions")
            connection.execSQL("ALTER TABLE transcriptions_new RENAME TO transcriptions")
            connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_transcriptions_noteId ON transcriptions(noteId)")

            connection.execSQL("DROP TABLE voice_notes")

            connection.execSQL("CREATE INDEX IF NOT EXISTS index_audio_notes_place_id ON audio_notes(place_id)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_audio_notes_coords ON audio_notes(latitude, longitude)")
        }
    }
