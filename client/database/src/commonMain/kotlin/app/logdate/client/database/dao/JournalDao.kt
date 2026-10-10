package app.logdate.client.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import app.logdate.client.database.entities.JournalCoverUri
import app.logdate.client.database.entities.JournalEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * Data access object for journals.
 *
 * All journal IDs are now represented as UUID strings.
 */
@Dao
interface JournalDao {
    @Query("SELECT * FROM journals WHERE id = :id")
    fun observeJournalById(id: Uuid): Flow<JournalEntity>

    @Query("SELECT * FROM journals WHERE id = :id")
    suspend fun getJournalById(id: Uuid): JournalEntity?

    @Query("SELECT * FROM journals")
    fun observeAll(): Flow<List<JournalEntity>>

    @Query("SELECT * FROM journals")
    suspend fun getAll(): List<JournalEntity>

    /**
     * Creates a new journal.
     */
    @Insert
    suspend fun create(journal: JournalEntity)

    @Upsert
    suspend fun update(journal: JournalEntity)

    @Query("UPDATE journals SET syncVersion = :syncVersion, lastSynced = :lastSynced WHERE id = :journalId")
    suspend fun updateSyncMetadata(
        journalId: Uuid,
        syncVersion: Long,
        lastSynced: kotlin.time.Instant,
    )

    /** Journals whose cover image reference is a local file path or `file:` URI, so a migration can rewrite them. */
    @Query(
        "SELECT id, coverImageUri FROM journals WHERE coverImageUri LIKE 'file:%' OR coverImageUri LIKE '/%' OR coverImageUri LIKE '_:%'",
    )
    suspend fun localFileCovers(): List<JournalCoverUri>

    /** Rewrites [previous] to [coverImageUri] only while the journal still stores [previous]. */
    @Query("UPDATE journals SET coverImageUri = :coverImageUri WHERE id = :journalId AND coverImageUri = :previous")
    suspend fun updateCoverImageUriIfUnchanged(
        journalId: Uuid,
        previous: String,
        coverImageUri: String,
    )

    @Query("DELETE FROM journals WHERE id = :journalId")
    suspend fun delete(journalId: Uuid)
}
