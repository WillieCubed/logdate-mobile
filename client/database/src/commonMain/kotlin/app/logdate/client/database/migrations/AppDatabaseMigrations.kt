package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import kotlin.uuid.Uuid

/**
 * A migration from version 1 to version 2 of the database.
 *
 * This migration adds a new table to the database to store the many-to-many relationship between
 * journals and notes.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE journal_notes (
                uid INTEGER NOT NULL,
                id INTEGER NOT NULL,
                PRIMARY KEY(id, uid)
            )
            """.trimIndent()
        )
        // Create indices on id and uid
        connection.execSQL("CREATE INDEX index_journal_notes_id ON journal_notes(id)")
        connection.execSQL("CREATE INDEX index_journal_notes_uid ON journal_notes(uid)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE user_devices (
                uid TEXT NOT NULL,
                user_id TEXT NOT NULL,
                label TEXT NOT NULL,
                operating_system TEXT NOT NULL,
                version TEXT NOT NULL,
                model TEXT NOT NULL,
                type TEXT NOT NULL,
                added INTEGER NOT NULL, 
                PRIMARY KEY(uid)
            )
            """.trimIndent()
        )

        connection.execSQL(
            """
            CREATE TABLE location_logs (
                user_id TEXT NOT NULL,
                device_id TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                latitude DOUBLE NOT NULL,
                longitude DOUBLE NOT NULL,
                altitude DOUBLE NOT NULL,
                confidence REAL NOT NULL,
                is_genuine INTEGER NOT NULL,
                PRIMARY KEY(user_id, device_id, timestamp)
            )
        """.trimIndent()
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE media_images (
                lastUpdated INTEGER NOT NULL,
                id INTEGER NOT NULL,
                addedTimestamp INTEGER NOT NULL,
                uri TEXT NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS rewinds (
                uid TEXT NOT NULL,
                PRIMARY KEY(uid)
            )
            """.trimIndent()
        )
    }
}

/**
 * A migration from version 5 to version 6 of the database.
 * 
 * This migration adds the journal_content_links table to enable many-to-many 
 * relationships between journals and various content (notes, images, etc.).
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        // Create the journal_content_links table
        connection.execSQL(
            """
            CREATE TABLE journal_content_links (
                journal_id TEXT NOT NULL,
                content_id TEXT NOT NULL,
                PRIMARY KEY(journal_id, content_id),
                FOREIGN KEY(journal_id) REFERENCES journals(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        
        // Create indices to improve query performance
        connection.execSQL("CREATE INDEX index_journal_content_links_journal_id ON journal_content_links(journal_id)")
        connection.execSQL("CREATE INDEX index_journal_content_links_content_id ON journal_content_links(content_id)")
    }
}

/**
 * A migration from version 6 to version 7 of the database.
 * 
 * This migration converts the journals table to use UUID strings as primary keys instead of integers.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        // Create a new temporary journals table with string IDs
        connection.execSQL(
            """
            CREATE TABLE journals_new (
                id TEXT NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                created INTEGER NOT NULL,
                lastUpdated INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        
        // Copy data from old table to new table, converting int IDs to strings
        connection.execSQL(
            """
            INSERT INTO journals_new (id, title, description, created, lastUpdated)
            SELECT CAST(id AS TEXT), title, description, created, lastUpdated 
            FROM journals
            """.trimIndent()
        )
        
        // Update journal_notes table references
        // Create new journal_notes table with TEXT id column instead of INTEGER
        connection.execSQL(
            """
            CREATE TABLE journal_notes_new (
                id TEXT NOT NULL,
                uid INTEGER NOT NULL,
                PRIMARY KEY(id, uid)
            )
            """.trimIndent()
        )
        
        // Copy data from old cross-reference table to new one, converting journal IDs
        connection.execSQL(
            """
            INSERT INTO journal_notes_new (id, uid)
            SELECT CAST(id AS TEXT), uid 
            FROM journal_notes
            """.trimIndent()
        )
        
        // Update any existing journal_content_links references
        connection.execSQL(
            """
            UPDATE journal_content_links
            SET journal_id = CAST(journal_id AS TEXT)
            WHERE journal_id IS NOT NULL
            """.trimIndent()
        )
        
        // Drop old tables
        connection.execSQL("DROP TABLE journal_notes")
        connection.execSQL("DROP TABLE journals")
        
        // Rename new tables to original names
        connection.execSQL("ALTER TABLE journals_new RENAME TO journals")
        connection.execSQL("ALTER TABLE journal_notes_new RENAME TO journal_notes")
        
        // Recreate indices for journal_notes
        connection.execSQL("CREATE INDEX index_journal_notes_id ON journal_notes(id)")
        connection.execSQL("CREATE INDEX index_journal_notes_uid ON journal_notes(uid)")
    }
}

/**
 * A migration from version 7 to version 8 of the database.
 * 
 * This migration converts all note tables to use UUID strings as primary keys 
 * instead of auto-generated integers.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        // Migrate text_notes table
        connection.execSQL(
            """
            CREATE TABLE text_notes_new (
                uid TEXT NOT NULL,
                content TEXT NOT NULL,
                lastUpdated INTEGER NOT NULL,
                created INTEGER NOT NULL,
                PRIMARY KEY(uid)
            )
            """.trimIndent()
        )
        
        // Copy data from old table to new table, converting int IDs to strings
        connection.execSQL(
            """
            INSERT INTO text_notes_new (uid, content, lastUpdated, created)
            SELECT CAST(uid AS TEXT), content, lastUpdated, created 
            FROM text_notes
            """.trimIndent()
        )
        
        // Migrate image_notes table
        connection.execSQL(
            """
            CREATE TABLE image_notes_new (
                uid TEXT NOT NULL,
                contentUri TEXT NOT NULL,
                lastUpdated INTEGER NOT NULL,
                created INTEGER NOT NULL,
                PRIMARY KEY(uid)
            )
            """.trimIndent()
        )
        
        // Copy data from old table to new table, converting int IDs to strings
        connection.execSQL(
            """
            INSERT INTO image_notes_new (uid, contentUri, lastUpdated, created)
            SELECT CAST(uid AS TEXT), contentUri, lastUpdated, created 
            FROM image_notes
            """.trimIndent()
        )
        
        // Update journal_notes table to use string UIDs for notes
        connection.execSQL(
            """
            CREATE TABLE journal_notes_new (
                id TEXT NOT NULL,
                uid TEXT NOT NULL,
                PRIMARY KEY(id, uid)
            )
            """.trimIndent()
        )
        
        // Copy data from old cross-reference table to new one, converting note IDs
        connection.execSQL(
            """
            INSERT INTO journal_notes_new (id, uid)
            SELECT id, CAST(uid AS TEXT) 
            FROM journal_notes
            """.trimIndent()
        )
        
        // Drop old tables
        connection.execSQL("DROP TABLE text_notes")
        connection.execSQL("DROP TABLE image_notes")
        connection.execSQL("DROP TABLE journal_notes")
        
        // Rename new tables to original names
        connection.execSQL("ALTER TABLE text_notes_new RENAME TO text_notes")
        connection.execSQL("ALTER TABLE image_notes_new RENAME TO image_notes")
        connection.execSQL("ALTER TABLE journal_notes_new RENAME TO journal_notes")
        
        // Recreate indices for journal_notes
        connection.execSQL("CREATE INDEX index_journal_notes_id ON journal_notes(id)")
        connection.execSQL("CREATE INDEX index_journal_notes_uid ON journal_notes(uid)")
    }
}

/**
 * A migration from version 8 to version 9 of the database.
 *
 * This migration converts all string-based IDs to valid UUID strings.
 * For each table with ID fields:
 * - If the ID is already a valid UUID, keep it as is
 * - If the ID is not a valid UUID (e.g., an integer), replace it with a newly generated Uuid
 *
 * This ensures all IDs can be correctly parsed as kotlin.uuid.Uuid in the code.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    override fun migrate(connection: SQLiteConnection) {
        // Helper function to check if a string is a valid UUID
        fun isValidUuid(str: String): Boolean {
            return try {
                Uuid.parse(str)
                true
            } catch (e: Exception) {
                false
            }
        }

        // Migrate journals table
        connection.execSQL(
            """
            CREATE TABLE journals_new (
                id TEXT NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                created INTEGER NOT NULL,
                lastUpdated INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )

        // Get all existing journals
        val journalIdMap = mutableMapOf<String, String>() // Map old ID to new UUID
        connection.prepare("SELECT id, title, description, created, lastUpdated FROM journals").use { statement ->
            while (statement.step()) {
                val oldId = statement.getText(0)
                val title = statement.getText(1)
                val description = statement.getText(2)
                val created = statement.getLong(3)
                val lastUpdated = statement.getLong(4)

                // Generate new UUID if needed
                val newId = if (isValidUuid(oldId)) oldId else Uuid.random().toString()
                journalIdMap[oldId] = newId

                // Insert into new table with potentially new UUID
                val insertSql = "INSERT INTO journals_new (id, title, description, created, lastUpdated) VALUES (?, ?, ?, ?, ?)"
                connection.prepare(insertSql).use { insertStmt ->
                    insertStmt.bindText(1, newId)
                    insertStmt.bindText(2, title)
                    insertStmt.bindText(3, description)
                    insertStmt.bindLong(4, created)
                    insertStmt.bindLong(5, lastUpdated)
                    insertStmt.step()
                }
            }
        }

        // Migrate text_notes table
        connection.execSQL(
            """
            CREATE TABLE text_notes_new (
                uid TEXT NOT NULL,
                content TEXT NOT NULL,
                lastUpdated INTEGER NOT NULL,
                created INTEGER NOT NULL,
                PRIMARY KEY(uid)
            )
            """.trimIndent()
        )

        // Get all existing text notes
        val textNoteIdMap = mutableMapOf<String, String>() // Map old ID to new UUID
        connection.prepare("SELECT uid, content, lastUpdated, created FROM text_notes").use { statement ->
            while (statement.step()) {
                val oldId = statement.getText(0)
                val content = statement.getText(1)
                val lastUpdated = statement.getLong(2)
                val created = statement.getLong(3)

                // Generate new UUID if needed
                val newId = if (isValidUuid(oldId)) oldId else Uuid.random().toString()
                textNoteIdMap[oldId] = newId

                // Insert into new table with potentially new UUID
                val insertSql = "INSERT INTO text_notes_new (uid, content, lastUpdated, created) VALUES (?, ?, ?, ?)"
                connection.prepare(insertSql).use { insertStmt ->
                    insertStmt.bindText(1, newId)
                    insertStmt.bindText(2, content)
                    insertStmt.bindLong(3, lastUpdated)
                    insertStmt.bindLong(4, created)
                    insertStmt.step()
                }
            }
        }

        // Migrate image_notes table
        connection.execSQL(
            """
            CREATE TABLE image_notes_new (
                uid TEXT NOT NULL,
                contentUri TEXT NOT NULL,
                lastUpdated INTEGER NOT NULL,
                created INTEGER NOT NULL,
                PRIMARY KEY(uid)
            )
            """.trimIndent()
        )

        // Get all existing image notes
        val imageNoteIdMap = mutableMapOf<String, String>() // Map old ID to new UUID
        connection.prepare("SELECT uid, contentUri, lastUpdated, created FROM image_notes").use { statement ->
            while (statement.step()) {
                val oldId = statement.getText(0)
                val contentUri = statement.getText(1)
                val lastUpdated = statement.getLong(2)
                val created = statement.getLong(3)

                // Generate new UUID if needed
                val newId = if (isValidUuid(oldId)) oldId else Uuid.random().toString()
                imageNoteIdMap[oldId] = newId

                // Insert into new table with potentially new UUID
                val insertSql = "INSERT INTO image_notes_new (uid, contentUri, lastUpdated, created) VALUES (?, ?, ?, ?)"
                connection.prepare(insertSql).use { insertStmt ->
                    insertStmt.bindText(1, newId)
                    insertStmt.bindText(2, contentUri)
                    insertStmt.bindLong(3, lastUpdated)
                    insertStmt.bindLong(4, created)
                    insertStmt.step()
                }
            }
        }

        // Migrate the journal_notes relationship table
        connection.execSQL(
            """
            CREATE TABLE journal_notes_new (
                id TEXT NOT NULL,
                uid TEXT NOT NULL,
                PRIMARY KEY(id, uid)
            )
            """.trimIndent()
        )

        // Update relationships with the new UUIDs
        connection.prepare("SELECT id, uid FROM journal_notes").use { statement ->
            while (statement.step()) {
                val oldJournalId = statement.getText(0)
                val oldNoteId = statement.getText(1)

                // Look up new IDs from our mapping tables, defaulting to old IDs if not found
                val newJournalId = journalIdMap[oldJournalId] ?: oldJournalId

                // Note ID could be either a text note or image note
                val newNoteId = textNoteIdMap[oldNoteId] ?: imageNoteIdMap[oldNoteId] ?: oldNoteId

                // Insert updated relationship
                val insertSql = "INSERT INTO journal_notes_new (id, uid) VALUES (?, ?)"
                connection.prepare(insertSql).use { insertStmt ->
                    insertStmt.bindText(1, newJournalId)
                    insertStmt.bindText(2, newNoteId)
                    insertStmt.step()
                }
            }
        }

        // Check if journal_content_links table exists
        var tableExists = false
        connection.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='journal_content_links'").use { statement ->
            tableExists = statement.step()
        }

        // Update the journal_content_links table if it exists
        if (tableExists) {
            connection.execSQL(
                """
                CREATE TABLE journal_content_links_new (
                    journal_id TEXT NOT NULL,
                    content_id TEXT NOT NULL,
                    PRIMARY KEY(journal_id, content_id),
                    FOREIGN KEY(journal_id) REFERENCES journals(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )

            // Update relationships with new UUIDs
            connection.prepare("SELECT journal_id, content_id FROM journal_content_links").use { statement ->
                while (statement.step()) {
                    val oldJournalId = statement.getText(0)
                    val oldContentId = statement.getText(1)

                    // Look up new IDs from our mapping tables
                    val newJournalId = journalIdMap[oldJournalId] ?: oldJournalId

                    // Content ID could be from various tables, try each map
                    val newContentId = textNoteIdMap[oldContentId] ?:
                    imageNoteIdMap[oldContentId] ?:
                    oldContentId

                    // Insert updated relationship
                    val insertSql = "INSERT INTO journal_content_links_new (journal_id, content_id) VALUES (?, ?)"
                    connection.prepare(insertSql).use { insertStmt ->
                        insertStmt.bindText(1, newJournalId)
                        insertStmt.bindText(2, newContentId)
                        insertStmt.step()
                    }
                }
            }

            // Replace the old table
            connection.execSQL("DROP TABLE journal_content_links")
            connection.execSQL("ALTER TABLE journal_content_links_new RENAME TO journal_content_links")

            // Recreate indices
            connection.execSQL("CREATE INDEX index_journal_content_links_journal_id ON journal_content_links(journal_id)")
            connection.execSQL("CREATE INDEX index_journal_content_links_content_id ON journal_content_links(content_id)")
        }

        // Replace all the migrated main tables
        connection.execSQL("DROP TABLE journals")
        connection.execSQL("DROP TABLE text_notes")
        connection.execSQL("DROP TABLE image_notes")
        connection.execSQL("DROP TABLE journal_notes")

        connection.execSQL("ALTER TABLE journals_new RENAME TO journals")
        connection.execSQL("ALTER TABLE text_notes_new RENAME TO text_notes")
        connection.execSQL("ALTER TABLE image_notes_new RENAME TO image_notes")
        connection.execSQL("ALTER TABLE journal_notes_new RENAME TO journal_notes")

        // Recreate indices for journal_notes
        connection.execSQL("CREATE INDEX index_journal_notes_id ON journal_notes(id)")
        connection.execSQL("CREATE INDEX index_journal_notes_uid ON journal_notes(uid)")
    }
}

/**
 * A migration from version 9 to version 10 of the database.
 *
 * This migration converts all string-based IDs to valid UUID strings.
 * For each table with ID fields:
 * - If the ID is already a valid UUID, keep it as is
 * - If the ID is not a valid UUID (e.g., an integer), replace it with a newly generated Uuid
 *
 * This ensures all IDs can be correctly parsed as kotlin.uuid.Uuid in the code.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    override fun migrate(connection: SQLiteConnection) {
        // Helper function to check if a string is a valid UUID
        fun isValidUuid(str: String): Boolean {
            return try {
                Uuid.parse(str)
                true
            } catch (e: Exception) {
                false
            }
        }

        // Migrate journals table
        connection.execSQL(
            """
            CREATE TABLE journals_new (
                id TEXT NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                created INTEGER NOT NULL,
                lastUpdated INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )

        // Get all existing journals
        val journalIdMap = mutableMapOf<String, String>() // Map old ID to new UUID
        connection.prepare("SELECT id, title, description, created, lastUpdated FROM journals").use { statement ->
            while (statement.step()) {
                val oldId = statement.getText(0)
                val title = statement.getText(1)
                val description = statement.getText(2)
                val created = statement.getLong(3)
                val lastUpdated = statement.getLong(4)

                // Generate new UUID if needed
                val newId = if (isValidUuid(oldId)) oldId else Uuid.random().toString()
                journalIdMap[oldId] = newId

                // Insert into new table with potentially new UUID
                val insertSql = "INSERT INTO journals_new (id, title, description, created, lastUpdated) VALUES (?, ?, ?, ?, ?)"
                connection.prepare(insertSql).use { insertStmt ->
                    insertStmt.bindText(1, newId)
                    insertStmt.bindText(2, title)
                    insertStmt.bindText(3, description)
                    insertStmt.bindLong(4, created)
                    insertStmt.bindLong(5, lastUpdated)
                    insertStmt.step()
                }
            }
        }

        // Migrate text_notes table
        connection.execSQL(
            """
            CREATE TABLE text_notes_new (
                uid TEXT NOT NULL,
                content TEXT NOT NULL,
                lastUpdated INTEGER NOT NULL,
                created INTEGER NOT NULL,
                PRIMARY KEY(uid)
            )
            """.trimIndent()
        )

        // Get all existing text notes
        val textNoteIdMap = mutableMapOf<String, String>() // Map old ID to new UUID
        connection.prepare("SELECT uid, content, lastUpdated, created FROM text_notes").use { statement ->
            while (statement.step()) {
                val oldId = statement.getText(0)
                val content = statement.getText(1)
                val lastUpdated = statement.getLong(2)
                val created = statement.getLong(3)

                // Generate new UUID if needed
                val newId = if (isValidUuid(oldId)) oldId else Uuid.random().toString()
                textNoteIdMap[oldId] = newId

                // Insert into new table with potentially new UUID
                val insertSql = "INSERT INTO text_notes_new (uid, content, lastUpdated, created) VALUES (?, ?, ?, ?)"
                connection.prepare(insertSql).use { insertStmt ->
                    insertStmt.bindText(1, newId)
                    insertStmt.bindText(2, content)
                    insertStmt.bindLong(3, lastUpdated)
                    insertStmt.bindLong(4, created)
                    insertStmt.step()
                }
            }
        }

        // Migrate image_notes table
        connection.execSQL(
            """
            CREATE TABLE image_notes_new (
                uid TEXT NOT NULL,
                contentUri TEXT NOT NULL,
                lastUpdated INTEGER NOT NULL,
                created INTEGER NOT NULL,
                PRIMARY KEY(uid)
            )
            """.trimIndent()
        )

        // Get all existing image notes
        val imageNoteIdMap = mutableMapOf<String, String>() // Map old ID to new UUID
        connection.prepare("SELECT uid, contentUri, lastUpdated, created FROM image_notes").use { statement ->
            while (statement.step()) {
                val oldId = statement.getText(0)
                val contentUri = statement.getText(1)
                val lastUpdated = statement.getLong(2)
                val created = statement.getLong(3)

                // Generate new UUID if needed
                val newId = if (isValidUuid(oldId)) oldId else Uuid.random().toString()
                imageNoteIdMap[oldId] = newId

                // Insert into new table with potentially new UUID
                val insertSql = "INSERT INTO image_notes_new (uid, contentUri, lastUpdated, created) VALUES (?, ?, ?, ?)"
                connection.prepare(insertSql).use { insertStmt ->
                    insertStmt.bindText(1, newId)
                    insertStmt.bindText(2, contentUri)
                    insertStmt.bindLong(3, lastUpdated)
                    insertStmt.bindLong(4, created)
                    insertStmt.step()
                }
            }
        }

        // Migrate the journal_notes relationship table
        connection.execSQL(
            """
            CREATE TABLE journal_notes_new (
                id TEXT NOT NULL,
                uid TEXT NOT NULL,
                PRIMARY KEY(id, uid)
            )
            """.trimIndent()
        )

        // Update relationships with the new UUIDs
        connection.prepare("SELECT id, uid FROM journal_notes").use { statement ->
            while (statement.step()) {
                val oldJournalId = statement.getText(0)
                val oldNoteId = statement.getText(1)

                // Look up new IDs from our mapping tables, defaulting to old IDs if not found
                val newJournalId = journalIdMap[oldJournalId] ?: oldJournalId

                // Note ID could be either a text note or image note
                val newNoteId = textNoteIdMap[oldNoteId] ?: imageNoteIdMap[oldNoteId] ?: oldNoteId

                // Insert updated relationship
                val insertSql = "INSERT INTO journal_notes_new (id, uid) VALUES (?, ?)"
                connection.prepare(insertSql).use { insertStmt ->
                    insertStmt.bindText(1, newJournalId)
                    insertStmt.bindText(2, newNoteId)
                    insertStmt.step()
                }
            }
        }

        // Check if journal_content_links table exists
        var tableExists = false
        connection.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='journal_content_links'").use { statement ->
            tableExists = statement.step()
        }

        // Update the journal_content_links table if it exists
        if (tableExists) {
            connection.execSQL(
                """
                CREATE TABLE journal_content_links_new (
                    journal_id TEXT NOT NULL,
                    content_id TEXT NOT NULL,
                    PRIMARY KEY(journal_id, content_id),
                    FOREIGN KEY(journal_id) REFERENCES journals(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )

            // Update relationships with new UUIDs
            connection.prepare("SELECT journal_id, content_id FROM journal_content_links").use { statement ->
                while (statement.step()) {
                    val oldJournalId = statement.getText(0)
                    val oldContentId = statement.getText(1)

                    // Look up new IDs from our mapping tables
                    val newJournalId = journalIdMap[oldJournalId] ?: oldJournalId

                    // Content ID could be from various tables, try each map
                    val newContentId = textNoteIdMap[oldContentId] ?:
                    imageNoteIdMap[oldContentId] ?:
                    oldContentId

                    // Insert updated relationship
                    val insertSql = "INSERT INTO journal_content_links_new (journal_id, content_id) VALUES (?, ?)"
                    connection.prepare(insertSql).use { insertStmt ->
                        insertStmt.bindText(1, newJournalId)
                        insertStmt.bindText(2, newContentId)
                        insertStmt.step()
                    }
                }
            }

            // Replace the old table
            connection.execSQL("DROP TABLE journal_content_links")
            connection.execSQL("ALTER TABLE journal_content_links_new RENAME TO journal_content_links")

            // Recreate indices
            connection.execSQL("CREATE INDEX index_journal_content_links_journal_id ON journal_content_links(journal_id)")
            connection.execSQL("CREATE INDEX index_journal_content_links_content_id ON journal_content_links(content_id)")
        }

        // Replace all the migrated main tables
        connection.execSQL("DROP TABLE journals")
        connection.execSQL("DROP TABLE text_notes")
        connection.execSQL("DROP TABLE image_notes")
        connection.execSQL("DROP TABLE journal_notes")

        connection.execSQL("ALTER TABLE journals_new RENAME TO journals")
        connection.execSQL("ALTER TABLE text_notes_new RENAME TO text_notes")
        connection.execSQL("ALTER TABLE image_notes_new RENAME TO image_notes")
        connection.execSQL("ALTER TABLE journal_notes_new RENAME TO journal_notes")

        // Recreate indices for journal_notes
        connection.execSQL("CREATE INDEX index_journal_notes_id ON journal_notes(id)")
        connection.execSQL("CREATE INDEX index_journal_notes_uid ON journal_notes(uid)")
    }
}

/**
 * A migration from version 10 to version 11 of the database.
 * 
 * This migration adds support for video and voice notes by creating two new tables:
 * - video_notes: for storing video content with optional duration
 * - voice_notes: for storing audio content with optional duration
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(connection: SQLiteConnection) {
        // Create video_notes table
        connection.execSQL(
            """
            CREATE TABLE video_notes (
                contentUri TEXT NOT NULL,
                durationMs INTEGER,
                uid TEXT NOT NULL,
                lastUpdated INTEGER NOT NULL,
                created INTEGER NOT NULL,
                PRIMARY KEY(uid)
            )
            """.trimIndent()
        )
        
        // Create voice_notes table
        connection.execSQL(
            """
            CREATE TABLE voice_notes (
                contentUri TEXT NOT NULL,
                durationMs INTEGER,
                uid TEXT NOT NULL,
                lastUpdated INTEGER NOT NULL,
                created INTEGER NOT NULL,
                PRIMARY KEY(uid)
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(connection: SQLiteConnection) {
        // Create storage_metadata table
        connection.execSQL(
            """
            CREATE TABLE storage_metadata (
                contentId TEXT NOT NULL,
                contentType TEXT NOT NULL,
                sizeBytes INTEGER NOT NULL,
                contentUri TEXT NOT NULL,
                recordedAt INTEGER NOT NULL,
                lastUpdated INTEGER NOT NULL,
                excludeFromQuota INTEGER NOT NULL,
                PRIMARY KEY(contentId)
            )
            """.trimIndent()
        )
    }
}