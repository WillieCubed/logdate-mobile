package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Migration from database version 16 to 17.
 *
 * This migration:
 * 1. Adds an index on the rewindId column in the rewind_generation_requests table
 *    to optimize queries and avoid full table scans when the parent table is modified.
 * 2. Creates a new transcriptions table for storing transcribed audio content.
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(connection: SQLiteConnection) {
        // Add index on rewindId column
        connection.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_rewind_generation_requests_rewindId` 
            ON `rewind_generation_requests` (`rewindId`)
            """.trimIndent()
        )
        
        // Create transcriptions table
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `transcriptions` (
                `noteId` TEXT NOT NULL,
                `text` TEXT,
                `status` TEXT NOT NULL,
                `errorMessage` TEXT,
                `created` INTEGER NOT NULL,
                `lastUpdated` INTEGER NOT NULL,
                `id` TEXT NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`noteId`) REFERENCES `voice_notes`(`uid`) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        
        // Create unique index for noteId in transcriptions table
        connection.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_transcriptions_noteId` 
            ON `transcriptions` (`noteId`)
            """.trimIndent()
        )
    }
}