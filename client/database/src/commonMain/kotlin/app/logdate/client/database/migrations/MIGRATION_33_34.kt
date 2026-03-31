@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_33_34 =
    object : Migration(33, 34) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS search_index_metadata (
                    id INTEGER NOT NULL PRIMARY KEY CHECK (id = 1),
                    schemaVersion INTEGER NOT NULL,
                    generation INTEGER NOT NULL DEFAULT 0,
                    lastRebuiltAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO search_index_metadata(id, schemaVersion, generation, lastRebuiltAt)
                SELECT 1, 0, 0, 0
                WHERE NOT EXISTS(SELECT 1 FROM search_index_metadata WHERE id = 1)
                """.trimIndent(),
            )
        }
    }
