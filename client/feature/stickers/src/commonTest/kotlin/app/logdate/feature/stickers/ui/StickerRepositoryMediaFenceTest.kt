package app.logdate.feature.stickers.ui

import app.logdate.client.database.dao.StickerDao
import app.logdate.client.database.entities.StickerEntity
import app.logdate.client.media.storage.MediaClaimFence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class StickerRepositoryMediaFenceTest {
    @Test
    fun `sticker media owner writes execute inside the media claim fence`() =
        runTest {
            val fence = RecordingFence()
            var inserted: StickerEntity? = null
            val dao =
                object : StickerDao {
                    override fun getAllStickers(): Flow<List<StickerEntity>> = emptyFlow()

                    override suspend fun getSticker(id: Uuid): StickerEntity? = inserted?.takeIf { it.id == id }

                    override fun getStickersForMoment(momentRef: Uuid): Flow<List<StickerEntity>> = emptyFlow()

                    override suspend fun insert(sticker: StickerEntity) {
                        inserted = sticker
                    }

                    override suspend fun delete(id: Uuid) {
                        if (inserted?.id == id) inserted = null
                    }
                }
            val repository = StickerRepository(dao, fence)

            repository.saveSticker(
                sourcePhotoUri = "logdate-media://library/objects/source.jpg",
                sourceMomentRef = null,
                imageUri = "logdate-media://library/objects/sticker.png",
            )

            assertEquals(1, fence.claims)
            assertEquals("logdate-media://library/objects/source.jpg", inserted?.sourcePhotoUri)
            assertEquals("logdate-media://library/objects/sticker.png", inserted?.imageUri)
        }
}

private class RecordingFence : MediaClaimFence {
    var claims = 0
        private set

    override suspend fun <T> withClaim(block: suspend () -> T): T {
        claims++
        return block()
    }

    override suspend fun <T> withExclusiveReclamation(block: suspend () -> T): T = error("The test fence does not support reclamation")
}
