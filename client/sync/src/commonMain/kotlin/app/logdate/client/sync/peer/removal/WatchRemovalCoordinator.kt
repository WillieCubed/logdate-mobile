package app.logdate.client.sync.peer.removal

import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WatchRemovalCoordinator(
    private val current: suspend (PeerDeliveryContext) -> Boolean,
    private val request: suspend (WatchRemovalRequest) -> WatchRemovalReply?,
    private val deferWipe: suspend (WatchRemovalRequest) -> Boolean,
    private val revoke: suspend (WatchRemovalTarget) -> Boolean,
    private val disassociate: suspend (WatchRemovalTarget) -> Boolean,
    private val forget: suspend (WatchRemovalTarget) -> Boolean,
) {
    private val mutex = Mutex()

    suspend fun remove(target: WatchRemovalTarget): WatchRemovalResult =
        mutex.withLock {
            if (!current(target.context)) return@withLock WatchRemovalResult.Failed("link changed")
            val drained = request(WatchRemovalRequest(context = target.context, action = RemovalAction.DRAIN))
            if (drained == null) {
                if (!current(target.context)) return@withLock WatchRemovalResult.Failed("link changed")
                if (!revoke(target)) return@withLock WatchRemovalResult.Failed("revoke")
                return@withLock deferAndForget(target)
            }
            if (!matches(drained, target, RemovalAction.DRAIN) || !drained.completed || drained.pending != 0) {
                return@withLock WatchRemovalResult.Failed("sync")
            }
            if (!current(target.context)) return@withLock WatchRemovalResult.Failed("link changed")
            if (!revoke(target)) return@withLock WatchRemovalResult.Failed("revoke")
            val wipe = WatchRemovalRequest(context = target.context, action = RemovalAction.WIPE)
            val erased = request(wipe)
            if (erased == null) return@withLock deferAndForget(target)
            if (!matches(erased, target, RemovalAction.WIPE)) {
                return@withLock WatchRemovalResult.Failed("wipe")
            }
            if (erased.pending != 0) {
                return@withLock WatchRemovalResult.Failed("sync")
            }
            if (!erased.completed) return@withLock WatchRemovalResult.Failed("wipe")
            if (!disassociate(target)) return@withLock WatchRemovalResult.Failed("disassociate")
            if (!forget(target)) return@withLock WatchRemovalResult.Failed("clear link")
            WatchRemovalResult.Removed
        }

    private suspend fun deferAndForget(target: WatchRemovalTarget): WatchRemovalResult {
        val deferred = WatchRemovalRequest(context = target.context, action = RemovalAction.REVOKED_WIPE)
        if (!deferWipe(deferred)) return WatchRemovalResult.Failed("queue wipe")
        if (!forget(target)) return WatchRemovalResult.Failed("clear link")
        return WatchRemovalResult.RemovedWillWipeOnNextConnect
    }

    private fun matches(
        reply: WatchRemovalReply,
        target: WatchRemovalTarget,
        action: RemovalAction,
    ): Boolean = reply.schema == 1 && reply.context == target.context && reply.action == action
}
