package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Migration 21 -> 22: Add location support to all note types.
 *
 * This migration implements the hybrid location approach:
 * - Creates the `places` table for ActivityPub-aligned semantic locations
 * - Adds embedded coordinates (latitude, longitude, altitude, accuracy) to all note tables
 * - Adds optional place_id foreign key to all note tables for semantic place references
 *
 * The hybrid approach preserves exact GPS coordinates at note creation while allowing
 * places to be associated later for display purposes (e.g., "Home", "Work").
 */
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(connection: SQLiteConnection) {
        // Create places table (ActivityPub-aligned)
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS places (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                altitude REAL,
                accuracy REAL,
                radius REAL NOT NULL,
                units TEXT NOT NULL,
                description TEXT,
                ap_uri TEXT,
                created INTEGER NOT NULL,
                last_updated INTEGER NOT NULL,
                sync_version INTEGER NOT NULL,
                last_synced INTEGER,
                deleted_at INTEGER
            )
            """.trimIndent()
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_places_latitude_longitude ON places(latitude, longitude)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_places_ap_uri ON places(ap_uri)")

        // Add location fields to all note tables
        val noteTables = listOf("text_notes", "image_notes", "video_notes", "voice_notes")

        for (table in noteTables) {
            // Add coordinate columns
            connection.execSQL("ALTER TABLE $table ADD COLUMN latitude REAL")
            connection.execSQL("ALTER TABLE $table ADD COLUMN longitude REAL")
            connection.execSQL("ALTER TABLE $table ADD COLUMN altitude REAL")
            connection.execSQL("ALTER TABLE $table ADD COLUMN location_accuracy REAL")
            connection.execSQL("ALTER TABLE $table ADD COLUMN place_id TEXT REFERENCES places(id) ON DELETE SET NULL")

            // Create indices for location queries
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_${table}_place_id ON $table(place_id)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_${table}_coords ON $table(latitude, longitude)")
        }
    }
}
