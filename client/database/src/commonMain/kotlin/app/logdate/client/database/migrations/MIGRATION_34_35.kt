@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Adds cover_image_uri column to the journals table for user-chosen cover photos.
 */
val MIGRATION_34_35 =
    object : Migration(34, 35) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE journals ADD COLUMN coverImageUri TEXT")
        }
    }
