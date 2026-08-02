@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Scopes sync metadata to the durable local owner. Existing rows remain deliberately unowned
 * until the metadata service adopts them for the canonical local identity on first access.
 */
val MIGRATION_45_46 =
    object : Migration(45, 46) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE sync_cursors_new (
                    ownerId TEXT NOT NULL DEFAULT '',
                    serverOrigin TEXT NOT NULL,
                    entityType TEXT NOT NULL,
                    lastSyncTimestamp INTEGER NOT NULL,
                    PRIMARY KEY(ownerId, serverOrigin, entityType)
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO sync_cursors_new (ownerId, serverOrigin, entityType, lastSyncTimestamp)
                SELECT '', serverOrigin, entityType, lastSyncTimestamp FROM sync_cursors
                """.trimIndent(),
            )
            connection.execSQL("DROP TABLE sync_cursors")
            connection.execSQL("ALTER TABLE sync_cursors_new RENAME TO sync_cursors")
            connection.execSQL(
                """
                CREATE TABLE pending_uploads_new (
                    ownerId TEXT NOT NULL DEFAULT '',
                    serverOrigin TEXT NOT NULL,
                    entityType TEXT NOT NULL,
                    entityId TEXT NOT NULL,
                    operation TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    retryCount INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(ownerId, serverOrigin, entityType, entityId)
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO pending_uploads_new (ownerId, serverOrigin, entityType, entityId, operation, createdAt, retryCount)
                SELECT '', serverOrigin, entityType, entityId, operation, createdAt, retryCount FROM pending_uploads
                """.trimIndent(),
            )
            connection.execSQL("DROP TABLE pending_uploads")
            connection.execSQL("ALTER TABLE pending_uploads_new RENAME TO pending_uploads")
        }
    }
