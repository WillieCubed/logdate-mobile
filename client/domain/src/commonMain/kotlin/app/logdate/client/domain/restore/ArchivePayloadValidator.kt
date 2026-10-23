package app.logdate.client.domain.restore

import app.logdate.client.domain.export.ExportMediaManifest
import app.logdate.client.domain.export.ExportMetadata
import app.logdate.client.domain.export.ExportSchemaVersion
import app.logdate.client.domain.export.LocationHistoryPayload
import app.logdate.client.domain.export.PlacesPayload
import app.logdate.client.domain.export.ProfilePayload
import kotlinx.serialization.json.Json

/** Validates archive documents separately from reads of the destination's stored data. */
object ArchivePayloadValidator {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    fun validate(archive: RestoreArchiveBundle) {
        val bundle =
            when (archive) {
                is RestoreArchiveBundle.V1 -> archive.bundle
                is RestoreArchiveBundle.V2 -> archive.bundle.adaptForRestore("", "").legacy
            }
        val metadata = json.decodeFromString<ExportMetadata>(bundle.metadataJson)
        if (metadata.version.major != ExportSchemaVersion.V1_0.major || metadata.version > ExportSchemaVersion.V1_2) {
            throw UnsupportedExportVersionException(metadata.version)
        }
        json.decodeFromString<JournalsPayload>(bundle.journalsJson)
        json.decodeFromString<NotesPayload>(bundle.notesJson).notes.forEach { it.locationDetails?.validateForRestore() }
        json.decodeFromString<DraftsPayload>(bundle.draftsJson)
        json.decodeFromString<JournalNotesPayload>(bundle.journalNotesJson)
        bundle.profileJson?.let { json.decodeFromString<ProfilePayload>(it) }
        bundle.placesJson?.let { json.decodeFromString<PlacesPayload>(it).places.forEach { place -> place.toPlace() } }
        bundle.locationHistoryJson?.let { json.decodeFromString<LocationHistoryPayload>(it) }
        bundle.mediaManifestJson?.let { json.decodeFromString<ExportMediaManifest>(it) }
    }
}
