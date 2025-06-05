package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Migration from database version 13 to 14.
 *
 * This migration adds tables for indexed media (images and videos) and rewind generation requests.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(connection: SQLiteConnection) {
        // Create indexed_media table
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `indexed_media` (
                `uid` TEXT NOT NULL,
                `uri` TEXT NOT NULL,
                `mediaType` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `caption` TEXT,
                `indexedAt` INTEGER NOT NULL,
                `processed` INTEGER NOT NULL DEFAULT 0,
                `thumbnailUri` TEXT,
                PRIMARY KEY(`uid`)
            )
            """.trimIndent()
        )
        
        // Create index on uri to enforce uniqueness
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_indexed_media_uri` ON `indexed_media` (`uri`)")
        
        // Create indexed_media_images table
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `indexed_media_images` (
                `id` TEXT NOT NULL,
                `mediaId` TEXT NOT NULL,
                `width` INTEGER NOT NULL,
                `height` INTEGER NOT NULL,
                `mimeType` TEXT NOT NULL,
                `fileSize` INTEGER NOT NULL,
                `hasLocationData` INTEGER NOT NULL DEFAULT 0,
                `latitude` REAL,
                `longitude` REAL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`mediaId`) REFERENCES `indexed_media`(`uid`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        
        // Create index on mediaId to enforce uniqueness and for foreign key lookup
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_indexed_media_images_mediaId` ON `indexed_media_images` (`mediaId`)")
        
        // Create indexed_media_videos table
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `indexed_media_videos` (
                `id` TEXT NOT NULL,
                `mediaId` TEXT NOT NULL,
                `width` INTEGER NOT NULL,
                `height` INTEGER NOT NULL,
                `mimeType` TEXT NOT NULL,
                `fileSize` INTEGER NOT NULL,
                `durationMs` INTEGER NOT NULL,
                `hasLocationData` INTEGER NOT NULL DEFAULT 0,
                `latitude` REAL,
                `longitude` REAL,
                `thumbnailFrameUri` TEXT,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`mediaId`) REFERENCES `indexed_media`(`uid`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        
        // Create index on mediaId to enforce uniqueness and for foreign key lookup
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_indexed_media_videos_mediaId` ON `indexed_media_videos` (`mediaId`)")
        
        // Create rewind_generation_requests table
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `rewind_generation_requests` (
                `id` TEXT NOT NULL,
                `startTime` INTEGER NOT NULL,
                `endTime` INTEGER NOT NULL,
                `requestTime` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `details` TEXT,
                `rewindId` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        
        // Create unique index on startTime and endTime to prevent duplicate requests for the same period
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_rewind_generation_requests_startTime_endTime` ON `rewind_generation_requests` (`startTime`, `endTime`)")
    }
}