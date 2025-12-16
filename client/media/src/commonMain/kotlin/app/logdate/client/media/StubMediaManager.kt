package app.logdate.client.media

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * A stub implementation of MediaManager for testing and development purposes.
 * 
 * This implementation returns empty or dummy data and doesn't perform any real operations.
 * It's mainly used for dependency injection when the real implementation is not needed
 * or during development when media access isn't required.
 */
class StubMediaManager : MediaManager {
    override suspend fun getMedia(uri: String): MediaObject {
        // Return a dummy video object
        return MediaObject.Video(
            name = "Sample Video",
            uri = uri,
            size = 0,
            timestamp = Instant.parse("2023-01-01T12:00:00Z"),
            duration = 30.seconds
        )
    }

    override suspend fun exists(mediaId: String): Boolean {
        return false
    }

    override suspend fun getRecentMedia(): Flow<List<MediaObject>> {
        return flowOf(emptyList())
    }

    override suspend fun queryMediaByDate(start: Instant, end: Instant): Flow<List<MediaObject>> {
        return flowOf(emptyList())
    }

    override suspend fun addToDefaultCollection(uri: String) {
        // No-op
    }
}