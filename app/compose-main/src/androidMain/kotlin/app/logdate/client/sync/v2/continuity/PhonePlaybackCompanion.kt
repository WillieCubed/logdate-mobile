package app.logdate.client.sync.v2.continuity

import app.logdate.client.media.audio.AndroidAudioPlaybackManager
import app.logdate.client.sync.continuity.HandoffFailure
import app.logdate.client.sync.continuity.PlaybackHandoffResult
import app.logdate.client.sync.continuity.WatchPlaybackCompanion
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LivePlaybackCommand
import app.logdate.client.sync.datalayer.v2.PlaybackCommandType
import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.client.sync.v2.receipts.PhoneDeliveryPeers
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.uuid.Uuid

class PhonePlaybackCompanion(
    private val peers: PhoneDeliveryPeers,
    private val capabilities: CapabilityClient,
    private val messages: MessageClient,
    private val player: AndroidAudioPlaybackManager,
    private val peerStates: PeerStateStore,
    private val publishNote: suspend (String, Uuid) -> Boolean,
    private val openMoment: suspend (String, Uuid) -> Boolean,
) : WatchPlaybackCompanion {
    override var lastFailure: HandoffFailure? = null
        private set

    private suspend fun reachable(): app.logdate.client.sync.datalayer.v2.PeerDeliveryContext? {
        val documents = peerStates.documents(PeerRole.WATCH).first()
        return capabilities
            .getCapability(WearProtocol.WATCH_CAPABILITY_V2, CapabilityClient.FILTER_REACHABLE)
            .await()
            .nodes
            .filter { WearFeatures.LIVE in documents[it.id]?.watch?.features.orEmpty() }
            .firstNotNullOfOrNull { peers.current(it.id) }
    }

    override suspend fun available(): Boolean =
        try {
            player.playbackStatus.value.let { it.isPlaying && it.metadata?.noteId != null } && reachable() != null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Couldn't find a reachable watch for playback", e)
            false
        }

    override suspend fun continueOnWatch(): Boolean {
        lastFailure = null
        var failureStage = HandoffFailure.WATCH_DID_NOT_RESPOND
        return try {
            val context = reachable() ?: return fail(HandoffFailure.NOT_ALLOWED)
            val initial = player.playbackStatus.value
            val id = initial.metadata?.noteId ?: return fail(HandoffFailure.PHONE_PLAYBACK_CHANGED)
            if (!initial.isPlaying) return fail(HandoffFailure.PHONE_PLAYBACK_CHANGED)
            if (!publishNote(context.watchNode, id)) {
                lastFailure = HandoffFailure.AUDIO_NOT_ON_WATCH
                return false
            }
            val session = Uuid.random().toString()
            val prepare =
                LivePlaybackCommand(
                    sessionId = session,
                    noteId = id.toString(),
                    command = PlaybackCommandType.PREPARE_ON_WATCH,
                )
            val prepared = request(context.watchNode, prepare, 20_000)
            if (prepared?.accepted != true || !peers.isCurrent(context)) {
                return fail(prepared?.failure ?: HandoffFailure.WATCH_DID_NOT_RESPOND)
            }
            failureStage = HandoffFailure.WATCH_DID_NOT_OPEN
            if (!openMoment(context.watchNode, id)) return fail(HandoffFailure.WATCH_DID_NOT_OPEN)
            if (!peers.isCurrent(context)) return fail(HandoffFailure.NOT_ALLOWED)
            val status = player.playbackStatus.value
            if (status.metadata?.noteId != id || !status.isPlaying) return fail(HandoffFailure.PHONE_PLAYBACK_CHANGED)
            failureStage = HandoffFailure.WATCH_DID_NOT_RESPOND
            val result =
                request(
                    context.watchNode,
                    prepare.copy(
                        command = PlaybackCommandType.CONTINUE_ON_WATCH,
                        positionMs = status.positionMs,
                        speed = status.playbackSpeed,
                    ),
                    30_000,
                )
            if (result?.accepted != true || !peers.isCurrent(context)) {
                return fail(result?.failure ?: HandoffFailure.WATCH_DID_NOT_RESPOND)
            }
            withContext(Dispatchers.Main.immediate) {
                if (player.playbackStatus.value.metadata
                        ?.noteId == id
                ) {
                    player.pausePlayback()
                }
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Couldn't continue playback on the watch", e)
            fail(failureStage)
        }
    }

    private fun fail(failure: HandoffFailure): Boolean {
        lastFailure = failure
        return false
    }

    private suspend fun request(
        node: String,
        command: LivePlaybackCommand,
        timeoutMs: Long,
    ): PlaybackHandoffResult? {
        val bytes =
            withTimeoutOrNull(timeoutMs) {
                messages
                    .sendRequest(
                        node,
                        WearPaths.LIVE_PLAYBACK_COMMAND,
                        WearJson.encodeToBytes(LivePlaybackCommand.serializer(), command),
                    ).await()
            } ?: return null
        return (WearJson.decode(bytes, WearProtocol.LIVE_SCHEMA, PlaybackHandoffResult.serializer()) as? Decoded.Supported)?.value
    }
}
