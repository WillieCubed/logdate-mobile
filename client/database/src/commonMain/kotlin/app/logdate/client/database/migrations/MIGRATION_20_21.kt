package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Migration 20 -> 21: Add sync metadata tables.
 *
 * Creates tables for tracking sync state:
 * - sync_cursors: Stores last sync timestamp per entity type
 * - pending_uploads: Outbox for entities waiting to be uploaded
 */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(connection: SQLiteConnection) {
        // Create sync_cursors table
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_cursors (
                entityType TEXT NOT NULL PRIMARY KEY,
                lastSyncTimestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // Create pending_uploads table
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS pending_uploads (
                entityType TEXT NOT NULL,
                entityId TEXT NOT NULL,
                operation TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                retryCount INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (entityType, entityId)
            )
            """.trimIndent()
        )

        // Index for efficient lookup by entity type
        connection.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_pending_uploads_entity_type
            ON pending_uploads(entityType)
            """.trimIndent()
        )
    }
}
