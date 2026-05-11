package app.logdate.shared.model.transcription

import kotlinx.serialization.Serializable

/**
 * Client request to reserve a LogDate Cloud transcription session before any
 * audio leaves the device. The server uses this to enforce subscription
 * features and monthly audio-minute quotas before minting a streaming session.
 */
@Serializable
data class CloudTranscriptionSessionRequest(
    val noteId: String,
    val language: String = "en-US",
    val mode: CloudTranscriptionMode = CloudTranscriptionMode.REALTIME,
)

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
    val sessionId: String,
    val noteId: String,
    val language: String,
    val mode: CloudTranscriptionMode,
    val streamPath: String,
    val inputFormat: CloudAudioInputFormat = CloudAudioInputFormat.PCM16_MONO_24KHZ,
    val provider: String = "logdate-cloud",
    val realtimeUrl: String? = null,
    val clientSecret: CloudTranscriptionClientSecret? = null,
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
