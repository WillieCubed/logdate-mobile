package app.logdate.client.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.logdate.client.database.entities.ImageNoteEntity
import app.logdate.client.database.entities.TextNoteEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface ImageNoteDao {

    /**
     * Retrieves an observable [ImageNoteEntity] by the given ID.
     */
    @Query("SELECT * FROM image_notes WHERE uid = :uid")
    fun getNote(uid: Uuid): Flow<ImageNoteEntity>

    /**
     * Fetches a non-observable [ImageNoteEntity] by its ID.
     */
    @Query("SELECT * FROM image_notes WHERE uid = :uid")
    suspend fun getNoteOneOff(uid: Uuid): ImageNoteEntity

    /**
     * Returns a flow of all [ImageNoteEntity]s.
     */
    @Query("SELECT * FROM image_notes ORDER BY created DESC")
    fun getAllNotes(): Flow<List<ImageNoteEntity>>

    /**
     * Returns all image notes as a list (for quota calculation).
     */
    @Query("SELECT * FROM image_notes")
    suspend fun getAll(): List<ImageNoteEntity>


    /**
     * Fast query for recent notes to enable immediate timeline display.
     */
    @Query("SELECT * FROM image_notes ORDER BY created DESC LIMIT :limit")
    fun getRecentNotes(limit: Int = 20): Flow<List<ImageNoteEntity>>

    /**
     * Returns a paginated flow of [ImageNoteEntity]s ordered by creation date.
     */
    @Query("SELECT * FROM image_notes ORDER BY created DESC LIMIT :limit OFFSET :offset")
    fun getNotesPage(limit: Int, offset: Int): Flow<List<ImageNoteEntity>>

    /**
     * Returns notes within a specific date range.
     */
    @Query("SELECT * FROM image_notes WHERE created BETWEEN :startTimestamp AND :endTimestamp ORDER BY created DESC")
    fun getNotesInRange(startTimestamp: Long, endTimestamp: Long): Flow<List<ImageNoteEntity>>

    /**
     * Inserts a [note] into the DB if it doesn't exist, and ignores it if it does.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addNote(note: ImageNoteEntity)

    /**
     * Removes the given note from the DB.
     */
    @Query("DELETE FROM image_notes WHERE uid = :noteId")
    suspend fun removeNote(noteId: Uuid)

    /**
     * Removes the given note from the DB.
     */
    @Query("DELETE FROM image_notes WHERE uid IN (:noteIds)")
    suspend fun removeNote(noteIds: List<Uuid>)
}