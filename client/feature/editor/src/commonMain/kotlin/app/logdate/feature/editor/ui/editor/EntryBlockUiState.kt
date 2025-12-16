package app.logdate.feature.editor.ui.editor

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
 * Camera block data representing an image captured from the camera in a journal entry.
 */
data class CameraBlockUiState(
    override val id: Uuid = Uuid.random(),
    override val timestamp: Instant = Clock.System.now(),
    override val location: Location? = null,
    val uri: String? = null,
    val caption: String = ""
) : EntryBlockUiState {
    override fun hasContent(): Boolean = uri != null
}

/**
 * Video block data representing a video in a journal entry.
 */
data class VideoBlockUiState(
    override val id: Uuid = Uuid.random(),
    override val timestamp: Instant = Clock.System.now(),
    override val location: Location? = null,
    val uri: String? = null,
    val caption: String = "",
    val duration: Long = 0 // Duration in milliseconds
) : EntryBlockUiState {
    override fun hasContent(): Boolean = uri != null
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