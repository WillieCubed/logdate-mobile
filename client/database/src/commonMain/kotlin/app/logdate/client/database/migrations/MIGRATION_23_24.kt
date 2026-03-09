@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Migration 23 -> 24: scope sync metadata tables by server origin.
 *
 * Existing rows are preserved under the blank server origin and are promoted to the
 * current configured origin on first access by the sync metadata service.
 */
val MIGRATION_23_24 =
    object : Migration(23, 24) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sync_cursors_new (
                    serverOrigin TEXT NOT NULL DEFAULT '',
                    entityType TEXT NOT NULL,
                    lastSyncTimestamp INTEGER NOT NULL,
                    PRIMARY KEY(serverOrigin, entityType)
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO sync_cursors_new (
                    serverOrigin,
                    entityType,
                    lastSyncTimestamp
                )
                SELECT
                    '',
                    entityType,
                    lastSyncTimestamp
                FROM sync_cursors
                """.trimIndent(),
            )
            connection.execSQL("DROP TABLE sync_cursors")
            connection.execSQL("ALTER TABLE sync_cursors_new RENAME TO sync_cursors")

            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS pending_uploads_new (
                    serverOrigin TEXT NOT NULL DEFAULT '',
                    entityType TEXT NOT NULL,
                    entityId TEXT NOT NULL,
                    operation TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    retryCount INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(serverOrigin, entityType, entityId)
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO pending_uploads_new (
                    serverOrigin,
                    entityType,
                    entityId,
                    operation,
                    createdAt,
                    retryCount
                )
                SELECT
                    '',
                    entityType,
                    entityId,
                    operation,
                    createdAt,
                    retryCount
                FROM pending_uploads
                """.trimIndent(),
            )
            connection.execSQL("DROP TABLE pending_uploads")
            connection.execSQL("ALTER TABLE pending_uploads_new RENAME TO pending_uploads")
        }
    }
