@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Replaces the single `text_notes_fts_update` trigger with two explicit triggers
 * that handle content updates and soft-deletes separately.
 *
 * Guards all FTS operations behind an existence check because the `entries_fts`
 * virtual table may not exist on databases that were created fresh after the
 * migration that introduced it (MIGRATION_19_20), or on encrypted databases
 * where earlier migrations may have been applied differently.
 */
val MIGRATION_31_32 =
    object : Migration(31, 32) {
        override fun migrate(connection: SQLiteConnection) {
            // Only touch FTS triggers and data if the entries_fts table exists.
            // It was created in MIGRATION_19_20 but may be absent on some devices.
            val hasFtsTable =
                connection
                    .prepare(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='entries_fts'",
                    ).use { stmt ->
                        stmt.step()
                        stmt.getLong(0) > 0
                    }

            if (!hasFtsTable) return

            // Drop the old catch-all update trigger
            connection.execSQL("DROP TRIGGER IF EXISTS text_notes_fts_update")

            // Trigger: re-index when content changes on a non-deleted note
            connection.execSQL(
                """
                CREATE TRIGGER text_notes_fts_content_update
                AFTER UPDATE ON text_notes
                WHEN NEW.deletedAt IS NULL
                BEGIN
                    DELETE FROM entries_fts WHERE uid = OLD.uid;
                    INSERT INTO entries_fts(uid, content, created, contentType)
                    VALUES (NEW.uid, NEW.content, NEW.created, 'text_note');
                END
                """.trimIndent(),
            )

            // Trigger: remove from FTS when a note is soft-deleted
            connection.execSQL(
                """
                CREATE TRIGGER text_notes_fts_soft_delete
                AFTER UPDATE ON text_notes
                WHEN NEW.deletedAt IS NOT NULL AND OLD.deletedAt IS NULL
                BEGIN
                    DELETE FROM entries_fts WHERE uid = OLD.uid;
                END
                """.trimIndent(),
            )

            // Clean up any soft-deleted notes that leaked into the FTS index
            connection.execSQL(
                """
                DELETE FROM entries_fts
                WHERE contentType = 'text_note'
                AND uid IN (
                    SELECT uid FROM text_notes WHERE deletedAt IS NOT NULL
                )
                """.trimIndent(),
            )
        }
    }
