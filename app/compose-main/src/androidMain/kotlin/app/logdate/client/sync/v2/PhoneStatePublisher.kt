package app.logdate.client.sync.v2

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.peer.state.LocalCapabilities
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.client.sync.peer.state.PhoneState
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.client.sync.peer.state.StateDocumentChannel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * Publishes this phone's state document whenever the Labs watch switch changes, and advertises
 * [WearProtocol.PHONE_CAPABILITY_V2] only while it is on.
 *
 * The document goes out whatever the switch says, because the watch takes the switch from it. It lists
 * no version 2 features yet: each one is added by the work that ships it.
 */
class PhoneStatePublisher(
    private val v2Enabled: Flow<Boolean>,
    private val channel: StateDocumentChannel,
    private val capabilities: LocalCapabilities,
    private val peerStates: PeerStateStore,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    fun start(scope: CoroutineScope): Job =
        scope.launch {
            catchUpWithWatches()
            v2Enabled.distinctUntilChanged().collect { enabled -> publish(enabled) }
        }

    /** Reads the documents the watches published while this phone's app was not running. */
    suspend fun catchUpWithWatches() {
        channel.peerDocuments().forEach { peer -> peerStates.accept(peer.nodeId, peer.decoded, PeerRole.WATCH) }
    }

    suspend fun publish(v2Enabled: Boolean) {
        val document =
            StateDocument(
                role = PeerRole.PHONE,
                writtenAt = clock.now().toEpochMilliseconds(),
                phone =
                    PhoneState(
                        flags = mapOf(FeatureFlag.WATCH_EXPERIENCE_V2.key to v2Enabled),
                        zone = timeZone().id,
                    ),
            )
        if (!channel.publish(document)) {
            Napier.e("Could not publish this phone's state; watches use the old Labs setting until the next change or restart")
        }
        val capabilityUpdated =
            if (v2Enabled) {
                capabilities.add(WearProtocol.PHONE_CAPABILITY_V2)
            } else {
                capabilities.remove(WearProtocol.PHONE_CAPABILITY_V2)
            }
        if (!capabilityUpdated) {
            Napier.e(
                "Could not update ${WearProtocol.PHONE_CAPABILITY_V2}; watches use the old protocol until the next change or restart",
            )
        }
    }
}
