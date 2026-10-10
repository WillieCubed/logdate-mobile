@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_51_52 =
    object : Migration(51, 52) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """CREATE TABLE IF NOT EXISTS journal_merges (
                ownerId TEXT NOT NULL, serverOrigin TEXT NOT NULL, sourceId TEXT NOT NULL,
                destinationId TEXT NOT NULL, operationId TEXT NOT NULL, contentIds TEXT NOT NULL,
                sourceTitle TEXT NOT NULL, sourceJournal TEXT NOT NULL, destinationTitle TEXT NOT NULL, pending INTEGER NOT NULL,
                createdAt INTEGER NOT NULL, requestedDestinationId TEXT NOT NULL, needsDestination INTEGER NOT NULL, previousOperationIds TEXT NOT NULL, recoveryContentIds TEXT NOT NULL, PRIMARY KEY(ownerId, serverOrigin, sourceId))""",
            )
        }
    }
