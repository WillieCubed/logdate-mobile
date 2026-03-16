package app.logdate.client.media.display

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * No-op [RemoteDisplayManager] for platforms that do not support external displays.
 */
class StubRemoteDisplayManager : RemoteDisplayManager {
    override fun observeExternalDisplays(): Flow<List<ExternalDisplay>> = flowOf(emptyList())

    override fun present(
        displayId: Int,
        mediaUri: String,
        mimeType: String,
    ) {
        // No-op
    }

    override fun updatePresentation(
        mediaUri: String,
        mimeType: String,
    ) {
        // No-op
    }

    override fun dismiss() {
        // No-op
    }

    override fun observeIsPresenting(): Flow<Boolean> = flowOf(false)
}
