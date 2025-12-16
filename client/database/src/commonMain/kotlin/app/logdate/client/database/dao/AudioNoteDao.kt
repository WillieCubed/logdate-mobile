package app.logdate.client.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.logdate.client.database.entities.VoiceNoteEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface AudioNoteDao {

    /**
     * Retrieves an observable [VoiceNoteEntity] by the given ID.
     */
    @Query("SELECT * FROM voice_notes WHERE uid = :uid")
    fun getNote(uid: Uuid): Flow<VoiceNoteEntity>

    /**
     * Fetches a non-observable [VoiceNoteEntity] by its ID.
     */
    @Query("SELECT * FROM voice_notes WHERE uid = :uid")
    suspend fun getNoteOneOff(uid: Uuid): VoiceNoteEntity

    /**
     * Returns a flow of all [VoiceNoteEntity]s.
     */
    @Query("SELECT * FROM voice_notes ORDER BY created DESC")
    fun getAllNotes(): Flow<List<VoiceNoteEntity>>

    /**
     * Returns all voice notes as a list (for quota calculation).
     */
    @Query("SELECT * FROM voice_notes")
    suspend fun getAll(): List<VoiceNoteEntity>


    /**
     * Fast query for recent notes to enable immediate timeline display.
     */
    @Query("SELECT * FROM voice_notes ORDER BY created DESC LIMIT :limit")
    fun getRecentNotes(limit: Int = 20): Flow<List<VoiceNoteEntity>>

    /**
     * Returns notes within a specific date range.
     */
    @Query("SELECT * FROM voice_notes WHERE created BETWEEN :startTimestamp AND :endTimestamp ORDER BY created DESC")
    fun getNotesInRange(startTimestamp: Long, endTimestamp: Long): Flow<List<VoiceNoteEntity>>

    /**
     * Inserts a [note] into the DB if it doesn't exist, and ignores it if it does.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addNote(note: VoiceNoteEntity)

    /**
     * Removes the given note from the DB.
     */
    @Query("DELETE FROM voice_notes WHERE uid = :noteId")
    suspend fun removeNote(noteId: Uuid)

    /**
     * Removes the given notes from the DB.
     */
    @Query("DELETE FROM voice_notes WHERE uid IN (:noteIds)")
    suspend fun removeNote(noteIds: List<Uuid>)
}