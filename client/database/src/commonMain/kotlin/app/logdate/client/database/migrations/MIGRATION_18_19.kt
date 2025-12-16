package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Migration from database version 18 to 19.
 * 
 * Adds sync metadata columns to support cloud synchronization:
 * - syncVersion: Version number for conflict resolution (NOT NULL, default 0)
 * - lastSynced: Timestamp of last successful sync (nullable)
 * - deletedAt: Soft delete timestamp for sync purposes (nullable)
 * 
 * This migration recreates tables to ensure perfect schema alignment with Room expectations.
 */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(connection: SQLiteConnection) {
        // Migrate text_notes table
        migrateTextNotesTable(connection)
        
        // Migrate image_notes table
        migrateImageNotesTable(connection)
        
        // Migrate journals table
        migrateJournalsTable(connection)
        
        // Migrate voice_notes table (if exists)
        try {
            migrateVoiceNotesTable(connection)
        } catch (e: Exception) {
            // Table might not exist, continue
        }
        
        // Migrate video_notes table (if exists)
        try {
            migrateVideoNotesTable(connection)
        } catch (e: Exception) {
            // Table might not exist, continue
        }
        
        // Migrate journal_content_links table (if exists)
        try {
            migrateJournalContentLinksTable(connection)
        } catch (e: Exception) {
            // Table might not exist, continue
        }
    }
    
    private fun migrateTextNotesTable(connection: SQLiteConnection) {
        // Create new table with exact Room schema - note column order matches Room's expectation
        connection.execSQL("""
            CREATE TABLE text_notes_new (
                content TEXT NOT NULL,
                uid TEXT NOT NULL,
                lastUpdated INTEGER NOT NULL,
                created INTEGER NOT NULL,
                syncVersion INTEGER NOT NULL,
                lastSynced INTEGER,
                deletedAt INTEGER,
                PRIMARY KEY(uid)
            )
        """)
        
        // Copy existing data, setting syncVersion to 0 for existing rows
        connection.execSQL("""
            INSERT INTO text_notes_new (content, uid, lastUpdated, created, syncVersion, lastSynced, deletedAt)
            SELECT content, uid, lastUpdated, created, 0, NULL, NULL
            FROM text_notes
        """)
        
        // Drop old table and rename new one
        connection.execSQL("DROP TABLE text_notes")
        connection.execSQL("ALTER TABLE text_notes_new RENAME TO text_notes")
    }
    
    private fun migrateImageNotesTable(connection: SQLiteConnection) {
        // Create new table with exact Room schema - note column order matches Room's expectation
        connection.execSQL("""
            CREATE TABLE image_notes_new (
                contentUri TEXT NOT NULL,
                uid TEXT NOT NULL,
                lastUpdated INTEGER NOT NULL,
                created INTEGER NOT NULL,
                syncVersion INTEGER NOT NULL,
                lastSynced INTEGER,
                deletedAt INTEGER,
                PRIMARY KEY(uid)
            )
        """)
        
        // Copy existing data
        connection.execSQL("""
            INSERT INTO image_notes_new (contentUri, uid, lastUpdated, created, syncVersion, lastSynced, deletedAt)
            SELECT contentUri, uid, lastUpdated, created, 0, NULL, NULL
            FROM image_notes
        """)
        
        // Drop old table and rename new one
        connection.execSQL("DROP TABLE image_notes")
        connection.execSQL("ALTER TABLE image_notes_new RENAME TO image_notes")
    }
    
    private fun migrateJournalsTable(connection: SQLiteConnection) {
        // Create new table with exact Room schema - note column order matches Room's expectation
        connection.execSQL("""
            CREATE TABLE journals_new (
                id TEXT NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                created INTEGER NOT NULL,
                lastUpdated INTEGER NOT NULL,
                syncVersion INTEGER NOT NULL,
                lastSynced INTEGER,
                deletedAt INTEGER,
                PRIMARY KEY(id)
            )
        """)
        
        // Copy existing data
        connection.execSQL("""
            INSERT INTO journals_new (id, title, description, created, lastUpdated, syncVersion, lastSynced, deletedAt)
            SELECT id, title, description, created, lastUpdated, 0, NULL, NULL
            FROM journals
        """)
        
        // Drop old table and rename new one
        connection.execSQL("DROP TABLE journals")
        connection.execSQL("ALTER TABLE journals_new RENAME TO journals")
    }
    
    private fun migrateVoiceNotesTable(connection: SQLiteConnection) {
        // Create new table with exact Room schema - note column order matches Room's expectation
        connection.execSQL("""
            CREATE TABLE voice_notes_new (
                contentUri TEXT NOT NULL,
                durationMs INTEGER,
                uid TEXT NOT NULL,
                lastUpdated INTEGER NOT NULL,
                created INTEGER NOT NULL,
                syncVersion INTEGER NOT NULL,
                lastSynced INTEGER,
                deletedAt INTEGER,
                PRIMARY KEY(uid)
            )
        """)
        
        // Copy existing data
        connection.execSQL("""
            INSERT INTO voice_notes_new (contentUri, durationMs, uid, lastUpdated, created, syncVersion, lastSynced, deletedAt)
            SELECT contentUri, durationMs, uid, lastUpdated, created, 0, NULL, NULL
            FROM voice_notes
        """)
        
        // Drop old table and rename new one
        connection.execSQL("DROP TABLE voice_notes")
        connection.execSQL("ALTER TABLE voice_notes_new RENAME TO voice_notes")
    }
    
    private fun migrateVideoNotesTable(connection: SQLiteConnection) {
        // Create new table with exact Room schema - note column order matches Room's expectation
        connection.execSQL("""
            CREATE TABLE video_notes_new (
                contentUri TEXT NOT NULL,
                durationMs INTEGER,
                uid TEXT NOT NULL,
                lastUpdated INTEGER NOT NULL,
                created INTEGER NOT NULL,
                syncVersion INTEGER NOT NULL,
                lastSynced INTEGER,
                deletedAt INTEGER,
                PRIMARY KEY(uid)
            )
        """)
        
        // Copy existing data
        connection.execSQL("""
            INSERT INTO video_notes_new (contentUri, durationMs, uid, lastUpdated, created, syncVersion, lastSynced, deletedAt)
            SELECT contentUri, durationMs, uid, lastUpdated, created, 0, NULL, NULL
            FROM video_notes
        """)
        
        // Drop old table and rename new one
        connection.execSQL("DROP TABLE video_notes")
        connection.execSQL("ALTER TABLE video_notes_new RENAME TO video_notes")
    }
    
    private fun migrateJournalContentLinksTable(connection: SQLiteConnection) {
        // Create new table with exact Room schema - note column order matches Room's expectation
        connection.execSQL("""
            CREATE TABLE journal_content_links_new (
                journal_id TEXT NOT NULL,
                content_id TEXT NOT NULL,
                syncVersion INTEGER NOT NULL,
                lastSynced INTEGER,
                deletedAt INTEGER,
                PRIMARY KEY(journal_id, content_id),
                FOREIGN KEY(journal_id) REFERENCES journals(id) ON DELETE CASCADE
            )
        """)
        
        // Copy existing data
        connection.execSQL("""
            INSERT INTO journal_content_links_new (journal_id, content_id, syncVersion, lastSynced, deletedAt)
            SELECT journal_id, content_id, 0, NULL, NULL
            FROM journal_content_links
        """)
        
        // Drop old table and rename new one
        connection.execSQL("DROP TABLE journal_content_links")
        connection.execSQL("ALTER TABLE journal_content_links_new RENAME TO journal_content_links")
        
        // Recreate indices exactly as Room expects them
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_journal_content_links_journal_id ON journal_content_links(journal_id)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_journal_content_links_content_id ON journal_content_links(content_id)")
    }
}