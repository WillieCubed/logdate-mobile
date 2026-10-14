package app.logdate.wear.v2.continuity

import app.logdate.client.sync.continuity.HandoffFailure
import app.logdate.client.sync.continuity.PlaybackHandoffResult
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LivePlaybackCommand
import app.logdate.client.sync.datalayer.v2.PlaybackCommandType
import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.datalayer.v2.WearProtocolGate
import app.logdate.wear.sync.v2.delivery.WatchDeliveryPeer
import app.logdate.wear.v2.phone.HandoffResult
import app.logdate.wear.v2.phone.PhoneHandoff
import app.logdate.wear.v2.playback.MomentPlayer
import app.logdate.wear.v2.playback.PlaybackState
import com.google.android.gms.wearable.MessageClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.uuid.Uuid

class WatchPlaybackHandoff(
    private val peer: WatchDeliveryPeer,
    private val messages: MessageClient,
    private val player: MomentPlayer,
    private val phoneHandoff: PhoneHandoff,
    private val gate: WearProtocolGate,
) {
    suspend fun openOnPhone(
        id: Uuid,
        target: String = "edit",
    ): Boolean {
        val context = peer.current() ?: return false
        return phoneHandoff.open("studio.hypertext.logdate://handoff/$id?target=$target", context.phoneNode) == HandoffResult.SENT &&
            peer.isCurrent(context)
    }

    suspend fun available(): Boolean = peer.current() != null && gate.isFeatureActive(WearFeatures.PHONE_PLAYBACK).first()

    suspend fun playOnPhone(id: Uuid): HandoffFailure? {
        if (!available()) return HandoffFailure.PHONE_NEEDS_SETUP
        val context = peer.current() ?: return HandoffFailure.PHONE_NEEDS_SETUP
        val active = (player.state.value as? PlaybackState.Active)?.takeIf { it.noteId == id }
        val command =
            LivePlaybackCommand(
                sessionId = Uuid.random().toString(),
                noteId = id.toString(),
                command = PlaybackCommandType.CONTINUE_ON_PHONE,
                positionMs = active?.positionMs ?: 0,
                speed = player.speed.value.factor,
            )
        return try {
            val bytes =
                withTimeoutOrNull(12_000) {
                    messages
                        .sendRequest(
                            context.phoneNode,
                            WearPaths.LIVE_PLAYBACK_COMMAND,
                            WearJson.encodeToBytes(LivePlaybackCommand.serializer(), command),
                        ).await()
                } ?: return HandoffFailure.PLAYBACK_FAILED
            val result = WearJson.decode(bytes, WearProtocol.LIVE_SCHEMA, PlaybackHandoffResult.serializer())
            if (result !is Decoded.Supported || !result.value.accepted || !peer.isCurrent(context)) {
                (result as? Decoded.Supported)?.value?.failure ?: HandoffFailure.PLAYBACK_FAILED
            } else {
                player.pauseIfPlaying(id)
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Couldn't continue audio on the phone", e)
            HandoffFailure.PLAYBACK_FAILED
        }
    }
}
