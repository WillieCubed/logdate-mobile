package app.logdate.client.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.logdate.client.database.entities.EventNoteLinkEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface EventNoteLinkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(link: EventNoteLinkEntity)

    @Query(
        """
        DELETE FROM event_note_links
        WHERE event_id = :eventId AND note_id = :noteId
        """,
    )
    suspend fun delete(
        eventId: Uuid,
        noteId: Uuid,
    )

    @Query(
        """
        SELECT note_id FROM event_note_links
        WHERE event_id = :eventId AND deleted_at IS NULL
        """,
    )
    fun getNotesForEvent(eventId: Uuid): Flow<List<Uuid>>

    @Query(
        """
        SELECT event_id FROM event_note_links
        WHERE note_id = :noteId AND deleted_at IS NULL
        """,
    )
    fun getEventsForNote(noteId: Uuid): Flow<List<Uuid>>
}
