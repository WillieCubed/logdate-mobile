@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Binds transcript completion to the saved audio revision without changing its text. */
val MIGRATION_50_51 =
    object : Migration(50, 51) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE transcriptions ADD COLUMN mediaUri TEXT")
            connection.execSQL("ALTER TABLE transcriptions ADD COLUMN mediaDurationMs INTEGER")
            connection.execSQL(
                """UPDATE transcriptions SET
                mediaUri = (SELECT contentUri FROM audio_notes WHERE uid = transcriptions.noteId),
                mediaDurationMs = (SELECT durationMs FROM audio_notes WHERE uid = transcriptions.noteId)""",
            )
        }
    }
