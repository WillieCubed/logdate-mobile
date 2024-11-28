package app.logdate.feature.editor.ui.editor

import app.logdate.shared.model.Location
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

// Base class for all entry blocks
sealed class EntryBlockData {
    abstract val id: Uuid
    abstract val timestamp: Instant
    abstract val location: Location?
}

// Specific block types
data class TextBlockData(
    override val id: Uuid = Uuid.random(),
    override val timestamp: Instant = Clock.System.now(),
    override val location: Location?,
    val content: String
) : EntryBlockData()

data class ImageBlockData(
    override val id: Uuid = Uuid.random(),
    override val timestamp: Instant = Clock.System.now(),
    override val location: Location?,
    val uri: String,
    val caption: String
) : EntryBlockData()

data class VideoBlockData(
    override val id: Uuid = Uuid.random(),
    override val timestamp: Instant = Clock.System.now(),
    override val location: Location?,
    val uri: String,
    val caption: String,
    val thumbnailUri: String
) : EntryBlockData()

data class AudioBlockData(
    override val id: Uuid = Uuid.random(),
    override val timestamp: Instant = Clock.System.now(),
    override val location: Location?,
    val uri: String,
    val duration: Long,
    val transcription: String?
) : EntryBlockData()

