package app.logdate.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * A migration from version 1 to version 2 of the database.
 *
 * This migration adds a new table to the database to store the many-to-many relationship between
 * journals and notes.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE journal_notes (
                uid INTEGER NOT NULL,
                id INTEGER NOT NULL,
                PRIMARY KEY(id, uid)
            )
            """.trimIndent()
        )
        // Create indices on id and uid
        db.execSQL("CREATE INDEX index_journal_notes_id ON journal_notes(id)")
        db.execSQL("CREATE INDEX index_journal_notes_uid ON journal_notes(uid)")
    }
}