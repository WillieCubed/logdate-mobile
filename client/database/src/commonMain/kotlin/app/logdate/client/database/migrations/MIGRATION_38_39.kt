@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Introduces the `rewind_prompt_responses` table for typed replies the user can write
 * back at the rewind's noticing prompts.
 *
 * Each row is a single reply keyed `(rewindId, promptKey)` — promptKey is the FNV-1a
 * hash of the prompt's text, see `ReflectionPromptKey`. Cascade-deletes with the
 * parent rewind. The observation and invitation columns are denormalized copies of
 * the prompt at write time so a future "your past replies" view doesn't lose context
 * if the rewind is later regenerated.
 *
 * Column types match the [ReflectionPromptResponseEntity] declarations: the Uuid
 * `rewindId` column is stored as TEXT — Room's Uuid type converter serializes to a
 * string. Using BLOB causes Room's compile-time schema validator to reject the
 * migration with "Migration didn't properly handle: rewind_prompt_responses".
 */
val MIGRATION_38_39 =
    object : Migration(38, 39) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS rewind_prompt_responses (
                    rewindId TEXT NOT NULL,
                    promptKey TEXT NOT NULL,
                    observation TEXT NOT NULL,
                    invitation TEXT NOT NULL,
                    responseText TEXT NOT NULL,
                    created INTEGER NOT NULL,
                    lastUpdated INTEGER NOT NULL,
                    PRIMARY KEY(rewindId, promptKey),
                    FOREIGN KEY(rewindId) REFERENCES rewinds(uid) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_rewind_prompt_responses_rewindId ON rewind_prompt_responses(rewindId)",
            )
        }
    }
