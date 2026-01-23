package app.logdate.client.media.audio

import kotlin.uuid.Uuid

/**
 * Result of preparing a file target for audio recording.
 */
data class AudioRecordingTarget(
    val noteId: Uuid?,
    val path: String,
)

/**
 * Abstraction for resolving storage locations for audio recordings.
 * Implementations should return app-private, durable storage paths.
 */
interface AudioStorage {
    /**
     * Creates a recording target path for the given note ID.
     * If [noteId] is null, a unique filename should be generated.
     */
    suspend fun createRecordingTarget(
        noteId: Uuid?,
        extension: String = "m4a",
    ): AudioRecordingTarget
}
