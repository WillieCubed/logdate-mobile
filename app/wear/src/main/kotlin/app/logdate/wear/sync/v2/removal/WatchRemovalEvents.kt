package app.logdate.wear.sync.v2.removal

import app.logdate.client.sync.peer.removal.WatchRemovalProtocol

class WatchRemovalEvents(
    private val endpoint: WatchRemovalEndpoint,
    private val lock: WatchRemovalLock,
) {
    suspend fun consume(
        node: String,
        path: String,
        data: Map<String, String>,
    ): Boolean {
        if (path.startsWith(WatchRemovalProtocol.PATH)) {
            data["request"]?.let { endpoint.answer(node, it.encodeToByteArray()) }
            return true
        }
        return lock.removed()
    }
}
