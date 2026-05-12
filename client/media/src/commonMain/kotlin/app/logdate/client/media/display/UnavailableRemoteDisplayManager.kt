package app.logdate.client.media.display

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** [RemoteDisplayManager] for platforms without external-display support. */
class UnavailableRemoteDisplayManager : RemoteDisplayManager {
    override fun observeExternalDisplays(): Flow<List<ExternalDisplay>> = flowOf(emptyList())

    override fun present(
        displayId: Int,
        mediaUri: String,
        mimeType: String,
    ) {
    }

    override fun updatePresentation(
        mediaUri: String,
        mimeType: String,
    ) {
    }

    override fun dismiss() {
    }

    override fun observeIsPresenting(): Flow<Boolean> = flowOf(false)
}
