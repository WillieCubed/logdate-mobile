@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Adds structured transcript metadata needed by live local/cloud recognition.
 */
val MIGRATION_42_43 =
    object : Migration(42, 43) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE transcriptions ADD COLUMN documentJson TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE transcriptions ADD COLUMN language TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE transcriptions ADD COLUMN source TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE transcriptions ADD COLUMN modelId TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE transcriptions ADD COLUMN revision INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE transcriptions ADD COLUMN isCloudEnhanced INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE transcriptions ADD COLUMN speakerCount INTEGER NOT NULL DEFAULT 0")
        }
    }
