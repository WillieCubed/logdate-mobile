package app.logdate.client.database.dao.rewind

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.logdate.client.database.entities.rewind.RewindConstants
import app.logdate.client.database.entities.rewind.RewindEntity
import app.logdate.client.database.entities.rewind.RewindImageContentEntity
import app.logdate.client.database.entities.rewind.RewindTextContentEntity
import app.logdate.client.database.entities.rewind.RewindVideoContentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * Data access object for rewinds and their content.
 * 
 * This DAO provides methods for interacting with rewinds and their
 * associated content. It uses separate tables for different content
 * types to ensure type safety and efficiency.
 */
@Dao
interface RewindDao {
    /**
     * Retrieves all rewinds.
     * 
     * @return Flow emitting a list of all rewinds
     */
    @Query("SELECT * FROM rewinds ORDER BY generationDate DESC")
    fun getAllRewinds(): Flow<List<RewindEntity>>
    
    /**
     * Retrieves a rewind by its unique identifier.
     * 
     * @param uid The unique identifier of the rewind to retrieve
     * @return Flow emitting the rewind if found
     */
    @Query("SELECT * FROM rewinds WHERE ${RewindConstants.COLUMN_UID} = :uid")
    fun getRewindById(uid: String): Flow<RewindEntity>
    
    /**
     * Retrieves a rewind for a given time period.
     * 
     * @param start The start of the time period
     * @param end The end of the time period
     * @return Flow emitting the rewind for the given period, or null if none exists
     */
    @Query("""
        SELECT * FROM rewinds 
        WHERE ${RewindConstants.COLUMN_START_DATE} = :start 
        AND ${RewindConstants.COLUMN_END_DATE} = :end
        LIMIT 1
    """)
    fun getRewindForPeriod(start: Instant, end: Instant): Flow<RewindEntity?>
    
    /**
     * Checks if a rewind exists for the given time period.
     * 
     * @param start The start of the time period
     * @param end The end of the time period
     * @return True if a rewind exists for the period, false otherwise
     */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM rewinds 
            WHERE ${RewindConstants.COLUMN_START_DATE} = :start 
            AND ${RewindConstants.COLUMN_END_DATE} = :end
        )
    """)
    suspend fun rewindExistsForPeriod(start: Instant, end: Instant): Boolean
    
    /**
     * Inserts a new rewind.
     * 
     * @param rewind The rewind to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRewind(rewind: RewindEntity)
    
    /**
     * Retrieves all text content for a given rewind.
     * 
     * @param rewindId The ID of the rewind
     * @return List of text content entities
     */
    @Query("SELECT * FROM rewind_text_content WHERE ${RewindConstants.COLUMN_REWIND_ID} = :rewindId ORDER BY timestamp ASC")
    suspend fun getTextContentForRewind(rewindId: String): List<RewindTextContentEntity>
    
    /**
     * Retrieves all image content for a given rewind.
     * 
     * @param rewindId The ID of the rewind
     * @return List of image content entities
     */
    @Query("SELECT * FROM rewind_image_content WHERE ${RewindConstants.COLUMN_REWIND_ID} = :rewindId ORDER BY timestamp ASC")
    suspend fun getImageContentForRewind(rewindId: String): List<RewindImageContentEntity>
    
    /**
     * Retrieves all video content for a given rewind.
     * 
     * @param rewindId The ID of the rewind
     * @return List of video content entities
     */
    @Query("SELECT * FROM rewind_video_content WHERE ${RewindConstants.COLUMN_REWIND_ID} = :rewindId ORDER BY timestamp ASC")
    suspend fun getVideoContentForRewind(rewindId: String): List<RewindVideoContentEntity>
    
    /**
     * Inserts text content.
     * 
     * @param content The text content to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTextContent(content: List<RewindTextContentEntity>)
    
    /**
     * Inserts image content.
     * 
     * @param content The image content to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImageContent(content: List<RewindImageContentEntity>)
    
    /**
     * Inserts video content.
     * 
     * @param content The video content to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideoContent(content: List<RewindVideoContentEntity>)
    
    /**
     * Deletes a rewind and all its content.
     * 
     * @param uid The unique identifier of the rewind to delete
     */
    @Query("DELETE FROM rewinds WHERE ${RewindConstants.COLUMN_UID} = :uid")
    suspend fun deleteRewind(uid: String)
}