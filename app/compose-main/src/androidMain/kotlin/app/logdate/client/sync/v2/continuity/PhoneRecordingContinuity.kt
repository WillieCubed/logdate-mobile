package app.logdate.client.sync.v2.continuity

import android.os.SystemClock
import app.logdate.client.sync.continuity.WatchLiveSessionStore
import app.logdate.client.sync.continuity.WatchRecordingCompanion
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LiveCommandResult
import app.logdate.client.sync.datalayer.v2.LiveRecordingCommand
import app.logdate.client.sync.datalayer.v2.LiveRecordingState
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.RecordingCommandType
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.v2.receipts.PhoneDeliveryPeers
import com.google.android.gms.wearable.MessageClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class PhoneRecordingContinuity(
    private val peers: PhoneDeliveryPeers,
    private val messages: MessageClient,
    nowMs: () -> Long = SystemClock::elapsedRealtime,
) : WatchRecordingCompanion {
    private val store = WatchLiveSessionStore(nowMs)
    private val mutex = Mutex()
    private var acceptedAudience: PeerDeliveryContext? = null
    private val mutableStopping = MutableStateFlow(false)
    val stopping = mutableStopping.asStateFlow()

    suspend fun receive(
        node: String,
        bytes: ByteArray,
    ) = mutex.withLock {
        val audience = peers.current(node) ?: return@withLock
        val decoded = WearJson.decode(bytes, WearProtocol.LIVE_SCHEMA, LiveRecordingState.serializer())
        if (decoded !is Decoded.Supported || !peers.isCurrent(audience)) return@withLock
        if (acceptedAudience != audience) store.clear()
        acceptedAudience = audience
        store.receive(node, decoded.value)
    }

    override suspend fun current(): WatchLiveSessionStore.Session? = mutex.withLock { currentLocked() }

    private suspend fun currentLocked(): WatchLiveSessionStore.Session? {
        val value = store.current() ?: return null
        val audience = acceptedAudience
        if (audience == null || !peers.isCurrent(audience)) {
            store.clear()
            acceptedAudience = null
            return null
        }
        return value
    }

    override suspend fun stop(): Boolean {
        if (!mutableStopping.compareAndSet(false, true)) return false
        try {
            val (value, context) =
                mutex.withLock {
                    val current = currentLocked() ?: return false
                    current to (acceptedAudience ?: return false)
                }
            if (!peers.isCurrent(context)) return false
            val command = LiveRecordingCommand(sessionId = value.state.sessionId, command = RecordingCommandType.STOP)
            val response =
                withTimeoutOrNull(5_000) {
                    messages
                        .sendRequest(
                            value.nodeId,
                            WearPaths.LIVE_RECORDING_COMMAND,
                            WearJson.encodeToBytes(LiveRecordingCommand.serializer(), command),
                        ).await()
                } ?: return false
            val decoded = WearJson.decode(response, WearProtocol.LIVE_SCHEMA, LiveCommandResult.serializer())
            return decoded is Decoded.Supported && decoded.value.accepted && peers.isCurrent(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Couldn't stop the recording on the watch", e)
            return false
        } finally {
            mutableStopping.value = false
        }
    }
}
