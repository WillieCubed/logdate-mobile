package app.logdate.client.domain.export.archive

import app.logdate.client.domain.export.ExportSchemaVersion
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * `manifest.json`: what this archive is, where everything in it is, and what it does not hold.
 *
 * It is the one file a program needs to read first. [contents] lists every data file with its
 * media type and the schema that describes it, so a consumer never has to guess a file name.
 */
@Serializable
data class ArchiveManifest(
    val format: String = FORMAT,
    val schemaVersion: ExportSchemaVersion = ExportSchemaVersion.V2_0,
    val exportedAt: Instant,
    /** IANA id of the zone the export was made in, used to show local times when a note has none. */
    val exportTimeZone: String,
    val generator: ArchiveGenerator,
    val owner: ArchiveOwner,
    val scope: ArchiveScope,
    val counts: ArchiveCounts,
    val contents: List<ArchiveContent>,
) {
    companion object {
        const val FORMAT = "logdate-export"
    }
}

@Serializable
data class ArchiveGenerator(
    val name: String = "LogDate",
    val version: String,
)

@Serializable
data class ArchiveOwner(
    val displayName: String? = null,
)

/**
 * What the archive covers. [complete] is true only when nothing the app holds for the user is left
 * out; otherwise [omitted] says which categories are missing and why.
 */
@Serializable
data class ArchiveScope(
    val complete: Boolean,
    val dateRange: ArchiveDateRange? = null,
    val omitted: List<ArchiveOmission> = emptyList(),
)

@Serializable
data class ArchiveDateRange(
    val from: Instant? = null,
    val to: Instant? = null,
)

@Serializable
data class ArchiveOmission(
    val category: ArchiveCategory,
    val reason: ArchiveOmissionReason,
)

@Serializable
enum class ArchiveCategory {
    @SerialName("journals")
    JOURNALS,

    @SerialName("notes")
    NOTES,

    @SerialName("drafts")
    DRAFTS,

    @SerialName("editorDrafts")
    EDITOR_DRAFTS,

    @SerialName("media")
    MEDIA,

    @SerialName("profile")
    PROFILE,

    @SerialName("places")
    PLACES,

    @SerialName("locationHistory")
    LOCATION_HISTORY,

    @SerialName("transcripts")
    TRANSCRIPTS,

    @SerialName("audioTags")
    AUDIO_TAGS,

    @SerialName("people")
    PEOPLE,

    @SerialName("events")
    EVENTS,

    @SerialName("rewinds")
    REWINDS,

    @SerialName("postcards")
    POSTCARDS,

    @SerialName("stickers")
    STICKERS,

    @SerialName("healthSnapshots")
    HEALTH_SNAPSHOTS,

    @SerialName("favorites")
    FAVORITES,

    @SerialName("journalCovers")
    JOURNAL_COVERS,

    @SerialName("profilePhoto")
    PROFILE_PHOTO,

    @SerialName("settings")
    SETTINGS,
}

@Serializable
enum class ArchiveOmissionReason {
    /** The person chose not to include it in this export. */
    @SerialName("notRequested")
    NOT_REQUESTED,

    /** Outside the date range chosen for this export. */
    @SerialName("outsideDateRange")
    OUTSIDE_DATE_RANGE,

    /** The data exists but could not be read while exporting. */
    @SerialName("unreadable")
    UNREADABLE,

    /** This version of the app does not export it yet. */
    @SerialName("notYetSupported")
    NOT_YET_SUPPORTED,
}

@Serializable
data class ArchiveCounts(
    val journals: Int,
    val notes: Int,
    val drafts: Int,
    val media: Int,
    val places: Int,
    val locationSamples: Int,
    val hasProfile: Boolean,
)

/** One file in the archive that a program may want to read. */
@Serializable
data class ArchiveContent(
    val role: ArchiveRole,
    val path: ArchivePath,
    val mediaType: String,
    /** The JSON Schema that describes this file, when it has one. */
    val schema: ArchivePath? = null,
)

@Serializable
enum class ArchiveRole {
    @SerialName("readme")
    README,

    @SerialName("journals")
    JOURNALS,

    @SerialName("notes")
    NOTES,

    @SerialName("drafts")
    DRAFTS,

    @SerialName("places")
    PLACES,

    @SerialName("profile")
    PROFILE,

    @SerialName("locationHistory")
    LOCATION_HISTORY,

    @SerialName("mediaInventory")
    MEDIA_INVENTORY,

    @SerialName("checksums")
    CHECKSUMS,

    @SerialName("readableIndex")
    READABLE_INDEX,
}
