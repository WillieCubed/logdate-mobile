package app.logdate.client.database.entities.rewind

/**
 * Constants for indexed columns in the rewind database tables.
 * 
 * This object contains constants for column names that are used in
 * indices and foreign keys across the rewind database entities.
 * Using these constants ensures consistency for these critical fields.
 */
object RewindConstants {
    /**
     * Primary key column for RewindEntity
     */
    const val COLUMN_UID = "uid"
    
    /**
     * Foreign key and indexed columns
     */
    const val COLUMN_REWIND_ID = "rewindId"
    const val COLUMN_SOURCE_ID = "sourceId"
    
    /**
     * Compound index columns for date ranges
     */
    const val COLUMN_START_DATE = "startDate"
    const val COLUMN_END_DATE = "endDate"
}