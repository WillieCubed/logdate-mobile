package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import io.github.aakira.napier.Napier

/**
 * Migration from database version 17 to 18.
 *
 * This migration ensures the database has the proper index on the rewindId column
 * in the rewind_generation_requests table to optimize queries and avoid
 * full table scans when the parent table is modified.
 */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(connection: SQLiteConnection) {
        // Drop the index if it exists to avoid conflicts
        // This is needed to ensure we have a clean state
        try {
            connection.execSQL(
                """
                DROP INDEX IF EXISTS `index_rewind_generation_requests_rewindId`
                """.trimIndent()
            )
            
            // Create the index
            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_rewind_generation_requests_rewindId` 
                ON `rewind_generation_requests` (`rewindId`)
                """.trimIndent()
            )
            
            Napier.d("Migration 17->18: Successfully created index on rewindId column")
        } catch (e: Exception) {
            Napier.e("Migration 17->18: Failed to create index", e)
        }
    }
}