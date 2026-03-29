package app.logdate.client.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.logdate.client.database.entities.PostcardEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface PostcardDao {
    /**
     * Returns an observable Postcard by ID.
     */
    @Query("SELECT * FROM postcards WHERE id = :id")
    fun getPostcard(id: Uuid): Flow<PostcardEntity?>

    /**
     * Fetches a Postcard by ID as a one-shot query.
     */
    @Query("SELECT * FROM postcards WHERE id = :id")
    suspend fun getPostcardOneShot(id: Uuid): PostcardEntity?

    /**
     * Returns all Postcards ordered by most recently modified.
     */
    @Query("SELECT * FROM postcards ORDER BY modified_at DESC")
    fun getAllPostcards(): Flow<List<PostcardEntity>>

    /**
     * Returns all Postcards linked to a specific source moment.
     */
    @Query("SELECT * FROM postcards WHERE source_moment_ref = :momentRef ORDER BY modified_at DESC")
    fun getPostcardsForMoment(momentRef: Uuid): Flow<List<PostcardEntity>>

    /**
     * Inserts a new Postcard. Ignores if a Postcard with the same ID already exists.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(postcard: PostcardEntity)

    /**
     * Updates an existing Postcard.
     */
    @Update
    suspend fun update(postcard: PostcardEntity)

    /**
     * Deletes a Postcard by ID.
     */
    @Query("DELETE FROM postcards WHERE id = :id")
    suspend fun delete(id: Uuid)
}
