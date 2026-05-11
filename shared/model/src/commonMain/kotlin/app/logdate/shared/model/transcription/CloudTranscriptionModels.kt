package app.logdate.shared.model.transcription

import kotlinx.serialization.Serializable

/**
 * Client request to reserve a LogDate Cloud transcription session before any
 * audio leaves the device. The server uses this to enforce subscription
 * features and monthly audio-minute quotas before minting a streaming session.
 */
@Serializable
data class CloudTranscriptionSessionRequest(
    /** Audio note that will receive the resulting transcript. */
    val noteId: String,
    /** BCP-47 language tag requested by the client. */
    val language: String = "en-US",
    /** Whether the session is for live capture or a second-pass refinement. */
    val mode: CloudTranscriptionMode = CloudTranscriptionMode.REALTIME,
) {
    init {
        require(noteId.isNotBlank()) { "noteId must not be blank" }
        require(language.isNotBlank()) { "language must not be blank" }
    }
}

/**
 * Cloud transcription modes supported by the public API contract.
 */
@Serializable
enum class CloudTranscriptionMode {
    REALTIME,
    REFINEMENT,
}

/**
 * Server response describing how the client should stream audio for the
 * reserved session. `streamPath` is relative to the LogDate Cloud origin so
 * clients do not learn or depend on the downstream ASR provider.
 */
@Serializable
data class CloudTranscriptionSessionResponse(
    /** LogDate-owned session id used for client correlation and diagnostics. */
    val sessionId: String,
    /** Audio note this session was created for. */
    val noteId: String,
    /** Language accepted for this session. */
    val language: String,
    /** Live or refinement mode reserved by the server. */
    val mode: CloudTranscriptionMode,
    /** LogDate Cloud stream path retained for server-side proxy implementations. */
    val streamPath: String,
    /** PCM format the client should send to the realtime recognizer. */
    val inputFormat: CloudAudioInputFormat = CloudAudioInputFormat.PCM16_MONO_24KHZ,
    /** Stable public provider label; downstream ASR vendors remain server-controlled. */
    val provider: String = "logdate-cloud",
    /** Realtime socket URL when the server returns a direct ephemeral provider lease. */
    val realtimeUrl: String? = null,
    /** Short-lived credential scoped to this realtime transcription session. */
    val clientSecret: CloudTranscriptionClientSecret? = null,
    /** Model selected by LogDate Cloud for diagnostics and transcript metadata. */
    val modelId: String? = null,
)

/**
 * Audio formats accepted by LogDate Cloud transcription sessions.
 */
@Serializable
enum class CloudAudioInputFormat {
    PCM16_MONO_16KHZ,
    PCM16_MONO_24KHZ,
}

/**
 * Short-lived credential used by a client to attach to a reserved realtime
 * transcription session without exposing server API keys.
 */
@Serializable
data class CloudTranscriptionClientSecret(
    val value: String,
    val expiresAtEpochSeconds: Long,
)
