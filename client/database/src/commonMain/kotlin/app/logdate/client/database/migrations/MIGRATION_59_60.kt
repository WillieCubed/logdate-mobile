@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Indexes every note table and indexed media by time, so the timeline, paging and "on this day"
 * queries read only the rows they return instead of sorting each table on every change.
 */
val MIGRATION_59_60 =
    object : Migration(59, 60) {
        override fun migrate(connection: SQLiteConnection) {
            for (table in listOf("text_notes", "image_notes", "audio_notes", "video_notes")) {
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_${table}_created` ON `$table` (`created`)")
            }
            for (table in listOf("indexed_media_images", "indexed_media_videos")) {
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_${table}_timestamp` ON `$table` (`timestamp`)")
            }
        }
    }
