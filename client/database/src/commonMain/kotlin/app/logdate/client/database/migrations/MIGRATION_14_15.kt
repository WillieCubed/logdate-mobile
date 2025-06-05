package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Migration from database version 14 to 15.
 *
 * This migration updates the indexed media tables to use a more efficient structure:
 * - Drops the old tables (indexed_media, indexed_media_images, indexed_media_videos)
 * - Creates new tables for the refactored class structure (indexed_media_images, indexed_media_videos)
 * - Migrates data from the old structure to the new one
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(connection: SQLiteConnection) {
        // Create temporary tables to store current data
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `temp_indexed_media` (
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
        
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `temp_indexed_media_images` (
                `id` TEXT NOT NULL,
                `mediaId` TEXT NOT NULL,
                `width` INTEGER NOT NULL,
                `height` INTEGER NOT NULL,
                `mimeType` TEXT NOT NULL,
                `fileSize` INTEGER NOT NULL,
                `hasLocationData` INTEGER NOT NULL DEFAULT 0,
                `latitude` REAL,
                `longitude` REAL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `temp_indexed_media_videos` (
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
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        
        // Copy existing data to temp tables
        connection.execSQL("INSERT INTO temp_indexed_media SELECT * FROM indexed_media")
        connection.execSQL("INSERT INTO temp_indexed_media_images SELECT * FROM indexed_media_images")
        connection.execSQL("INSERT INTO temp_indexed_media_videos SELECT * FROM indexed_media_videos")
        
        // Drop old tables
        connection.execSQL("DROP TABLE indexed_media_videos")
        connection.execSQL("DROP TABLE indexed_media_images")
        connection.execSQL("DROP TABLE indexed_media")
        
        // Create new indexed_media_images table with improved structure
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `indexed_media_images` (
                `uid` TEXT NOT NULL,
                `uri` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `indexedAt` INTEGER NOT NULL,
                `mimeType` TEXT NOT NULL,
                `fileSize` INTEGER NOT NULL,
                `dimensions_width` INTEGER NOT NULL,
                `dimensions_height` INTEGER NOT NULL,
                `location_latitude` REAL,
                `location_longitude` REAL,
                PRIMARY KEY(`uid`)
            )
            """.trimIndent()
        )
        
        // Create index on uri to enforce uniqueness
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_indexed_media_images_uri` ON `indexed_media_images` (`uri`)")
        
        // Create new indexed_media_videos table with improved structure
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `indexed_media_videos` (
                `uid` TEXT NOT NULL,
                `uri` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `indexedAt` INTEGER NOT NULL,
                `mimeType` TEXT NOT NULL,
                `fileSize` INTEGER NOT NULL,
                `dimensions_width` INTEGER NOT NULL,
                `dimensions_height` INTEGER NOT NULL,
                `location_latitude` REAL,
                `location_longitude` REAL,
                `duration` INTEGER NOT NULL,
                PRIMARY KEY(`uid`)
            )
            """.trimIndent()
        )
        
        // Create index on uri to enforce uniqueness
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_indexed_media_videos_uri` ON `indexed_media_videos` (`uri`)")
        
        // Migrate image data from the old structure to the new one
        connection.execSQL(
            """
            INSERT INTO indexed_media_images (
                uid, uri, timestamp, indexedAt, mimeType, fileSize,
                dimensions_width, dimensions_height, location_latitude, location_longitude
            )
            SELECT 
                m.uid, m.uri, m.timestamp, m.indexedAt, i.mimeType, i.fileSize,
                i.width, i.height, i.latitude, i.longitude
            FROM temp_indexed_media m
            JOIN temp_indexed_media_images i ON m.uid = i.mediaId
            WHERE m.mediaType = 'IMAGE'
            """.trimIndent()
        )
        
        // Migrate video data from the old structure to the new one
        connection.execSQL(
            """
            INSERT INTO indexed_media_videos (
                uid, uri, timestamp, indexedAt, mimeType, fileSize,
                dimensions_width, dimensions_height, location_latitude, location_longitude, duration
            )
            SELECT 
                m.uid, m.uri, m.timestamp, m.indexedAt, v.mimeType, v.fileSize,
                v.width, v.height, v.latitude, v.longitude, v.durationMs
            FROM temp_indexed_media m
            JOIN temp_indexed_media_videos v ON m.uid = v.mediaId
            WHERE m.mediaType = 'VIDEO'
            """.trimIndent()
        )
        
        // Drop temporary tables
        connection.execSQL("DROP TABLE temp_indexed_media_videos")
        connection.execSQL("DROP TABLE temp_indexed_media_images")
        connection.execSQL("DROP TABLE temp_indexed_media")
    }
}