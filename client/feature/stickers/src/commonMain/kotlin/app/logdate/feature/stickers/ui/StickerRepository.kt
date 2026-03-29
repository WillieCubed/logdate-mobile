package app.logdate.feature.stickers.ui

import app.logdate.client.database.dao.StickerDao
import app.logdate.client.database.entities.StickerEntity
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Manages the user's personal sticker library.
 */
class StickerRepository(
    private val stickerDao: StickerDao,
) {
    fun getAllStickers(): Flow<List<StickerEntity>> = stickerDao.getAllStickers()

    fun getStickersForMoment(momentRef: Uuid): Flow<List<StickerEntity>> = stickerDao.getStickersForMoment(momentRef)

    suspend fun getSticker(id: Uuid): StickerEntity? = stickerDao.getSticker(id)

    suspend fun saveSticker(
        sourcePhotoUri: String,
        sourceMomentRef: Uuid?,
        imageUri: String,
        label: String? = null,
    ): Uuid {
        val id = Uuid.random()
        stickerDao.insert(
            StickerEntity(
                id = id,
                sourcePhotoUri = sourcePhotoUri,
                sourceMomentRef = sourceMomentRef,
                imageUri = imageUri,
                createdAt = Clock.System.now(),
                label = label,
            ),
        )
        return id
    }

    suspend fun deleteSticker(id: Uuid) {
        stickerDao.delete(id)
    }
}
