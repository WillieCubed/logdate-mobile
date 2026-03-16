@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_28_29 =
    object : Migration(28, 29) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS media_exif_metadata (
                    mediaUid TEXT NOT NULL PRIMARY KEY,
                    cameraMake TEXT,
                    cameraModel TEXT,
                    aperture REAL,
                    iso INTEGER,
                    focalLength REAL,
                    shutterSpeed TEXT,
                    whiteBalance TEXT,
                    flashFired INTEGER,
                    orientation INTEGER,
                    extractedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }
