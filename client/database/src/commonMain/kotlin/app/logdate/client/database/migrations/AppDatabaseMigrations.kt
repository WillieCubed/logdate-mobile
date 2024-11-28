package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * A migration from version 1 to version 2 of the database.
 *
 * This migration adds a new table to the database to store the many-to-many relationship between
 * journals and notes.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SQLiteConnection) {
        db.execSQL(
            """
            CREATE TABLE journal_notes (
                uid INTEGER NOT NULL,
                id INTEGER NOT NULL,
                PRIMARY KEY(id, uid)
            )
            """.trimIndent()
        )
        // Create indices on id and uid
        db.execSQL("CREATE INDEX index_journal_notes_id ON journal_notes(id)")
        db.execSQL("CREATE INDEX index_journal_notes_uid ON journal_notes(uid)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SQLiteConnection) {
        db.execSQL(
            """
            CREATE TABLE user_devices (
                uid TEXT NOT NULL,
                user_id TEXT NOT NULL,
                label TEXT NOT NULL,
                operating_system TEXT NOT NULL,
                version TEXT NOT NULL,
                model TEXT NOT NULL,
                type TEXT NOT NULL,
                added INTEGER NOT NULL, 
                PRIMARY KEY(uid)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE location_logs (
                user_id TEXT NOT NULL,
                device_id TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                latitude DOUBLE NOT NULL,
                longitude DOUBLE NOT NULL,
                altitude DOUBLE NOT NULL,
                confidence REAL NOT NULL,
                is_genuine INTEGER NOT NULL,
                PRIMARY KEY(user_id, device_id, timestamp)
            )
        """.trimIndent()
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SQLiteConnection) {
        db.execSQL(
            """
            CREATE TABLE media_images (
                lastUpdated INTEGER NOT NULL,
                id INTEGER NOT NULL,
                addedTimestamp INTEGER NOT NULL,
                uri TEXT NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SQLiteConnection) {
        db.execSQL(
            """

            CREATE TABLE IF NOT EXISTS rewinds (
                uid TEXT NOT NULL,
                PRIMARY KEY(uid)
            )
            """.trimIndent()
        )
    }
}