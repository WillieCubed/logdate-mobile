package app.logdate.shared.model

import app.logdate.util.UuidSerializer
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Represents a draft of an editor session that can be serialized and saved.
 */
@Serializable
data class EditorDraft(
    val id: @Serializable(with = UuidSerializer::class) Uuid = Uuid.random(),
    val blocks: List<SerializableEntryBlock> = emptyList(),
    val selectedJournalIds: List<@Serializable(with = UuidSerializer::class) Uuid> = emptyList(),
    val createdAt: Instant = Clock.System.now(),
    val lastModifiedAt: Instant = Clock.System.now()
)

/**
 * Serializable representation of an entry block for storing drafts.
 */
@Serializable
sealed class SerializableEntryBlock {
    abstract val id: @Serializable(with = UuidSerializer::class) Uuid
    abstract val timestamp: Instant
    abstract val locationLat: Double?
    abstract val locationLng: Double?
    abstract val altitude: Double?
}

@Serializable
data class SerializableTextBlock(
    override val id: @Serializable(with = UuidSerializer::class) Uuid,
    override val timestamp: Instant,
    override val locationLat: Double? = null,
    override val locationLng: Double? = null,
    override val altitude: Double? = null,
    val content: String = ""
) : SerializableEntryBlock()

@Serializable
data class SerializableImageBlock(
    override val id: @Serializable(with = UuidSerializer::class) Uuid,
    override val timestamp: Instant,
    override val locationLat: Double? = null,
    override val locationLng: Double? = null,
    override val altitude: Double? = null,
    val uri: String? = null,
    val caption: String = ""
) : SerializableEntryBlock()

@Serializable
data class SerializableVideoBlock(
    override val id: @Serializable(with = UuidSerializer::class) Uuid,
    override val timestamp: Instant,
    override val locationLat: Double? = null,
    override val locationLng: Double? = null,
    override val altitude: Double? = null,
    val uri: String? = null,
    val thumbnailUri: String? = null,
    val caption: String = ""
) : SerializableEntryBlock()

@Serializable
data class SerializableAudioBlock(
    override val id: @Serializable(with = UuidSerializer::class) Uuid,
    override val timestamp: Instant,
    override val locationLat: Double? = null,
    override val locationLng: Double? = null,
    override val altitude: Double? = null,
    val uri: String? = null,
    val duration: Long = 0L,
    val transcription: String? = null
) : SerializableEntryBlock()

@Serializable
data class SerializableCameraBlock(
    override val id: @Serializable(with = UuidSerializer::class) Uuid,
    override val timestamp: Instant,
    override val locationLat: Double? = null,
    override val locationLng: Double? = null,
    override val altitude: Double? = null,
    val uri: String? = null
) : SerializableEntryBlock()
