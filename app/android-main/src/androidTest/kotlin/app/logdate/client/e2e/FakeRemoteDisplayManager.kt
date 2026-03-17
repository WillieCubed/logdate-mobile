package app.logdate.client.e2e

import app.logdate.client.media.display.ExternalDisplay
import app.logdate.client.media.display.RemoteDisplayManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake [RemoteDisplayManager] for E2E tests.
 *
 * Tracks all method calls and allows simulating display connect/disconnect.
 */
class FakeRemoteDisplayManager : RemoteDisplayManager {
    private val displays = MutableStateFlow<List<ExternalDisplay>>(emptyList())
    private val presenting = MutableStateFlow(false)

    val presentCalls = mutableListOf<Triple<Int, String, String>>()
    val updateCalls = mutableListOf<Pair<String, String>>()
    var dismissCallCount = 0
        private set

    fun simulateDisplayConnected(name: String = "Test TV") {
        displays.value = listOf(ExternalDisplay(id = 1, name = name))
    }

    fun simulateDisplayDisconnected() {
        displays.value = emptyList()
    }

    override fun observeExternalDisplays(): Flow<List<ExternalDisplay>> = displays

    override fun present(
        displayId: Int,
        mediaUri: String,
        mimeType: String,
    ) {
        presentCalls += Triple(displayId, mediaUri, mimeType)
        presenting.value = true
    }

    override fun updatePresentation(
        mediaUri: String,
        mimeType: String,
    ) {
        updateCalls += Pair(mediaUri, mimeType)
    }

    override fun dismiss() {
        dismissCallCount++
        presenting.value = false
    }

    override fun observeIsPresenting(): Flow<Boolean> = presenting
}
