@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Introduces the canonical `people` table used by the People primitive.
 *
 * This first slice stores stable person identities imported from contacts or added manually.
 * `aliases` is a separator-encoded string handled by [StringListConverter], so it is stored as
 * TEXT in SQLite. `contact_lookup_key` is nullable because not every person originates from the
 * device address book.
 */
val MIGRATION_39_40 =
    object : Migration(39, 40) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS people (
                    id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    photo_uri TEXT,
                    aliases TEXT NOT NULL,
                    relationship_label TEXT,
                    notes TEXT,
                    origin TEXT NOT NULL,
                    contact_lookup_key TEXT,
                    created INTEGER NOT NULL,
                    last_updated INTEGER NOT NULL,
                    deleted_at INTEGER,
                    PRIMARY KEY(id)
                )
                """.trimIndent(),
            )
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_people_name ON people(name)")
            connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_people_contact_lookup_key ON people(contact_lookup_key)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_people_origin ON people(origin)")
        }
    }
