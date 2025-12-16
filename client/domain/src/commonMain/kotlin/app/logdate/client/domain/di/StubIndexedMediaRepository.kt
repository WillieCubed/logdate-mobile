package app.logdate.client.domain.di

import app.logdate.client.repository.media.IndexedMedia
import app.logdate.client.repository.media.IndexedMediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.uuid.Uuid

/**
 * Stub implementation of [IndexedMediaRepository] for testing and development.
 * 
 * This implementation returns empty data and doesn't perform any real operations.
 * It's mainly used for dependency injection when the real implementation is not needed.
 */
class StubIndexedMediaRepository : IndexedMediaRepository {
    override suspend fun indexImage(uri: String, timestamp: Instant): IndexedMedia.Image {
        return IndexedMedia.Image(
            uid = Uuid.random(),
            uri = uri,
            timestamp = timestamp,
            caption = null
        )
    }

    override suspend fun indexVideo(uri: String, timestamp: Instant, duration: Duration): IndexedMedia.Video {
        return IndexedMedia.Video(
            uid = Uuid.random(),
            uri = uri,
            timestamp = timestamp,
            caption = null,
            duration = duration
        )
    }

    override suspend fun getByUid(uid: Uuid): IndexedMedia? = null

    override fun getForPeriod(startTime: Instant, endTime: Instant): Flow<List<IndexedMedia>> = emptyFlow()

    override suspend fun isIndexed(uri: String): Boolean = false

    override suspend fun updateCaption(uid: Uuid, caption: String?): IndexedMedia? = null

    override suspend fun remove(uid: Uuid): Boolean = false
}