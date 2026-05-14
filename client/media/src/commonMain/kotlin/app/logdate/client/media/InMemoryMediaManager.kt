package app.logdate.client.media

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Deterministic in-memory [MediaManager] used by cross-module tests.
 */
class InMemoryMediaManager : MediaManager {
    override suspend fun getMedia(uri: String): MediaObject =
        MediaObject.Video(
            name = "In-memory video",
            uri = uri,
            size = 0,
            timestamp = Instant.parse("2023-01-01T12:00:00Z"),
            duration = 30.seconds,
        )

    override suspend fun exists(mediaId: String): Boolean = false

    override suspend fun getRecentMedia(limit: Int): Flow<List<MediaObject>> = flowOf(emptyList())

    override suspend fun queryMediaByDate(
        start: Instant,
        end: Instant,
    ): Flow<List<MediaObject>> = flowOf(emptyList())

    override suspend fun addToDefaultCollection(uri: String) {
    }

    override suspend fun readMedia(uri: String): MediaPayload =
        MediaPayload(
            fileName = "memory.bin",
            mimeType = "application/octet-stream",
            sizeBytes = 0,
            data = ByteArray(0),
        )

    override suspend fun saveMedia(payload: MediaPayload): String = "file:///tmp/${payload.fileName}"

    override suspend fun saveMediaFromFile(
        sourceFilePath: String,
        fileName: String,
        mimeType: String,
    ): String = "file:///tmp/$fileName"
}
