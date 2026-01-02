package app.logdate.client.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.logdate.client.database.entities.VideoNoteEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface VideoNoteDao {

    /**
     * Retrieves an observable [VideoNoteEntity] by the given ID.
     */
    @Query("SELECT * FROM video_notes WHERE uid = :uid")
    fun getNote(uid: Uuid): Flow<VideoNoteEntity>

    /**
     * Fetches a non-observable [VideoNoteEntity] by its ID.
     */
    @Query("SELECT * FROM video_notes WHERE uid = :uid")
    suspend fun getNoteOneOff(uid: Uuid): VideoNoteEntity

    /**
     * Returns a flow of all [VideoNoteEntity]s.
     */
    @Query("SELECT * FROM video_notes ORDER BY created DESC")
    fun getAllNotes(): Flow<List<VideoNoteEntity>>

    /**
     * Returns all video notes as a list (for quota calculation).
     */
    @Query("SELECT * FROM video_notes")
    suspend fun getAll(): List<VideoNoteEntity>


    /**
     * Fast query for recent notes to enable immediate timeline display.
     */
    @Query("SELECT * FROM video_notes ORDER BY created DESC LIMIT :limit")
    fun getRecentNotes(limit: Int = 20): Flow<List<VideoNoteEntity>>

    /**
     * Returns notes within a specific date range.
     */
    @Query("SELECT * FROM video_notes WHERE created BETWEEN :startTimestamp AND :endTimestamp ORDER BY created DESC")
    fun getNotesInRange(startTimestamp: Long, endTimestamp: Long): Flow<List<VideoNoteEntity>>

    /**
     * Inserts a [note] into the DB if it doesn't exist, and ignores it if it does.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addNote(note: VideoNoteEntity)

    /**
     * Removes the given note from the DB.
     */
    @Query("DELETE FROM video_notes WHERE uid = :noteId")
    suspend fun removeNote(noteId: Uuid)

    /**
     * Removes the given notes from the DB.
     */
    @Query("DELETE FROM video_notes WHERE uid IN (:noteIds)")
    suspend fun removeNote(noteIds: List<Uuid>)

    @Query("UPDATE video_notes SET syncVersion = :syncVersion, lastSynced = :lastSynced WHERE uid = :noteId")
    suspend fun updateSyncMetadata(noteId: Uuid, syncVersion: Long, lastSynced: kotlinx.datetime.Instant)
}
