package app.logdate.client.sync.datalayer.v2

import kotlinx.serialization.Serializable

@Serializable
enum class RecordingPhase {
    RECORDING,
    PAUSED,
    SAVED,
    DISCARDED,
}

/** Sent as a message on [WearPaths.LIVE_RECORDING] while the watch records. */
@Serializable
data class LiveRecordingState(
    val schema: Int = WearProtocol.LIVE_SCHEMA,
    val sessionId: String,
    val sequence: Long,
    val phase: RecordingPhase,
    val elapsedMs: Long,
    val sentAt: Long,
)

@Serializable
enum class RecordingCommandType {
    STOP,
    PAUSE,
    RESUME,
}

/** Sent with `MessageClient.sendRequest` to [WearPaths.LIVE_RECORDING_COMMAND]. */
@Serializable
data class LiveRecordingCommand(
    val schema: Int = WearProtocol.LIVE_SCHEMA,
    val sessionId: String,
    val command: RecordingCommandType,
)

@Serializable
enum class PlaybackPhase {
    PLAYING,
    PAUSED,
    ENDED,
}

/** Sent as a message on [WearPaths.LIVE_PLAYBACK]. */
@Serializable
data class LivePlaybackState(
    val schema: Int = WearProtocol.LIVE_SCHEMA,
    val sessionId: String,
    val noteId: String,
    val sequence: Long,
    val phase: PlaybackPhase,
    val positionMs: Long,
    val speed: Float = 1f,
    val sentAt: Long,
)

@Serializable
enum class PlaybackCommandType {
    PLAY,
    PAUSE,
    SEEK,
    SET_SPEED,
    CONTINUE_ON_PHONE,
    CONTINUE_ON_WATCH,
    PREPARE_ON_WATCH,
}

/** Sent with `MessageClient.sendRequest` to [WearPaths.LIVE_PLAYBACK_COMMAND]. */
@Serializable
data class LivePlaybackCommand(
    val schema: Int = WearProtocol.LIVE_SCHEMA,
    val sessionId: String,
    val noteId: String,
    val command: PlaybackCommandType,
    val positionMs: Long? = null,
    val speed: Float? = null,
)

@Serializable
enum class CommandFailure {
    NO_SUCH_SESSION,
    NOT_ALLOWED,
    FAILED,
}

/** The answer to a live command request. */
@Serializable
data class LiveCommandResult(
    val schema: Int = WearProtocol.LIVE_SCHEMA,
    val accepted: Boolean,
    val failure: CommandFailure? = null,
)

/**
 * Drops live state that arrives late. Messages are not ordered, so each session admits only a
 * sequence higher than the last it admitted. One gate belongs to one receiving coroutine.
 */
class LiveSequenceGate {
    private val latest = mutableMapOf<String, Long>()

    fun admit(
        sessionId: String,
        sequence: Long,
    ): Boolean {
        val previous = latest[sessionId]
        if (previous != null && sequence <= previous) return false
        latest[sessionId] = sequence
        return true
    }

    fun end(sessionId: String) {
        latest.remove(sessionId)
    }
}
