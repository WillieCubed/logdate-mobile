package app.logdate.wear.sync.v2

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.peer.state.LocalCapabilities
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.client.sync.peer.state.Stamped
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.client.sync.peer.state.StateDocumentChannel
import app.logdate.client.sync.peer.state.StateMerger
import app.logdate.client.sync.peer.state.WatchState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
    private val ownState: Flow<WatchState> = flowOf(WatchState()),
    private val clock: Clock = Clock.System,
    private val sharedPreferences: Flow<Map<String, Stamped>> = flowOf(emptyMap()),
    private val initializeShared: suspend () -> Unit = {},
    private val adoptPhonePreferences: suspend (String, StateDocument) -> Unit = { _, _ -> },
    private val lastReminderAt: Flow<Long?> = flowOf(null),
    private val acceptsPhone: suspend () -> Boolean = { true },
) {
    fun start(scope: CoroutineScope): Job =
        scope.launch {
            initializeShared()
            catchUpWithPhone()
            var advertised: Boolean? = null
            combine(featureFlags.observe(FeatureFlag.WATCH_EXPERIENCE_V2), ownState, sharedPreferences, lastReminderAt) {
                enabled,
                state,
                shared,
                posted,
                ->
                Publication(enabled, state, shared, posted)
            }.distinctUntilChanged()
                .collect { publication ->
                    peerStates.documents(PeerRole.PHONE).first().forEach { (node, document) -> adoptPhonePreferences(node, document) }
                    publish(publication)
                    if (publication.enabled != advertised) {
                        if (updateCapability(publication.enabled)) advertised = publication.enabled
                    }
                }
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
        if (sourceNodeId == localNodeId || !acceptsPhone()) return
        val document = peerStates.accept(sourceNodeId, decoded, PeerRole.PHONE) ?: return
        val phoneState = merger.merge(phone = document, watch = null).phone
        val enabled = phoneState?.flags?.get(FeatureFlag.WATCH_EXPERIENCE_V2.key) ?: return
        if (featureFlags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2) != enabled) {
            featureFlags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, enabled)
            Napier.i("The redesigned watch app is now ${if (enabled) "on" else "off"}, as set in Labs on the phone")
        }
        adoptPhonePreferences(sourceNodeId, document)
    }

    private suspend fun publish(publication: Publication) {
        val document =
            StateDocument(
                role = PeerRole.WATCH,
                writtenAt = clock.now().toEpochMilliseconds(),
                watch = publication.watch,
                shared = publication.shared,
                lastReminderAt = publication.posted,
            )
        if (!channel.publish(document)) {
            Napier.e("Could not publish this watch's state; the phone sees the old one until the next change or restart")
        }
    }

    private suspend fun updateCapability(enabled: Boolean): Boolean {
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
        return capabilityUpdated
    }

    private data class Publication(
        val enabled: Boolean,
        val watch: WatchState,
        val shared: Map<String, Stamped>,
        val posted: Long?,
    )
}
