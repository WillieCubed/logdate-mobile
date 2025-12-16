package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Migration from database version 19 to 20.
 *
 * Adds FTS5 (Full-Text Search) virtual table for searching across entries:
 * - Creates entries_fts virtual table combining text_notes and transcriptions
 * - Populates FTS table with existing data
 * - Sets up triggers to keep FTS table in sync with source tables
 *
 * The FTS table enables:
 * - Fast full-text search across all text content
 * - Fuzzy matching and relevance ranking
 * - Boolean search operators (AND, OR, NOT)
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(connection: SQLiteConnection) {
        // Create FTS5 virtual table
        createFtsTable(connection)

        // Populate FTS table with existing data
        populateFtsTable(connection)

        // Create triggers to keep FTS table in sync
        createTriggers(connection)
    }

    private fun createFtsTable(connection: SQLiteConnection) {
        // Create FTS5 virtual table for searchable content
        // Columns: uid (unique identifier), content (searchable text), created (timestamp for ordering)
        connection.execSQL("""
            CREATE VIRTUAL TABLE entries_fts USING fts5(
                uid UNINDEXED,
                content,
                created UNINDEXED,
                contentType UNINDEXED,
                tokenize = 'porter unicode61'
            )
        """)
    }

    private fun populateFtsTable(connection: SQLiteConnection) {
        // Insert existing text notes into FTS table
        connection.execSQL("""
            INSERT INTO entries_fts(uid, content, created, contentType)
            SELECT uid, content, created, 'text_note'
            FROM text_notes
            WHERE deletedAt IS NULL
        """)

        // Insert existing transcriptions into FTS table
        connection.execSQL("""
            INSERT INTO entries_fts(uid, content, created, contentType)
            SELECT noteId, text, lastUpdated, 'transcription'
            FROM transcriptions
            WHERE status = 'COMPLETED' AND text IS NOT NULL
        """)
    }

    private fun createTriggers(connection: SQLiteConnection) {
        // Triggers for text_notes table

        // Insert trigger for text_notes
        connection.execSQL("""
            CREATE TRIGGER text_notes_fts_insert
            AFTER INSERT ON text_notes
            WHEN NEW.deletedAt IS NULL
            BEGIN
                INSERT INTO entries_fts(uid, content, created, contentType)
                VALUES (NEW.uid, NEW.content, NEW.created, 'text_note');
            END
        """)

        // Update trigger for text_notes
        connection.execSQL("""
            CREATE TRIGGER text_notes_fts_update
            AFTER UPDATE ON text_notes
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.uid;
                INSERT INTO entries_fts(uid, content, created, contentType)
                SELECT uid, content, created, 'text_note'
                FROM text_notes
                WHERE uid = NEW.uid AND deletedAt IS NULL;
            END
        """)

        // Delete trigger for text_notes
        connection.execSQL("""
            CREATE TRIGGER text_notes_fts_delete
            AFTER DELETE ON text_notes
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.uid;
            END
        """)

        // Triggers for transcriptions table

        // Insert trigger for transcriptions
        connection.execSQL("""
            CREATE TRIGGER transcriptions_fts_insert
            AFTER INSERT ON transcriptions
            WHEN NEW.status = 'COMPLETED' AND NEW.text IS NOT NULL
            BEGIN
                INSERT INTO entries_fts(uid, content, created, contentType)
                VALUES (NEW.noteId, NEW.text, NEW.lastUpdated, 'transcription');
            END
        """)

        // Update trigger for transcriptions
        connection.execSQL("""
            CREATE TRIGGER transcriptions_fts_update
            AFTER UPDATE ON transcriptions
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.noteId AND contentType = 'transcription';
                INSERT INTO entries_fts(uid, content, created, contentType)
                SELECT noteId, text, lastUpdated, 'transcription'
                FROM transcriptions
                WHERE noteId = NEW.noteId AND status = 'COMPLETED' AND text IS NOT NULL;
            END
        """)

        // Delete trigger for transcriptions
        connection.execSQL("""
            CREATE TRIGGER transcriptions_fts_delete
            AFTER DELETE ON transcriptions
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.noteId AND contentType = 'transcription';
            END
        """)
    }
}
