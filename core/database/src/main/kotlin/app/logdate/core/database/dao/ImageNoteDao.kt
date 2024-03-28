package app.logdate.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.logdate.core.database.model.ImageNoteEntity
import app.logdate.core.database.model.TextNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageNoteDao {

    /**
     * Retrieves an observable [ImageNoteEntity] by the given ID.
     */
    @Query("SELECT * FROM image_notes WHERE uid = :uid")
    fun getNote(uid: Int): Flow<ImageNoteEntity>

    /**
     * Fetches a non-observable [ImageNoteEntity] by its ID.
     */
    @Query("SELECT * FROM image_notes WHERE uid = :uid")
    fun getNoteOneOff(uid: Int): ImageNoteEntity

    /**
     * Returns a flow of all [TextNoteEntity]s.
     */
    @Query("SELECT * FROM image_notes")
    fun getAllNotes(): Flow<List<ImageNoteEntity>>

    /**
     * Inserts a [note] into the DB if it doesn't exist, and ignores it if it does.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addNote(note: ImageNoteEntity)

    /**
     * Removes the given note from the DB.
     */
    @Query("DELETE FROM text_notes WHERE uid = :noteId")
    suspend fun removeNote(noteId: Int)

    /**
     * Removes the given note from the DB.
     */
    @Query("DELETE FROM text_notes WHERE uid IN (:noteIds)")
    suspend fun removeNote(noteIds: List<Int>)
}