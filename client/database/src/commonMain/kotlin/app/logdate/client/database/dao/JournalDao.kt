package app.logdate.client.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import app.logdate.client.database.entities.JournalEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for journals.
 */
@Dao
interface JournalDao {

    @Query("SELECT * FROM journals WHERE id = :id")
    fun observeJournalById(id: String): Flow<JournalEntity>

    @Query("SELECT * FROM journals")
    fun observeAll(): Flow<List<JournalEntity>>

    @Query("SELECT * FROM journals")
    suspend fun getAll(): List<JournalEntity>

    /**
     * Creates a new journal.
     *
     * @return The row ID of the created journal.
     */
    @Insert
    suspend fun create(journal: JournalEntity): Long

    @Upsert
    suspend fun update(journal: JournalEntity)

    @Query("DELETE FROM journals WHERE id = :journalId")
    suspend fun delete(journalId: String)
}