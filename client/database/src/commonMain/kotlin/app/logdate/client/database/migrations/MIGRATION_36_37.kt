@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Introduces the `events` and `event_note_links` tables for the event primitive.
 *
 * Events are time-bound things (recitals, parties, trips) that media and notes attach to.
 * Both tables are new — no existing tables are altered.
 *
 * Column types match the [EventEntity] / `EventNoteLinkEntity` declarations: Uuid columns are
 * stored as TEXT (Room's Uuid type converter serializes to a string), and `sync_version` carries
 * its default in Kotlin rather than as a SQL DEFAULT — Room's schema validator rejects DEFAULTs
 * the entity didn't declare.
 */
val MIGRATION_36_37 =
    object : Migration(36, 37) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS events (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    description TEXT,
                    start_time INTEGER NOT NULL,
                    end_time INTEGER,
                    place_id TEXT,
                    cover_image_uri TEXT,
                    external_calendar_id TEXT,
                    external_calendar_source TEXT,
                    created INTEGER NOT NULL,
                    last_updated INTEGER NOT NULL,
                    sync_version INTEGER NOT NULL,
                    last_synced INTEGER,
                    deleted_at INTEGER,
                    FOREIGN KEY(place_id) REFERENCES places(id) ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_events_start_time ON events(start_time)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_events_place_id ON events(place_id)")
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_events_external_calendar_id ON events(external_calendar_id)",
            )

            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS event_note_links (
                    event_id TEXT NOT NULL,
                    note_id TEXT NOT NULL,
                    sync_version INTEGER NOT NULL,
                    last_synced INTEGER,
                    deleted_at INTEGER,
                    PRIMARY KEY(event_id, note_id),
                    FOREIGN KEY(event_id) REFERENCES events(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_event_note_links_event_id ON event_note_links(event_id)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_event_note_links_note_id ON event_note_links(note_id)",
            )
        }
    }
