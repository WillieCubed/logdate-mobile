package app.logdate.feature.library.fakes

import app.logdate.client.repository.media.ExifMetadata
import app.logdate.client.repository.media.IndexedMedia
import app.logdate.client.repository.media.IndexedMediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Fake implementation of [IndexedMediaRepository] for testing.
 */
class FakeIndexedMediaRepository(
    initialMedia: List<IndexedMedia> = emptyList(),
) : IndexedMediaRepository {
    private val mediaFlow = MutableStateFlow(initialMedia)
    private val exifMetadataByUid = mutableMapOf<Uuid, ExifMetadata>()

    fun setMedia(media: List<IndexedMedia>) {
        mediaFlow.value = media
    }

    fun setExifMetadata(
        uid: Uuid,
        metadata: ExifMetadata,
    ) {
        exifMetadataByUid[uid] = metadata
    }

    override suspend fun indexImage(
        uri: String,
        timestamp: Instant,
    ): IndexedMedia.Image {
        val image = IndexedMedia.Image(Uuid.random(), uri, timestamp)
        mediaFlow.value = mediaFlow.value + image
        return image
    }

    override suspend fun indexVideo(
        uri: String,
        timestamp: Instant,
        duration: Duration,
    ): IndexedMedia.Video {
        val video = IndexedMedia.Video(Uuid.random(), uri, timestamp, duration = duration)
        mediaFlow.value = mediaFlow.value + video
        return video
    }

    override suspend fun getByUid(uid: Uuid): IndexedMedia? = mediaFlow.value.find { it.uid == uid }

    override fun getForPeriod(
        startTime: Instant,
        endTime: Instant,
    ): Flow<List<IndexedMedia>> =
        mediaFlow.map { media ->
            media.filter { it.timestamp in startTime..endTime }
        }

    override suspend fun isIndexed(uri: String): Boolean = mediaFlow.value.any { it.uri == uri }

    override suspend fun remove(uid: Uuid): Boolean {
        val before = mediaFlow.value
        mediaFlow.value = before.filter { it.uid != uid }
        return mediaFlow.value.size < before.size
    }

    override suspend fun updateCaption(
        uid: Uuid,
        caption: String?,
    ): IndexedMedia? = null

    override fun observeAllMedia(): Flow<List<IndexedMedia>> = mediaFlow.map { it.sortedByDescending { item -> item.timestamp } }

    override fun getMediaCount(): Flow<Int> = mediaFlow.map { it.size }

    override suspend fun getExifMetadata(uid: Uuid): ExifMetadata? = exifMetadataByUid[uid]
}
