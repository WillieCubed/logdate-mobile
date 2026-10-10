package app.logdate.wear.sync.v2

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.peer.state.LocalCapabilities
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.client.sync.peer.state.StateDocumentChannel
import app.logdate.client.sync.peer.state.StateMerger
import app.logdate.client.sync.peer.state.WatchState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Makes the watch's Labs switch the same as the phone's, and publishes the watch's own state.
 *
 * The switch is phone-owned: only a document the phone wrote, read through [StateMerger], can change
 * it, and the watch never writes it back. While the switch is on the watch advertises
 * [WearProtocol.WATCH_CAPABILITY_V2].
 */
class WatchStateMirror(
    private val featureFlags: FeatureFlagStore,
    private val channel: StateDocumentChannel,
    private val capabilities: LocalCapabilities,
    private val peerStates: PeerStateStore,
    private val merger: StateMerger,
    private val clock: Clock = Clock.System,
) {
    fun start(scope: CoroutineScope): Job =
        scope.launch {
            catchUpWithPhone()
            featureFlags.observe(FeatureFlag.WATCH_EXPERIENCE_V2).distinctUntilChanged().collect { enabled -> publish(enabled) }
        }

    /** Reads the phone's document, for a change made while this watch's app was not running. */
    suspend fun catchUpWithPhone() {
        channel.peerDocuments().forEach { peer -> onPhoneDocument(peer.nodeId, localNodeId = null, decoded = peer.decoded) }
    }

    suspend fun onPhoneDocument(
        sourceNodeId: String,
        localNodeId: String?,
        decoded: Decoded<StateDocument>,
    ) {
        if (sourceNodeId == localNodeId) return
        val document = peerStates.accept(sourceNodeId, decoded, PeerRole.PHONE) ?: return
        val phoneState = merger.merge(phone = document, watch = null).phone
        val enabled = phoneState?.flags?.get(FeatureFlag.WATCH_EXPERIENCE_V2.key) ?: return
        if (featureFlags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2) == enabled) return
        featureFlags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, enabled)
        Napier.i("The redesigned watch app is now ${if (enabled) "on" else "off"}, as set in Labs on the phone")
    }

    private suspend fun publish(enabled: Boolean) {
        val document = StateDocument(role = PeerRole.WATCH, writtenAt = clock.now().toEpochMilliseconds(), watch = WatchState())
        if (!channel.publish(document)) {
            Napier.e("Could not publish this watch's state; the phone sees the old one until the next change or restart")
        }
        val capabilityUpdated =
            if (enabled) {
                capabilities.add(WearProtocol.WATCH_CAPABILITY_V2)
            } else {
                capabilities.remove(WearProtocol.WATCH_CAPABILITY_V2)
            }
        if (!capabilityUpdated) {
            Napier.e(
                "Could not update ${WearProtocol.WATCH_CAPABILITY_V2}; the phone uses the old protocol until the next change or restart",
            )
        }
    }
}
