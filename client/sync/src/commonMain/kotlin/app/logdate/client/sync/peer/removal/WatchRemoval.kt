package app.logdate.client.sync.peer.removal

import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import kotlinx.serialization.Serializable

object WatchRemovalProtocol {
    const val PATH = "/logdate/v2/device/removal"
}

@Serializable
enum class RemovalAction { DRAIN, WIPE, REVOKED_WIPE }

@Serializable
data class WatchRemovalRequest(
    val schema: Int = 1,
    val context: PeerDeliveryContext,
    val action: RemovalAction,
)

@Serializable
data class WatchRemovalReply(
    val schema: Int = 1,
    val context: PeerDeliveryContext,
    val action: RemovalAction,
    val pending: Int,
    val completed: Boolean,
)

sealed interface WatchRemovalResult {
    data object Removed : WatchRemovalResult

    data object RemovedWillWipeOnNextConnect : WatchRemovalResult

    data class Failed(
        val step: String,
    ) : WatchRemovalResult
}

/** The reviewed target is immutable throughout removal; no selected-device fallback is allowed. */
data class WatchRemovalTarget(
    val context: PeerDeliveryContext,
    val displayName: String,
    val pending: Int?,
    val sessionId: String? = null,
)

interface WatchRemovalAccess {
    suspend fun target(): WatchRemovalTarget?

    suspend fun remove(target: WatchRemovalTarget): WatchRemovalResult

    suspend fun removeAll(): Boolean = false
}
