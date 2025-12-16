package app.logdate.client.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.logdate.client.database.entities.TextNoteEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface TextNoteDao {

    /**
     * Retrieves an observable [TextNoteEntity] by the given ID.
     */
    @Query("SELECT * FROM text_notes WHERE uid = :uid")
    fun getNote(uid: Uuid): Flow<TextNoteEntity>

    /**
     * Fetches a non-observable [TextNoteEntity] by its ID.
     */
    @Query("SELECT * FROM text_notes WHERE uid = :uid")
    suspend fun getNoteOneOff(uid: Uuid): TextNoteEntity

    /**
     * Returns a flow of all [TextNoteEntity]s optimized for timeline display.
     */
    @Query("SELECT * FROM text_notes ORDER BY created DESC")
    fun getAllNotes(): Flow<List<TextNoteEntity>>

    /**
     * Returns all text notes as a list (for quota calculation).
     */
    @Query("SELECT * FROM text_notes")
    suspend fun getAll(): List<TextNoteEntity>

    /**
     * Fast query for recent notes to enable immediate timeline display.
     */
    @Query("SELECT * FROM text_notes ORDER BY created DESC LIMIT :limit")
    fun getRecentNotes(limit: Int = 20): Flow<List<TextNoteEntity>>

    /**
     * Returns a paginated flow of [TextNoteEntity]s ordered by creation date.
     */
    @Query("SELECT * FROM text_notes ORDER BY created DESC LIMIT :limit OFFSET :offset")
    fun getNotesPage(limit: Int, offset: Int): Flow<List<TextNoteEntity>>

    /**
     * Returns notes within a specific date range.
     */
    @Query("SELECT * FROM text_notes WHERE created BETWEEN :startTimestamp AND :endTimestamp ORDER BY created DESC")
    fun getNotesInRange(startTimestamp: Long, endTimestamp: Long): Flow<List<TextNoteEntity>>

    /**
     * Inserts the [note] into the DB if it doesn't already exist and ignores it if it does.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addNote(note: TextNoteEntity)

    /**
     * Removes the given note from the DB.
     */
    @Query("DELETE FROM text_notes WHERE uid = :noteId")
    suspend fun removeNote(noteId: Uuid)

    /**
     * Removes the given notes from the DB.
     */
    @Query("DELETE FROM text_notes WHERE uid IN (:noteIds)")
    suspend fun removeNote(noteIds: List<Uuid>)
    
    /**
     * Fetches notes by their content, useful for finding newly created notes.
     */
    @Query("SELECT * FROM text_notes WHERE content = :content ORDER BY created DESC")
    suspend fun getTextNotesByContent(content: String): List<TextNoteEntity>
}