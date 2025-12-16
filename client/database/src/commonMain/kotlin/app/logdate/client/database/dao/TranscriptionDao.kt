package app.logdate.client.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.logdate.client.database.entities.TranscriptionEntity
import app.logdate.client.database.entities.TranscriptionStatus
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * Data Access Object for [TranscriptionEntity].
 */
@Dao
interface TranscriptionDao {
    /**
     * Inserts a new transcription entry.
     * 
     * @param transcription The transcription to insert.
     * @return The ID of the inserted transcription.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscription(transcription: TranscriptionEntity): Long
    
    /**
     * Updates an existing transcription.
     * 
     * @param transcription The transcription to update.
     * @return The number of rows updated.
     */
    @Update
    suspend fun updateTranscription(transcription: TranscriptionEntity): Int
    
    /**
     * Gets a transcription by its ID.
     * 
     * @param id The ID of the transcription to get.
     * @return The transcription, or null if not found.
     */
    @Query("SELECT * FROM transcriptions WHERE id = :id LIMIT 1")
    suspend fun getTranscriptionById(id: Uuid): TranscriptionEntity?
    
    /**
     * Gets a transcription by the note ID it's associated with.
     * 
     * @param noteId The ID of the note to get the transcription for.
     * @return The transcription, or null if not found.
     */
    @Query("SELECT * FROM transcriptions WHERE noteId = :noteId LIMIT 1")
    suspend fun getTranscriptionByNoteId(noteId: Uuid): TranscriptionEntity?
    
    /**
     * Observes a transcription by the note ID it's associated with.
     * 
     * @param noteId The ID of the note to observe the transcription for.
     * @return A Flow emitting the transcription whenever it changes, or null if not found.
     */
    @Query("SELECT * FROM transcriptions WHERE noteId = :noteId LIMIT 1")
    fun observeTranscriptionByNoteId(noteId: Uuid): Flow<TranscriptionEntity?>
    
    /**
     * Gets all transcriptions.
     * 
     * @return A list of all transcriptions.
     */
    @Query("SELECT * FROM transcriptions")
    suspend fun getAllTranscriptions(): List<TranscriptionEntity>
    
    /**
     * Gets all transcriptions with a specific status.
     * 
     * @param status The status to filter by.
     * @return A list of all transcriptions with the given status.
     */
    @Query("SELECT * FROM transcriptions WHERE status = :status")
    suspend fun getTranscriptionsByStatus(status: TranscriptionStatus): List<TranscriptionEntity>
    
    /**
     * Gets all pending or in-progress transcriptions.
     * 
     * @return A list of all transcriptions that are either pending or in progress.
     */
    @Query("SELECT * FROM transcriptions WHERE status = 'PENDING' OR status = 'IN_PROGRESS'")
    suspend fun getActiveTranscriptions(): List<TranscriptionEntity>
    
    /**
     * Updates the status of a transcription.
     * 
     * @param id The ID of the transcription to update.
     * @param status The new status.
     * @param errorMessage Optional error message if status is FAILED.
     * @param timestamp The timestamp when the update occurred.
     * @return The number of rows updated.
     */
    @Query("UPDATE transcriptions SET status = :status, errorMessage = :errorMessage, lastUpdated = :timestamp WHERE id = :id")
    suspend fun updateTranscriptionStatus(
        id: Uuid,
        status: TranscriptionStatus,
        errorMessage: String? = null,
        timestamp: kotlinx.datetime.Instant
    ): Int
    
    /**
     * Updates the text of a transcription.
     * 
     * @param id The ID of the transcription to update.
     * @param text The new transcription text.
     * @param timestamp The timestamp when the update occurred.
     * @return The number of rows updated.
     */
    @Query("UPDATE transcriptions SET text = :text, lastUpdated = :timestamp WHERE id = :id")
    suspend fun updateTranscriptionText(
        id: Uuid,
        text: String,
        timestamp: kotlinx.datetime.Instant
    ): Int
    
    /**
     * Updates both the status and text of a transcription.
     * 
     * @param id The ID of the transcription to update.
     * @param status The new status.
     * @param text The new transcription text.
     * @param errorMessage Optional error message if status is FAILED.
     * @param timestamp The timestamp when the update occurred.
     * @return The number of rows updated.
     */
    @Transaction
    suspend fun updateTranscriptionStatusAndText(
        id: Uuid,
        status: TranscriptionStatus,
        text: String?,
        errorMessage: String? = null,
        timestamp: kotlinx.datetime.Instant
    ): Int {
        return if (text != null) {
            updateTranscriptionText(id, text, timestamp)
            updateTranscriptionStatus(id, status, errorMessage, timestamp)
        } else {
            updateTranscriptionStatus(id, status, errorMessage, timestamp)
        }
    }
    
    /**
     * Deletes a transcription by its ID.
     * 
     * @param id The ID of the transcription to delete.
     * @return The number of rows deleted.
     */
    @Query("DELETE FROM transcriptions WHERE id = :id")
    suspend fun deleteTranscription(id: Uuid): Int
    
    /**
     * Deletes a transcription by the note ID it's associated with.
     * 
     * @param noteId The ID of the note whose transcription should be deleted.
     * @return The number of rows deleted.
     */
    @Query("DELETE FROM transcriptions WHERE noteId = :noteId")
    suspend fun deleteTranscriptionByNoteId(noteId: Uuid): Int
}