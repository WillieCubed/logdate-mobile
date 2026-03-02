package app.logdate.client.media.audio

/**
 * Result of preparing a file target for audio recording.
 */
data class AudioRecordingTarget(
    val path: String,
)

/**
 * Abstraction for resolving storage locations for audio recordings.
 * Implementations should return app-private, durable storage paths.
 */
interface AudioStorage {
    /**
     * Creates a recording target path.
     * Implementations should generate a unique filename.
     */
    suspend fun createRecordingTarget(extension: String = "m4a"): AudioRecordingTarget
}
