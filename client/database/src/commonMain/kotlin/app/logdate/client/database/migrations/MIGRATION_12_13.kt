package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import app.logdate.client.database.entities.rewind.RewindConstants

/**
 * Migration from database version 12 to 13.
 * 
 * This migration:
 * 1. Drops the old rewinds table (no data migration needed since it was just a placeholder)
 * 2. Creates the new rewinds table with all required fields
 * 3. Creates separate tables for different types of rewind content
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(connection: SQLiteConnection) {
        // Drop the old rewinds table completely
        connection.execSQL("DROP TABLE IF EXISTS rewinds")
        
        // Create the new rewinds table with all required fields
        connection.execSQL("""
            CREATE TABLE rewinds (
                ${RewindConstants.COLUMN_UID} TEXT NOT NULL PRIMARY KEY,
                ${RewindConstants.COLUMN_START_DATE} INTEGER NOT NULL,
                ${RewindConstants.COLUMN_END_DATE} INTEGER NOT NULL,
                generationDate INTEGER NOT NULL,
                label TEXT NOT NULL,
                title TEXT NOT NULL
            )
        """)
        
        // Create index on start/end date
        connection.execSQL(
            "CREATE UNIQUE INDEX index_rewinds_startDate_endDate ON rewinds " +
            "(${RewindConstants.COLUMN_START_DATE}, ${RewindConstants.COLUMN_END_DATE})"
        )
        
        // Create text content table
        connection.execSQL("""
            CREATE TABLE rewind_text_content (
                id TEXT NOT NULL PRIMARY KEY,
                ${RewindConstants.COLUMN_REWIND_ID} TEXT NOT NULL,
                ${RewindConstants.COLUMN_SOURCE_ID} TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                content TEXT NOT NULL,
                FOREIGN KEY (${RewindConstants.COLUMN_REWIND_ID}) 
                    REFERENCES rewinds(${RewindConstants.COLUMN_UID}) 
                    ON DELETE CASCADE
            )
        """)
        
        // Create indices for text content
        connection.execSQL(
            "CREATE INDEX index_rewind_text_content_rewindId ON rewind_text_content (${RewindConstants.COLUMN_REWIND_ID})"
        )
        connection.execSQL(
            "CREATE INDEX index_rewind_text_content_sourceId ON rewind_text_content (${RewindConstants.COLUMN_SOURCE_ID})"
        )
        
        // Create image content table
        connection.execSQL("""
            CREATE TABLE rewind_image_content (
                id TEXT NOT NULL PRIMARY KEY,
                ${RewindConstants.COLUMN_REWIND_ID} TEXT NOT NULL,
                ${RewindConstants.COLUMN_SOURCE_ID} TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                uri TEXT NOT NULL,
                caption TEXT,
                FOREIGN KEY (${RewindConstants.COLUMN_REWIND_ID}) 
                    REFERENCES rewinds(${RewindConstants.COLUMN_UID}) 
                    ON DELETE CASCADE
            )
        """)
        
        // Create indices for image content
        connection.execSQL(
            "CREATE INDEX index_rewind_image_content_rewindId ON rewind_image_content (${RewindConstants.COLUMN_REWIND_ID})"
        )
        connection.execSQL(
            "CREATE INDEX index_rewind_image_content_sourceId ON rewind_image_content (${RewindConstants.COLUMN_SOURCE_ID})"
        )
        
        // Create video content table
        connection.execSQL("""
            CREATE TABLE rewind_video_content (
                id TEXT NOT NULL PRIMARY KEY,
                ${RewindConstants.COLUMN_REWIND_ID} TEXT NOT NULL,
                ${RewindConstants.COLUMN_SOURCE_ID} TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                uri TEXT NOT NULL,
                caption TEXT,
                duration TEXT NOT NULL,
                FOREIGN KEY (${RewindConstants.COLUMN_REWIND_ID}) 
                    REFERENCES rewinds(${RewindConstants.COLUMN_UID}) 
                    ON DELETE CASCADE
            )
        """)
        
        // Create indices for video content
        connection.execSQL(
            "CREATE INDEX index_rewind_video_content_rewindId ON rewind_video_content (${RewindConstants.COLUMN_REWIND_ID})"
        )
        connection.execSQL(
            "CREATE INDEX index_rewind_video_content_sourceId ON rewind_video_content (${RewindConstants.COLUMN_SOURCE_ID})"
        )
    }
}