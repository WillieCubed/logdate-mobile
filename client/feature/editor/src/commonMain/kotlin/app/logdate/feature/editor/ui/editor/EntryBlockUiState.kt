package app.logdate.feature.editor.ui.editor

import app.logdate.feature.editor.ui.camera.CapturedMediaType
import app.logdate.feature.editor.ui.formatMediaDuration
import app.logdate.shared.model.Location
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Base interface for entry block data.
 * Each block represents a discrete piece of content within a journal entry.
 */
sealed interface EntryBlockUiState {
    val id: Uuid
    val timestamp: Instant
    val location: Location?

    fun hasContent(): Boolean
}

/**
 * Sub-interface for blocks backed by a media URI (image, video, audio, camera).
 */
sealed interface MediaBlockUiState : EntryBlockUiState {
    val uri: String?
    val caption: String
}

/**
 * Text block data representing text content in a journal entry.
 */
data class TextBlockUiState(
    override val id: Uuid = Uuid.random(),
    override val timestamp: Instant = Clock.System.now(),
    override val location: Location? = null,
    val content: String = "",
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
    override val uri: String? = null,
    override val caption: String = "",
) : MediaBlockUiState {
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
    override val uri: String? = null,
    override val caption: String = "",
    val mediaType: CapturedMediaType = CapturedMediaType.PHOTO,
    val durationMs: Long = 0,
) : MediaBlockUiState {
    override fun hasContent(): Boolean = uri != null

    /**
     * Returns a formatted duration string in MM:SS format.
     * Only meaningful when [mediaType] is [CapturedMediaType.VIDEO].
     */
    val formattedDuration: String
        get() {
            return formatMediaDuration(durationMs, true)
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
    override val uri: String? = null,
    override val caption: String = "",
    val durationMs: Long = 0,
) : MediaBlockUiState {
    override fun hasContent(): Boolean = uri != null

    /**
     * Returns a formatted duration string in MM:SS format.
     */
    val formattedDuration: String
        get() {
            return formatMediaDuration(durationMs, true)
        }
}

/**
 * Audio block data representing an audio recording in a journal entry.
 */
data class AudioBlockUiState(
    override val id: Uuid = Uuid.random(),
    override val timestamp: Instant = Clock.System.now(),
    override val location: Location? = null,
    override val uri: String? = null,
    override val caption: String = "",
    val duration: Long = 0,
    val transcription: String = "",
) : MediaBlockUiState {
    override fun hasContent(): Boolean = uri != null
}
