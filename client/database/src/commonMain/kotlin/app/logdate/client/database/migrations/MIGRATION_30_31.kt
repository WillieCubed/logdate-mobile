@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_30_31 =
    object : Migration(30, 31) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS postcards (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    modified_at INTEGER NOT NULL,
                    source_moment_ref TEXT,
                    document_json TEXT NOT NULL
                )
                """.trimIndent(),
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_postcards_modified_at ON postcards(modified_at)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_postcards_source_moment_ref ON postcards(source_moment_ref)",
            )

            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS stickers (
                    id TEXT NOT NULL PRIMARY KEY,
                    source_photo_uri TEXT NOT NULL,
                    source_moment_ref TEXT,
                    image_uri TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    label TEXT
                )
                """.trimIndent(),
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_stickers_created_at ON stickers(created_at)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_stickers_source_moment_ref ON stickers(source_moment_ref)",
            )
        }
    }
