package app.logdate.client.database.dao.rewind

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.logdate.client.database.entities.rewind.RewindGenerationRequestEntity
import app.logdate.client.database.entities.rewind.RewindGenerationRequestEntity.Status
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Data access object for rewind generation requests.
 * 
 * This DAO manages the persistence of rewind generation requests, which represent
 * the process of creating a rewind for a specific time period.
 */
@Dao
interface RewindGenerationRequestDao {
    /**
     * Retrieves all rewind generation requests.
     * 
     * @return Flow emitting a list of all requests
     */
    @Query("SELECT * FROM rewind_generation_requests ORDER BY requestTime DESC")
    fun getAllRequests(): Flow<List<RewindGenerationRequestEntity>>
    
    /**
     * Retrieves a specific rewind generation request by its ID.
     * 
     * @param id The unique identifier of the request
     * @return Flow emitting the request if found
     */
    @Query("SELECT * FROM rewind_generation_requests WHERE id = :id")
    fun getRequestById(id: Uuid): Flow<RewindGenerationRequestEntity?>
    
    /**
     * Retrieves all requests with a specific status.
     * 
     * @param status The status to filter by
     * @return Flow emitting a list of matching requests
     */
    @Query("SELECT * FROM rewind_generation_requests WHERE status = :status ORDER BY requestTime DESC")
    fun getRequestsByStatus(status: Status): Flow<List<RewindGenerationRequestEntity>>
    
    /**
     * Retrieves all pending or processing requests.
     * 
     * @return Flow emitting a list of active requests
     */
    @Query("SELECT * FROM rewind_generation_requests WHERE status IN ('PENDING', 'PROCESSING') ORDER BY requestTime ASC")
    fun getActiveRequests(): Flow<List<RewindGenerationRequestEntity>>
    
    /**
     * Checks if a request exists for a specific time period.
     * 
     * @param startTime The start of the time period
     * @param endTime The end of the time period
     * @return True if a request exists, false otherwise
     */
    @Query("SELECT EXISTS(SELECT 1 FROM rewind_generation_requests WHERE startTime = :startTime AND endTime = :endTime)")
    suspend fun requestExistsForPeriod(startTime: Instant, endTime: Instant): Boolean
    
    /**
     * Retrieves a request for a specific time period if it exists.
     * 
     * @param startTime The start of the time period
     * @param endTime The end of the time period
     * @return The request entity if found, null otherwise
     */
    @Query("SELECT * FROM rewind_generation_requests WHERE startTime = :startTime AND endTime = :endTime LIMIT 1")
    suspend fun getRequestForPeriod(startTime: Instant, endTime: Instant): RewindGenerationRequestEntity?
    
    /**
     * Inserts a new rewind generation request.
     * 
     * @param request The request entity to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: RewindGenerationRequestEntity)
    
    /**
     * Updates the status of a rewind generation request.
     * 
     * @param id The unique identifier of the request
     * @param status The new status
     * @param details Optional details about the status change
     * @return The number of rows updated
     */
    @Query("UPDATE rewind_generation_requests SET status = :status, details = :details WHERE id = :id")
    suspend fun updateRequestStatus(id: Uuid, status: Status, details: String? = null): Int
    
    /**
     * Updates a rewind generation request with the generated rewind ID.
     * 
     * @param id The unique identifier of the request
     * @param rewindId The ID of the generated rewind
     * @return The number of rows updated
     */
    @Query("UPDATE rewind_generation_requests SET rewindId = :rewindId, status = 'COMPLETED' WHERE id = :id")
    suspend fun updateWithRewindId(id: Uuid, rewindId: Uuid): Int
    
    /**
     * Deletes a rewind generation request.
     * 
     * @param id The unique identifier of the request to delete
     * @return The number of rows deleted
     */
    @Query("DELETE FROM rewind_generation_requests WHERE id = :id")
    suspend fun deleteRequest(id: Uuid): Int
    
    /**
     * Deletes all completed, failed, or cancelled requests.
     * 
     * @return The number of rows deleted
     */
    @Query("DELETE FROM rewind_generation_requests WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    suspend fun cleanupFinishedRequests(): Int
}