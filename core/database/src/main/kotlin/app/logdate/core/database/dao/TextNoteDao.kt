package app.logdate.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.logdate.core.database.model.TextNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TextNoteDao {

    /**
     * Retrieves an observable [TextNoteEntity] by the given ID.
     */
    @Query("SELECT * FROM text_notes WHERE uid = :uid")
    fun getNote(uid: Int): Flow<TextNoteEntity>

    /**
     * Fetches a non-observable [TextNoteEntity] by its ID.
     */
    @Query("SELECT * FROM text_notes WHERE uid = :uid")
    fun getNoteOneOff(uid: Int): TextNoteEntity

    /**
     * Returns a flow of all [TextNoteEntity]s.
     */
    @Query("SELECT * FROM text_notes")
    fun getAllNotes(): Flow<List<TextNoteEntity>>

    /**
     * Inserts the [note] into the DB if it doesn't already exist and ignores it if it does.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addNote(note: TextNoteEntity)

    /**
     * Removes the given note from the DB.
     */
    @Query("DELETE FROM text_notes WHERE uid = :noteId")
    suspend fun removeNote(noteId: Int)

    /**
     * Removes the given notes from the DB.
     */
    @Query("DELETE FROM text_notes WHERE uid IN (:noteIds)")
    suspend fun removeNote(noteIds: List<Int>)
}