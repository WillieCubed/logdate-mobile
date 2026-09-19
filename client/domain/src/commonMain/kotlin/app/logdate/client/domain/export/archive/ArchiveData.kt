package app.logdate.client.domain.export.archive

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The records in a 2.0 archive's `data/` files.
 *
 * These types describe the archive, not the app: they are written to and read from JSON on their own
 * and never expose a domain class, so a change inside the app cannot change the archive by accident.
 * Instants are RFC 3339 in UTC, ids are lowercase hyphenated UUIDs, and a file is always referred to
 * by [ArchivePath], never by a device path.
 */

@Serializable
data class ArchiveJournalFile(
    val journals: List<ArchiveJournal>,
)

@Serializable
data class ArchiveJournal(
    val id: String,
    val title: String,
    val description: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Serializable
data class ArchiveNoteFile(
    val notes: List<ArchiveNote>,
)

@Serializable
data class ArchiveNote(
    val id: String,
    val type: ArchiveNoteType,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** IANA id of the zone the note was captured in. Absent when it was not recorded. */
    val timeZone: String? = null,
    /** [createdAt] as local time with its UTC offset, present only when [timeZone] is known and valid. */
    val createdAtLocal: String? = null,
    /** The note's text as the person wrote it. */
    val text: String? = null,
    /** How [text] is written, so a reader knows to render it. Present whenever [text] is. */
    val textFormat: ArchiveTextFormat? = null,
    val caption: String? = null,
    val media: ArchiveMediaRef? = null,
    val durationMs: Long? = null,
    val location: ArchiveLocation? = null,
    /** The journals this note appears in. A note may be in several, or in none. */
    val journalIds: List<String> = emptyList(),
)

@Serializable
enum class ArchiveNoteType {
    @SerialName("text")
    TEXT,

    @SerialName("image")
    IMAGE,

    @SerialName("video")
    VIDEO,

    @SerialName("audio")
    AUDIO,
}

@Serializable
enum class ArchiveTextFormat {
    @SerialName("markdown")
    MARKDOWN,
}

/**
 * A note's media file. When [status] is included, [path] and [mediaType] say where it is and what it
 * is; when it is omitted, [omittedReason] says why and there is no path to a missing file.
 */
@Serializable
data class ArchiveMediaRef(
    val status: ArchiveMediaStatus,
    val path: ArchivePath? = null,
    val mediaType: String? = null,
    val omittedReason: ArchiveOmissionReason? = null,
)

@Serializable
enum class ArchiveMediaStatus {
    @SerialName("included")
    INCLUDED,

    @SerialName("omitted")
    OMITTED,
}

@Serializable
data class ArchiveLocation(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Double? = null,
    val placeName: String? = null,
)

@Serializable
data class ArchiveDraftFile(
    val drafts: List<ArchiveDraft>,
)

@Serializable
data class ArchiveDraft(
    val id: String,
    val journalIds: List<String> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val blocks: List<ArchiveDraftBlock> = emptyList(),
)

@Serializable
data class ArchiveDraftBlock(
    val id: String,
    val type: ArchiveBlockType,
    val timestamp: Instant,
    val text: String? = null,
    val media: ArchiveMediaRef? = null,
    val caption: String? = null,
    val durationMs: Long? = null,
    val transcription: String? = null,
    val location: ArchiveLocation? = null,
)

@Serializable
enum class ArchiveBlockType {
    @SerialName("text")
    TEXT,

    @SerialName("image")
    IMAGE,

    @SerialName("video")
    VIDEO,

    @SerialName("audio")
    AUDIO,

    @SerialName("camera")
    CAMERA,
}

@Serializable
data class ArchivePlaceFile(
    val places: List<ArchivePlace>,
)

@Serializable
data class ArchivePlace(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val description: String? = null,
)

@Serializable
data class ArchiveProfileFile(
    val profile: ArchiveProfile,
)

@Serializable
data class ArchiveProfile(
    val displayName: String? = null,
    val birthday: Instant? = null,
    val bio: String? = null,
    val originalBio: String? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)

/** One line of `location-history.jsonl`. */
@Serializable
data class ArchiveLocationSample(
    val timestamp: Instant,
    val loggedAt: Instant,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val accuracyMeters: Double? = null,
    val speedMetersPerSecond: Double? = null,
    val bearingDegrees: Double? = null,
    val confidence: Double,
    val isGenuine: Boolean,
    val isMock: Boolean,
    val capturePipeline: String,
    val captureSource: String,
)

@Serializable
data class ArchiveMediaFile(
    val files: List<ArchiveMediaInventoryEntry>,
)

/** A media file in the archive with the checks needed to confirm it arrived intact. */
@Serializable
data class ArchiveMediaInventoryEntry(
    val path: ArchivePath,
    val mediaType: String,
    val bytes: Long,
    val sha256: String,
)
