package app.logdate.client.media

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

class DesktopMediaManager : MediaManager {
    override suspend fun getMedia(uri: String): MediaObject {
        TODO("Not yet implemented")
    }

    override suspend fun exists(mediaId: String): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun getRecentMedia(): Flow<List<MediaObject>> {
        TODO("Not yet implemented")
    }

    override suspend fun queryMediaByDate(start: Instant, end: Instant): Flow<List<MediaObject>> {
        TODO("Not yet implemented")
    }

    override suspend fun addToDefaultCollection(uri: String) {
        TODO("Not yet implemented")
    }

}