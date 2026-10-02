@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Adds durable sync recovery after the photo-presentation schema shipped in version 49. */
val MIGRATION_49_50 =
    object : Migration(49, 50) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE pending_uploads ADD COLUMN expectedServerVersion INTEGER")
            connection.execSQL("ALTER TABLE pending_uploads ADD COLUMN operationId TEXT NOT NULL DEFAULT ''")
            connection.execSQL(
                """UPDATE pending_uploads SET operationId = lower(hex(randomblob(4))) || '-' ||
                    lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))), 2) || '-' ||
                    substr('89ab', (random() & 3) + 1, 1) || substr(lower(hex(randomblob(2))), 2) || '-' ||
                    lower(hex(randomblob(6)))""",
            )
            connection.execSQL(
                """CREATE TABLE IF NOT EXISTS sync_download_inbox (
                    ownerId TEXT NOT NULL,
                    serverOrigin TEXT NOT NULL,
                    entityType TEXT NOT NULL,
                    entityId TEXT NOT NULL,
                    version INTEGER NOT NULL,
                    deleted INTEGER NOT NULL,
                    payload TEXT NOT NULL,
                    operationId TEXT NOT NULL,
                    state TEXT NOT NULL,
                    attempts INTEGER NOT NULL,
                    nextAttemptAt INTEGER NOT NULL,
                    reason TEXT NOT NULL,
                    PRIMARY KEY(ownerId, serverOrigin, entityType, entityId)
                )""",
            )
            connection.execSQL(
                """CREATE TABLE IF NOT EXISTS sync_download_checkpoints (
                    ownerId TEXT NOT NULL,
                    serverOrigin TEXT NOT NULL,
                    entityType TEXT NOT NULL,
                    cursor INTEGER NOT NULL,
                    fetchFailure TEXT NOT NULL,
                    PRIMARY KEY(ownerId, serverOrigin, entityType)
                )""",
            )
        }
    }
