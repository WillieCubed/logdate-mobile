package app.logdate.client.data.media

import app.logdate.client.database.dao.media.IndexedMediaContent
import app.logdate.client.database.dao.media.IndexedMediaDao
import app.logdate.client.database.dao.media.MediaExifDao
import app.logdate.client.database.entities.media.IndexedImageEntity
import app.logdate.client.database.entities.media.IndexedVideoEntity
import app.logdate.client.database.entities.media.MediaDimensions
import app.logdate.client.database.entities.media.MediaExifMetadataEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Verifies that [OfflineIndexedMediaRepository] correctly aggregates and filters locally
 * indexed media from various DAO sources.
 *
 * The tests focus on the repository's ability to combine disparate media types (images and videos)
 * into a unified stream for a given time period, ensuring that updates to either source are
 * correctly reflected in the resulting flow.
 */
class OfflineIndexedMediaRepositoryTest {
    @Test
    fun `getForPeriod emits empty list initially`() =
        runTest {
            val dao = FakeIndexedMediaDao()
            val repository = OfflineIndexedMediaRepository(dao, FakeMediaExifDao())

            val media = repository.getForPeriod(PERIOD_START, PERIOD_END).first()

            assertEquals(emptyList(), media)
        }

    @Test
    fun `getForPeriod combines image and video updates`() =
        runTest {
            val dao = FakeIndexedMediaDao()
            val repository = OfflineIndexedMediaRepository(dao, FakeMediaExifDao())
            val flow = repository.getForPeriod(PERIOD_START, PERIOD_END)

            dao.images.value = listOf(imageEntity("image-1", PERIOD_START))
            dao.videos.value = listOf(videoEntity("video-1", PERIOD_START + 1.seconds))

            val media = flow.first { it.size == 2 }
            assertEquals(listOf("image-1", "video-1"), media.map { it.uri.substringAfterLast('/') })
        }

    private companion object {
        private val PERIOD_START = Instant.fromEpochMilliseconds(1_000)
        private val PERIOD_END = Instant.fromEpochMilliseconds(10_000)

        private fun imageEntity(
            suffix: String,
            timestamp: Instant,
        ) = IndexedImageEntity(
            uid = Uuid.random(),
            uri = "content://media/$suffix",
            timestamp = timestamp,
            indexedAt = Clock.System.now(),
            mimeType = "image/jpeg",
            fileSize = 10,
            dimensions = MediaDimensions(width = 100, height = 100),
            location = null,
        )

        private fun videoEntity(
            suffix: String,
            timestamp: Instant,
        ) = IndexedVideoEntity(
            uid = Uuid.random(),
            uri = "content://media/$suffix",
            timestamp = timestamp,
            indexedAt = Clock.System.now(),
            mimeType = "video/mp4",
            fileSize = 10,
            dimensions = MediaDimensions(width = 100, height = 100),
            location = null,
            duration = 5.seconds,
        )
    }
}

private class FakeIndexedMediaDao : IndexedMediaDao {
    val images = MutableStateFlow<List<IndexedImageEntity>>(emptyList())
    val videos = MutableStateFlow<List<IndexedVideoEntity>>(emptyList())

    override fun getImageById(uid: Uuid): Flow<IndexedImageEntity?> = MutableStateFlow(images.value.firstOrNull { it.uid == uid })

    override fun getVideoById(uid: Uuid): Flow<IndexedVideoEntity?> = MutableStateFlow(videos.value.firstOrNull { it.uid == uid })

    override suspend fun getImageByUri(uri: String): IndexedImageEntity? = images.value.firstOrNull { it.uri == uri }

    override suspend fun getVideoByUri(uri: String): IndexedVideoEntity? = videos.value.firstOrNull { it.uri == uri }

    override suspend fun isImageIndexed(uri: String): Boolean = images.value.any { it.uri == uri }

    override suspend fun isVideoIndexed(uri: String): Boolean = videos.value.any { it.uri == uri }

    override fun getImagesForPeriod(
        start: Instant,
        end: Instant,
    ): Flow<List<IndexedImageEntity>> = images

    override fun getVideosForPeriod(
        start: Instant,
        end: Instant,
    ): Flow<List<IndexedVideoEntity>> = videos

    override fun getAllIndexedImages(): Flow<List<IndexedImageEntity>> = images

    override fun getAllIndexedVideos(): Flow<List<IndexedVideoEntity>> = videos

    override suspend fun insertImage(image: IndexedImageEntity) {
        images.value = images.value + image
    }

    override suspend fun insertVideo(video: IndexedVideoEntity) {
        videos.value = videos.value + video
    }

    override suspend fun removeImage(uid: Uuid): Int {
        val before = images.value.size
        images.value = images.value.filterNot { it.uid == uid }
        return before - images.value.size
    }

    override suspend fun removeVideo(uid: Uuid): Int {
        val before = videos.value.size
        videos.value = videos.value.filterNot { it.uid == uid }
        return before - videos.value.size
    }

    override suspend fun getAllMediaForPeriod(
        start: Instant,
        end: Instant,
    ): IndexedMediaContent = IndexedMediaContent(images.value, videos.value)
}

private class FakeMediaExifDao : MediaExifDao {
    override suspend fun insert(entity: MediaExifMetadataEntity) {}

    override suspend fun getByMediaUid(mediaUid: Uuid): MediaExifMetadataEntity? = null

    override suspend fun deleteByMediaUid(mediaUid: Uuid) {}
}
