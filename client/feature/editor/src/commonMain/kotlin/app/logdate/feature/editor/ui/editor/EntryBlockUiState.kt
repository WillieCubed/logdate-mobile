package app.logdate.feature.editor.ui.editor

import app.logdate.feature.editor.ui.camera.CapturedMediaType
import app.logdate.shared.model.Location
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Base interface for entry block data.
 * Each block represents a discrete piece of content within a journal entry.
 */
sealed interface EntryBlockUiState {
    /**
     * Unique identifier for the block.
     */
    val id: Uuid

    /**
     * Timestamp when the block was created.
     */
    val timestamp: Instant

    /**
     * Location data associated with the block, if any.
     */
    val location: Location?

    /**
     * Returns true if the block has content that can be saved.
     */
    fun hasContent(): Boolean
}

/**
 * Text block data representing text content in a journal entry.
 */
data class TextBlockUiState(
    override val id: Uuid = Uuid.random(),
    override val timestamp: Instant = Clock.System.now(),
    override val location: Location? = null,
    val content: String = ""
) : EntryBlockUiState {
    override fun hasContent(): Boolean = content.isNotBlank()
}

/**
 * Image block data representing an image in a journal entry.
 */
data class ImageBlockUiState(
    override val id: Uuid = Uuid.random(),
    override val timestamp: Instant = Clock.System.now(),
    override val location: Location? = null,
    val uri: String? = null,
    val caption: String = ""
) : EntryBlockUiState {
    override fun hasContent(): Boolean = uri != null
}

/**
 * Camera block data representing media captured from the camera in a journal entry.
 * Can be either a photo or a video, determined by [mediaType].
 *
 * @property id Unique identifier for the block.
 * @property timestamp When the block was created.
 * @property location Geographic location where the media was captured, if available.
 * @property uri Content URI pointing to the captured photo or video file.
 * @property caption User-provided caption or description for the media.
 * @property mediaType Type of media captured (photo or video).
 * @property durationMs Duration in milliseconds (only applicable for videos).
 */
data class CameraBlockUiState(
    override val id: Uuid = Uuid.random(),
    override val timestamp: Instant = Clock.System.now(),
    override val location: Location? = null,
    val uri: String? = null,
    val caption: String = "",
    val mediaType: CapturedMediaType = CapturedMediaType.PHOTO,
    val durationMs: Long = 0
) : EntryBlockUiState {
    override fun hasContent(): Boolean = uri != null

    /**
     * Returns a formatted duration string in MM:SS format.
     * Only meaningful when [mediaType] is [CapturedMediaType.VIDEO].
     */
    val formattedDuration: String
        get() {
            val seconds = (durationMs / 1000) % 60
            val minutes = (durationMs / 1000) / 60
            return "%02d:%02d".format(minutes, seconds)
        }
}

/**
 * Video block data representing a video in a journal entry.
 *
 * @property id Unique identifier for the block.
 * @property timestamp When the block was created.
 * @property location Geographic location where the video was recorded, if available.
 * @property uri Content URI pointing to the video file.
 * @property caption User-provided caption or description for the video.
 * @property durationMs Duration of the video in milliseconds.
 */
data class VideoBlockUiState(
    override val id: Uuid = Uuid.random(),
    override val timestamp: Instant = Clock.System.now(),
    override val location: Location? = null,
    val uri: String? = null,
    val caption: String = "",
    val durationMs: Long = 0
) : EntryBlockUiState {
    override fun hasContent(): Boolean = uri != null

    /**
     * Returns a formatted duration string in MM:SS format.
     */
    val formattedDuration: String
        get() {
            val seconds = (durationMs / 1000) % 60
            val minutes = (durationMs / 1000) / 60
            return "%02d:%02d".format(minutes, seconds)
        }
}

/**
 * Audio block data representing an audio recording in a journal entry.
 */
data class AudioBlockUiState(
    override val id: Uuid = Uuid.random(),
    override val timestamp: Instant = Clock.System.now(),
    override val location: Location? = null,
    val uri: String? = null,
    val caption: String = "",
    val duration: Long = 0, // Duration in milliseconds
    val transcription: String = "",
    val isPlaying: Boolean = false,
) : EntryBlockUiState {
    override fun hasContent(): Boolean = uri != null
}