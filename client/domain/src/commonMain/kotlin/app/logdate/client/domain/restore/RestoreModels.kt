package app.logdate.client.domain.restore

import app.logdate.client.domain.export.ExportDraft
import app.logdate.client.domain.export.ExportJournalNoteRelation
import app.logdate.client.domain.export.ExportLocationHistoryItem
import app.logdate.client.domain.export.ExportMetadata
import app.logdate.client.domain.export.ExportNote
import app.logdate.client.domain.export.ExportPlace
import app.logdate.client.repository.location.LocationCapturePipeline
import app.logdate.client.repository.location.LocationCaptureSource
import app.logdate.client.repository.location.LocationLogRecord
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Journal
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import app.logdate.shared.model.Place
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

interface MediaImporter {
    suspend fun importMedia(exportPath: String): String?
}

data class RestoreBundle(
    val metadataJson: String,
    val journalsJson: String,
    val notesJson: String,
    val journalNotesJson: String,
    val draftsJson: String,
    val profileJson: String? = null,
    val placesJson: String? = null,
    val locationHistoryJson: String? = null,
    val mediaManifestJson: String? = null,
)

data class RestoreOptions(
    val strategy: RestoreStrategy = RestoreStrategy.MERGE_KEEP_NEWEST,
    val includeDrafts: Boolean = true,
    val includeMedia: Boolean = true,
    val preservePopulatedLocalProfile: Boolean = false,
)

enum class RestoreStrategy {
    MERGE_KEEP_NEWEST,
    REPLACE_EXISTING,
}

data class RestoreResult(
    val metadata: ExportMetadata,
    val journalsImported: Int,
    val notesImported: Int,
    val draftsImported: Int,
    val journalLinksImported: Int,
    val mediaImported: Int,
    val warnings: List<String>,
    val integrityMismatches: List<IntegrityMismatch> = emptyList(),
)

/**
 * A discrepancy between metadata.json stats and actual archive contents.
 */
@Serializable
data class IntegrityMismatch(
    val category: IntegrityCategory,
    val expected: Int,
    val actual: Int,
)

@Serializable
enum class IntegrityCategory {
    JOURNALS,
    NOTES,
    DRAFTS,
    MEDIA,
    PROFILE,
    PLACES,
    LOCATION_HISTORY,
}

internal data class MediaResolution(
    val uri: String?,
    val imported: Boolean,
    val allowSourceFallback: Boolean,
)

@Serializable
internal data class JournalsPayload(
    val journals: List<Journal>,
)

@Serializable
internal data class NotesPayload(
    val notes: List<ExportNote>,
)

@Serializable
internal data class DraftsPayload(
    val drafts: List<ExportDraft>,
)

@Serializable
internal data class JournalNotesPayload(
    @SerialName("journal_notes")
    val journalNotes: List<ExportJournalNoteRelation>,
)

internal fun ExportPlace.toPlace(): Place.UserDefined =
    Place.UserDefined(
        id = Uuid.parse(id),
        displayName = displayName,
        lat = latitude,
        lng = longitude,
        radiusMeters = radiusMeters,
        description = description,
    )

internal fun ExportLocationHistoryItem.toLocationLogRecord(): LocationLogRecord =
    LocationLogRecord(
        sampleId = sampleId,
        userId = userId,
        deviceId = deviceId,
        timestamp = timestamp,
        loggedAt = loggedAt,
        location =
            Location(
                latitude = latitude,
                longitude = longitude,
                altitude = LocationAltitude(altitudeMeters, AltitudeUnit.METERS),
            ),
        confidence = confidence,
        isGenuine = isGenuine,
        capturePipeline =
            runCatching { LocationCapturePipeline.valueOf(capturePipeline) }
                .getOrDefault(LocationCapturePipeline.LEGACY),
        captureSource =
            runCatching { LocationCaptureSource.valueOf(captureSource) }
                .getOrDefault(LocationCaptureSource.BACKGROUND_PERIODIC),
        accuracyMeters = accuracyMeters,
        speedMetersPerSecond = speedMetersPerSecond,
        bearingDegrees = bearingDegrees,
        isMock = isMock,
    )
