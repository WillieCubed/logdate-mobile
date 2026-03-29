package app.logdate.client.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.logdate.client.database.entities.StickerEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface StickerDao {
    /**
     * Returns all stickers ordered by most recently created.
     */
    @Query("SELECT * FROM stickers ORDER BY created_at DESC")
    fun getAllStickers(): Flow<List<StickerEntity>>

    /**
     * Fetches a single sticker by ID.
     */
    @Query("SELECT * FROM stickers WHERE id = :id")
    suspend fun getSticker(id: Uuid): StickerEntity?

    /**
     * Returns stickers extracted from a specific moment.
     */
    @Query("SELECT * FROM stickers WHERE source_moment_ref = :momentRef ORDER BY created_at DESC")
    fun getStickersForMoment(momentRef: Uuid): Flow<List<StickerEntity>>

    /**
     * Inserts a new sticker. Ignores if a sticker with the same ID already exists.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sticker: StickerEntity)

    /**
     * Deletes a sticker by ID.
     */
    @Query("DELETE FROM stickers WHERE id = :id")
    suspend fun delete(id: Uuid)
}
