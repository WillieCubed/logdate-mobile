package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Migration from database version 15 to 16.
 *
 * This migration adds the missing foreign key reference between rewind_generation_requests and rewinds tables.
 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(connection: SQLiteConnection) {
        // Create a temporary table with the correct structure including the foreign key
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `rewind_generation_requests_new` (
                `id` TEXT NOT NULL,
                `startTime` INTEGER NOT NULL,
                `endTime` INTEGER NOT NULL,
                `requestTime` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `details` TEXT,
                `rewindId` TEXT,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`rewindId`) REFERENCES `rewinds`(`uid`) ON DELETE SET NULL
            )
            """.trimIndent()
        )
        
        // Copy data from the old table to the new one
        connection.execSQL(
            """
            INSERT INTO rewind_generation_requests_new (id, startTime, endTime, requestTime, status, details, rewindId)
            SELECT id, startTime, endTime, requestTime, status, details, rewindId 
            FROM rewind_generation_requests
            """.trimIndent()
        )
        
        // Drop the old table
        connection.execSQL("DROP TABLE rewind_generation_requests")
        
        // Rename the new table to the original name
        connection.execSQL("ALTER TABLE rewind_generation_requests_new RENAME TO rewind_generation_requests")
        
        // Recreate the unique index
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_rewind_generation_requests_startTime_endTime` " +
            "ON `rewind_generation_requests` (`startTime`, `endTime`)"
        )
    }
}