package app.logdate.wear.v2.continuity

import app.logdate.client.sync.datalayer.v2.CommandFailure
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LiveCommandResult
import app.logdate.client.sync.datalayer.v2.LiveRecordingCommand
import app.logdate.client.sync.datalayer.v2.LiveRecordingState
import app.logdate.client.sync.datalayer.v2.RecordingCommandType
import app.logdate.client.sync.datalayer.v2.RecordingPhase
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.wear.sync.v2.delivery.WatchDeliveryPeer
import app.logdate.wear.v2.recorder.RecorderState
import com.google.android.gms.wearable.MessageClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Acknowledges Stop only after the recorder has finalized and saved the capture. */
class WatchRecordingContinuity(
    private val peer: WatchDeliveryPeer,
    private val messages: MessageClient,
) {
    private var sessionId: String? = null
    private var sequence = 0L
    private var stopRecording: (suspend () -> Boolean)? = null
    private var job: Job? = null

    fun clearForRemoval() {
        job?.cancel()
        job = null
        stopRecording = null
        sessionId = null
        sequence = 0
    }

    fun attach(
        scope: CoroutineScope,
        state: StateFlow<RecorderState>,
        stop: suspend () -> Boolean,
    ) {
        job?.cancel()
        stopRecording = stop
        job =
            scope.launch {
                launch {
                    state
                        .map { current ->
                            when (current) {
                                is RecorderState.Live -> if (current.paused) RecordingPhase.PAUSED else RecordingPhase.RECORDING
                                RecorderState.Saving -> null
                                is RecorderState.Saved -> RecordingPhase.SAVED
                                else -> RecordingPhase.DISCARDED
                            }
                        }.distinctUntilChanged()
                        .collect { publish(state.value) }
                }
                while (isActive) {
                    delay(10_000)
                    if (state.value is RecorderState.Live) publish(state.value)
                }
            }
    }

    suspend fun answer(
        node: String,
        bytes: ByteArray,
    ): ByteArray {
        val command = WearJson.decode(bytes, WearProtocol.LIVE_SCHEMA, LiveRecordingCommand.serializer())
        val context = peer.current()
        val failure =
            when {
                context == null || context.phoneNode != node -> CommandFailure.NOT_ALLOWED
                command !is Decoded.Supported -> CommandFailure.FAILED
                command.value.sessionId != sessionId -> CommandFailure.NO_SUCH_SESSION
                command.value.command != RecordingCommandType.STOP -> CommandFailure.NOT_ALLOWED
                else -> null
            }
        if (failure != null) return result(false, failure)
        val saved = stopRecording?.invoke() == true
        val accepted = saved && peer.isCurrent(context!!)
        return result(accepted, if (accepted) null else CommandFailure.FAILED)
    }

    private suspend fun publish(state: RecorderState) {
        val live = state as? RecorderState.Live
        if (live == null && state == RecorderState.Saving) return
        if (live != null && sessionId == null) {
            sessionId = Uuid.random().toString()
            sequence = 0
        }
        val id = sessionId ?: return
        val phase =
            if (live != null) {
                if (live.paused) RecordingPhase.PAUSED else RecordingPhase.RECORDING
            } else if (state is RecorderState.Saved) {
                RecordingPhase.SAVED
            } else {
                RecordingPhase.DISCARDED
            }
        val message =
            LiveRecordingState(
                sessionId = id,
                sequence = ++sequence,
                phase = phase,
                elapsedMs = live?.elapsed?.inWholeMilliseconds ?: 0,
                sentAt = Clock.System.now().toEpochMilliseconds(),
            )
        try {
            val context = peer.current()
            if (context !=
                null
            ) {
                messages
                    .sendMessage(
                        context.phoneNode,
                        WearPaths.LIVE_RECORDING,
                        WearJson.encodeToBytes(LiveRecordingState.serializer(), message),
                    ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Couldn't publish the live watch recording", e)
        }
        if (live == null) sessionId = null
    }

    private fun result(
        accepted: Boolean,
        failure: CommandFailure?,
    ) = WearJson.encodeToBytes(LiveCommandResult.serializer(), LiveCommandResult(accepted = accepted, failure = failure))
}
