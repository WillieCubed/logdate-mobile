package app.logdate.client.domain.restore

import app.logdate.client.domain.export.ExportDraft
import app.logdate.client.domain.export.ExportJournalNoteRelation
import app.logdate.client.domain.export.ExportLocationHistoryItem
import app.logdate.client.domain.export.ExportMediaFile
import app.logdate.client.domain.export.ExportMediaManifest
import app.logdate.client.domain.export.ExportMetadata
import app.logdate.client.domain.export.ExportNote
import app.logdate.client.domain.export.ExportPlace
import app.logdate.client.domain.export.ExportSchemaVersion
import app.logdate.client.domain.export.ExportStats
import app.logdate.client.domain.export.LocationHistoryPayload
import app.logdate.client.domain.export.PlacesPayload
import app.logdate.client.domain.export.ProfilePayload
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.journals.NotePlace
import app.logdate.client.repository.journals.SyncableJournalContentRepository
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.repository.journals.SyncableJournalRepository
import app.logdate.client.repository.location.LocationCapturePipeline
import app.logdate.client.repository.location.LocationCaptureSource
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.client.repository.location.LocationLogRecord
import app.logdate.client.repository.places.UserPlacesRepository
import app.logdate.client.repository.profile.ProfileRepository
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import app.logdate.shared.model.Place
import app.logdate.shared.model.SerializableAudioBlock
import app.logdate.shared.model.SerializableCameraBlock
import app.logdate.shared.model.SerializableEntryBlock
import app.logdate.shared.model.SerializableImageBlock
import app.logdate.shared.model.SerializableTextBlock
import app.logdate.shared.model.SerializableVideoBlock
import app.logdate.shared.model.profile.LogDateProfile
import io.github.aakira.napier.Napier
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.time.Instant
import kotlin.uuid.Uuid

class RestoreUserDataUseCase(
    private val journalRepository: JournalRepository,
    private val journalNotesRepository: JournalNotesRepository,
    private val journalContentRepository: JournalContentRepository,
    private val profileRepository: ProfileRepository,
    private val userPlacesRepository: UserPlacesRepository,
    private val locationHistoryRepository: LocationHistoryRepository,
) {
    private val migrationRunner = ExportMigrationRunner(exportMigrations)
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    suspend fun restore(
        bundle: RestoreBundle,
        options: RestoreOptions = RestoreOptions(),
        mediaImporter: MediaImporter? = null,
        onProgress: (suspend (RestoreProgressPhase) -> Unit)? = null,
    ): RestoreResult {
        val metadata = json.decodeFromString<ExportMetadata>(bundle.metadataJson)

        if (metadata.version.major > ExportSchemaVersion.CURRENT.major) {
            throw UnsupportedExportVersionException(metadata.version)
        }

        val journalsPayload = json.decodeFromString<JournalsPayload>(bundle.journalsJson)
        val notesPayload = json.decodeFromString<NotesPayload>(bundle.notesJson)
        val draftsPayload = json.decodeFromString<DraftsPayload>(bundle.draftsJson)
        val journalNotesPayload = json.decodeFromString<JournalNotesPayload>(bundle.journalNotesJson)
        val profilePayload = bundle.profileJson?.let { json.decodeFromString<ProfilePayload>(it) }
        val placesPayload = bundle.placesJson?.let { json.decodeFromString<PlacesPayload>(it) }
        val locationHistoryPayload = bundle.locationHistoryJson?.let { json.decodeFromString<LocationHistoryPayload>(it) }
        val manifest = bundle.mediaManifestJson?.let { json.decodeFromString<ExportMediaManifest>(it) }
        val manifestIndex = manifest?.files?.associateBy { it.sourceUri }.orEmpty()

        val migrated =
            migrationRunner.run(
                sourceVersion = metadata.version,
                bundle =
                    ParsedExportBundle(
                        journals = journalsPayload.journals,
                        notes = notesPayload.notes,
                        drafts = draftsPayload.drafts,
                    ),
            )

        val syncableJournals = journalRepository as? SyncableJournalRepository
        val syncableNotes = journalNotesRepository as? SyncableJournalNotesRepository
        val syncableContent = journalContentRepository as? SyncableJournalContentRepository

        var journalsImported = 0
        var notesImported = 0
        var draftsImported = 0
        var linksImported = 0
        var mediaImported = 0
        val warnings = mutableListOf<String>()

        // Verify metadata.json stats match actual archive contents
        val integrityMismatches =
            validateArchiveIntegrity(
                stats = metadata.stats,
                actualJournals = journalsPayload.journals.size,
                actualNotes = notesPayload.notes.size,
                actualDrafts = draftsPayload.drafts.size,
                actualMedia = manifest?.files?.size ?: 0,
                actualPlaces = placesPayload?.places?.size ?: 0,
                actualLocationHistory = locationHistoryPayload?.locationHistory?.size ?: 0,
                hasProfile = profilePayload != null,
            )

        val createdJournalIds = mutableListOf<Uuid>()
        val createdNoteIds = mutableListOf<Uuid>()
        val createdLinks = mutableListOf<Pair<Uuid, Uuid>>()
        val createdDraftIds = mutableListOf<Uuid>()

        try {
            onProgress?.invoke(RestoreProgressPhase.RESTORING_JOURNALS)
            Napier.i("Restore: importing ${migrated.journals.size} journals")
            for (journal in migrated.journals) {
                val existing = journalRepository.getJournalById(journal.id)
                val shouldWrite = shouldOverwrite(existing?.lastUpdated, journal.lastUpdated, options.strategy)
                if (existing == null) {
                    if (syncableJournals != null) {
                        syncableJournals.createFromSync(journal)
                    } else {
                        journalRepository.create(journal)
                    }
                    createdJournalIds.add(journal.id)
                    journalsImported++
                } else if (shouldWrite) {
                    if (syncableJournals != null) {
                        syncableJournals.updateFromSync(journal)
                    } else {
                        journalRepository.update(journal)
                    }
                    journalsImported++
                }
            }

            onProgress?.invoke(RestoreProgressPhase.RESTORING_NOTES)
            Napier.i("Restore: importing ${migrated.notes.size} notes")
            for (note in migrated.notes) {
                val parsedId = parseUuid(note.id, warnings) ?: continue
                val mediaResolution = resolveMediaReference(note.mediaPath, manifestIndex, mediaImporter)
                if (mediaResolution.imported) {
                    mediaImported++
                }
                val restored = note.toJournalNote(parsedId, mediaResolution)
                if (restored == null) {
                    val normalizedType = note.type.lowercase()
                    val message =
                        if (normalizedType == "image" || normalizedType == "video" || normalizedType == "audio") {
                            val mediaPath = note.mediaPath ?: "unknown"
                            val status =
                                if (mediaImporter != null && manifestIndex.containsKey(mediaPath)) {
                                    "could not be imported (file missing or corrupted)"
                                } else {
                                    "not found in archive"
                                }
                            "Skipped $normalizedType note (ID: ${note.id}) - media file $status: $mediaPath"
                        } else {
                            "Skipped unsupported note type: ${note.type}"
                        }
                    Napier.w(message)
                    warnings.add(message)
                    continue
                }

                val existing = journalNotesRepository.getNoteById(parsedId)
                val shouldWrite = shouldOverwrite(existing?.lastUpdated, restored.lastUpdated, options.strategy)
                if (existing == null) {
                    if (syncableNotes != null) {
                        syncableNotes.createFromSync(restored)
                    } else {
                        journalNotesRepository.create(restored)
                    }
                    createdNoteIds.add(parsedId)
                    notesImported++
                } else if (shouldWrite) {
                    if (syncableNotes != null) {
                        syncableNotes.deleteFromSync(parsedId)
                        syncableNotes.createFromSync(restored)
                    } else {
                        journalNotesRepository.removeById(parsedId)
                        journalNotesRepository.create(restored)
                    }
                    notesImported++
                }
            }

            onProgress?.invoke(RestoreProgressPhase.RESTORING_LINKS)
            Napier.i("Restore: importing ${journalNotesPayload.journalNotes.size} journal links")
            for (relation in journalNotesPayload.journalNotes) {
                val journalId = parseUuid(relation.journalId, warnings) ?: continue
                val noteId = parseUuid(relation.noteId, warnings) ?: continue

                if (syncableContent != null) {
                    syncableContent.addContentToJournalFromSync(noteId, journalId)
                } else {
                    journalContentRepository.addContentToJournal(noteId, journalId)
                }
                createdLinks.add(noteId to journalId)
                linksImported++
            }

            if (options.includeDrafts) {
                onProgress?.invoke(RestoreProgressPhase.RESTORING_DRAFTS)
                Napier.i("Restore: importing ${migrated.drafts.size} drafts")
                for (draft in migrated.drafts) {
                    val restored =
                        restoreDraft(draft, manifestIndex, mediaImporter) { imported ->
                            if (imported) {
                                mediaImported++
                            }
                        }
                    journalRepository.saveDraft(restored)
                    createdDraftIds.add(restored.id)
                    draftsImported++
                }
            }

            restoreProfile(profilePayload?.profile, options.strategy, warnings, onProgress)
            restorePlaces(placesPayload?.places.orEmpty(), warnings, onProgress)
            restoreLocationHistory(locationHistoryPayload?.locationHistory.orEmpty(), warnings, onProgress)

            onProgress?.invoke(RestoreProgressPhase.COMPLETED)

            return RestoreResult(
                metadata = metadata,
                journalsImported = journalsImported,
                notesImported = notesImported,
                draftsImported = draftsImported,
                journalLinksImported = linksImported,
                mediaImported = mediaImported,
                warnings = warnings,
                integrityMismatches = integrityMismatches,
            )
        } catch (e: Exception) {
            rollback(createdLinks, createdNoteIds, createdJournalIds, createdDraftIds)
            throw e
        }
    }

    private suspend fun rollback(
        links: List<Pair<Uuid, Uuid>>,
        noteIds: List<Uuid>,
        journalIds: List<Uuid>,
        draftIds: List<Uuid>,
    ) {
        Napier.w(
            "Restore failed, rolling back ${links.size} links, ${noteIds.size} notes, ${journalIds.size} journals, ${draftIds.size} drafts",
        )
        for ((contentId, journalId) in links) {
            runCatching { journalContentRepository.removeContentFromJournal(contentId, journalId) }
                .onFailure { Napier.e("Rollback: failed to remove link $contentId->$journalId", it) }
        }
        for (noteId in noteIds) {
            runCatching { journalNotesRepository.removeById(noteId) }
                .onFailure { Napier.e("Rollback: failed to remove note $noteId", it) }
        }
        for (journalId in journalIds) {
            runCatching { journalRepository.delete(journalId) }
                .onFailure { Napier.e("Rollback: failed to delete journal $journalId", it) }
        }
        for (draftId in draftIds) {
            runCatching { journalRepository.deleteDraft(draftId) }
                .onFailure { Napier.e("Rollback: failed to delete draft $draftId", it) }
        }
    }

    private fun shouldOverwrite(
        existing: Instant?,
        incoming: Instant,
        strategy: RestoreStrategy,
    ): Boolean =
        when (strategy) {
            RestoreStrategy.MERGE_KEEP_NEWEST -> existing == null || incoming > existing
            RestoreStrategy.REPLACE_EXISTING -> true
        }

    private fun validateArchiveIntegrity(
        stats: ExportStats,
        actualJournals: Int,
        actualNotes: Int,
        actualDrafts: Int,
        actualMedia: Int,
        actualPlaces: Int,
        actualLocationHistory: Int,
        hasProfile: Boolean,
    ): List<IntegrityMismatch> =
        buildList {
            if (stats.journalCount != actualJournals) {
                add(IntegrityMismatch(IntegrityCategory.JOURNALS, stats.journalCount, actualJournals))
            }
            if (stats.noteCount != actualNotes) {
                add(IntegrityMismatch(IntegrityCategory.NOTES, stats.noteCount, actualNotes))
            }
            if (stats.draftCount != actualDrafts) {
                add(IntegrityMismatch(IntegrityCategory.DRAFTS, stats.draftCount, actualDrafts))
            }
            if (stats.mediaCount != actualMedia) {
                add(IntegrityMismatch(IntegrityCategory.MEDIA, stats.mediaCount, actualMedia))
            }
            if (stats.placeCount != actualPlaces) {
                add(IntegrityMismatch(IntegrityCategory.PLACES, stats.placeCount, actualPlaces))
            }
            if (stats.locationHistoryCount != actualLocationHistory) {
                add(IntegrityMismatch(IntegrityCategory.LOCATION_HISTORY, stats.locationHistoryCount, actualLocationHistory))
            }
            if (stats.hasProfile != hasProfile) {
                add(IntegrityMismatch(IntegrityCategory.PROFILE, if (stats.hasProfile) 1 else 0, if (hasProfile) 1 else 0))
            }
        }

    private suspend fun restoreProfile(
        profile: LogDateProfile?,
        strategy: RestoreStrategy,
        warnings: MutableList<String>,
        onProgress: (suspend (RestoreProgressPhase) -> Unit)?,
    ) {
        if (profile == null) {
            return
        }

        onProgress?.invoke(RestoreProgressPhase.RESTORING_PROFILE)
        Napier.i("Restore: importing profile")
        val existing = profileRepository.getCurrentProfile()
        val shouldWrite =
            if (strategy == RestoreStrategy.MERGE_KEEP_NEWEST && existing == LogDateProfile()) {
                profile != LogDateProfile()
            } else {
                shouldOverwrite(existing.lastUpdatedAt, profile.lastUpdatedAt, strategy)
            }
        if (!shouldWrite) {
            return
        }

        if (existing.displayName != profile.displayName) {
            profileRepository
                .updateDisplayName(profile.displayName)
                .onFailure { warnings.add("Failed to restore profile display name: ${it.message ?: "unknown error"}") }
        }
        if (existing.birthday != profile.birthday) {
            profileRepository
                .updateBirthday(profile.birthday)
                .onFailure { warnings.add("Failed to restore profile birthday: ${it.message ?: "unknown error"}") }
        }
        if (existing.profilePhotoUri != profile.profilePhotoUri) {
            profileRepository
                .updateProfilePhoto(profile.profilePhotoUri)
                .onFailure { warnings.add("Failed to restore profile photo: ${it.message ?: "unknown error"}") }
        }
        if (existing.bio != profile.bio || existing.originalBio != profile.originalBio) {
            profileRepository
                .updateBio(profile.bio, profile.originalBio)
                .onFailure { warnings.add("Failed to restore profile bio: ${it.message ?: "unknown error"}") }
        }
    }

    private suspend fun restorePlaces(
        places: List<ExportPlace>,
        warnings: MutableList<String>,
        onProgress: (suspend (RestoreProgressPhase) -> Unit)?,
    ) {
        if (places.isEmpty()) {
            return
        }

        onProgress?.invoke(RestoreProgressPhase.RESTORING_PLACES)
        Napier.i("Restore: importing ${places.size} places")
        val existingPlaces = userPlacesRepository.getAllPlaces().associateBy { it.uid.toString() }
        for (place in places) {
            val restored = place.toPlace()
            val existing = existingPlaces[place.id]
            if (existing == null) {
                userPlacesRepository
                    .createPlace(restored)
                    .onFailure { warnings.add("Failed to restore place ${place.displayName}: ${it.message ?: "unknown error"}") }
            } else {
                userPlacesRepository
                    .updatePlace(restored)
                    .onFailure { warnings.add("Failed to update place ${place.displayName}: ${it.message ?: "unknown error"}") }
            }
        }
    }

    private suspend fun restoreLocationHistory(
        entries: List<ExportLocationHistoryItem>,
        warnings: MutableList<String>,
        onProgress: (suspend (RestoreProgressPhase) -> Unit)?,
    ) {
        if (entries.isEmpty()) {
            return
        }

        onProgress?.invoke(RestoreProgressPhase.RESTORING_LOCATION_HISTORY)
        Napier.i("Restore: importing ${entries.size} location history samples")
        val seenSampleIds = mutableSetOf<String>()
        for (entry in entries) {
            if (!seenSampleIds.add(entry.sampleId)) {
                continue
            }
            locationHistoryRepository
                .logLocation(entry.toLocationLogRecord())
                .onFailure { error ->
                    warnings.add("Failed to restore location sample ${entry.sampleId}: ${error.message ?: "unknown error"}")
                }
        }
    }

    private fun parseUuid(
        value: String,
        warnings: MutableList<String>,
    ): Uuid? =
        runCatching { Uuid.parse(value) }
            .onFailure { warnings.add("Invalid UUID in restore payload: $value") }
            .getOrNull()

    private suspend fun resolveMediaReference(
        sourceUri: String?,
        manifestIndex: Map<String, ExportMediaFile>,
        mediaImporter: MediaImporter?,
    ): MediaResolution {
        if (sourceUri.isNullOrBlank()) {
            return MediaResolution(uri = null, imported = false, allowSourceFallback = false)
        }
        val manifestEntry = manifestIndex[sourceUri]
        val exportPath = manifestEntry?.exportPath ?: sourceUri
        val imported = mediaImporter?.importMedia(exportPath)
        return if (imported != null) {
            MediaResolution(uri = imported, imported = true, allowSourceFallback = false)
        } else if (mediaImporter == null || manifestEntry == null) {
            MediaResolution(uri = sourceUri, imported = false, allowSourceFallback = true)
        } else {
            MediaResolution(uri = null, imported = false, allowSourceFallback = false)
        }
    }

    private suspend fun restoreDraft(
        draft: ExportDraft,
        manifestIndex: Map<String, ExportMediaFile>,
        mediaImporter: MediaImporter?,
        onMediaImported: (Boolean) -> Unit,
    ): EditorDraft {
        val restoredBlocks =
            if (draft.blocks.isNotEmpty()) {
                draft.blocks.mapNotNull { block ->
                    restoreDraftBlock(block, manifestIndex, mediaImporter, onMediaImported)
                }
            } else {
                emptyList()
            }

        if (restoredBlocks.isNotEmpty()) {
            return EditorDraft(
                id = runCatching { Uuid.parse(draft.id) }.getOrDefault(Uuid.random()),
                blocks = restoredBlocks,
                selectedJournalIds = draft.parseSelectedJournalIds(),
                createdAt = draft.createdAt,
                lastModifiedAt = draft.updatedAt,
            )
        }

        val blocks = mutableListOf<SerializableEntryBlock>()
        if (draft.content.isNotBlank()) {
            blocks.add(
                SerializableTextBlock(
                    id = Uuid.random(),
                    timestamp = draft.createdAt,
                    locationLat = draft.location?.latitude,
                    locationLng = draft.location?.longitude,
                    content = draft.content,
                ),
            )
        }

        draft.mediaReferences.forEach { reference ->
            val resolution = resolveMediaReference(reference, manifestIndex, mediaImporter)
            onMediaImported(resolution.imported)
            val uri = resolution.uri ?: return@forEach
            blocks.add(
                SerializableCameraBlock(
                    id = Uuid.random(),
                    timestamp = draft.updatedAt,
                    uri = uri,
                ),
            )
        }

        val selectedJournalIds =
            draft.parseSelectedJournalIds()

        return EditorDraft(
            id = runCatching { Uuid.parse(draft.id) }.getOrDefault(Uuid.random()),
            blocks = blocks,
            selectedJournalIds = selectedJournalIds,
            createdAt = draft.createdAt,
            lastModifiedAt = draft.updatedAt,
        )
    }

    private fun ExportNote.toJournalNote(
        id: Uuid,
        mediaResolution: MediaResolution,
    ): JournalNote? {
        val location = location?.toNoteLocation()
        val resolvedMediaRef =
            mediaResolution.uri ?: mediaPath?.takeIf { mediaResolution.allowSourceFallback && it.isNotBlank() }
        return when (type.lowercase()) {
            "text" ->
                JournalNote.Text(
                    uid = id,
                    creationTimestamp = createdAt,
                    lastUpdated = updatedAt,
                    content = content.orEmpty(),
                    syncVersion = syncVersion,
                    location = location,
                )
            "image" ->
                JournalNote.Image(
                    uid = id,
                    creationTimestamp = createdAt,
                    lastUpdated = updatedAt,
                    mediaRef = resolvedMediaRef ?: return null,
                    caption = caption.orEmpty(),
                    syncVersion = syncVersion,
                    location = location,
                )
            "video" ->
                JournalNote.Video(
                    uid = id,
                    creationTimestamp = createdAt,
                    lastUpdated = updatedAt,
                    mediaRef = resolvedMediaRef ?: return null,
                    caption = caption.orEmpty(),
                    syncVersion = syncVersion,
                    location = location,
                )
            "audio" ->
                JournalNote.Audio(
                    uid = id,
                    creationTimestamp = createdAt,
                    lastUpdated = updatedAt,
                    mediaRef = resolvedMediaRef ?: return null,
                    durationMs = this.durationMs ?: 0,
                    syncVersion = syncVersion,
                    location = location,
                )
            else -> null
        }
    }

    private suspend fun restoreDraftBlock(
        block: SerializableEntryBlock,
        manifestIndex: Map<String, ExportMediaFile>,
        mediaImporter: MediaImporter?,
        onMediaImported: (Boolean) -> Unit,
    ): SerializableEntryBlock? =
        when (block) {
            is SerializableTextBlock -> block
            is SerializableImageBlock -> {
                val resolution = resolveMediaReference(block.uri, manifestIndex, mediaImporter)
                onMediaImported(resolution.imported)
                block.copy(uri = resolution.uri)
            }
            is SerializableVideoBlock -> {
                val mediaResolution = resolveMediaReference(block.uri, manifestIndex, mediaImporter)
                onMediaImported(mediaResolution.imported)
                val thumbnailResolution = resolveMediaReference(block.thumbnailUri, manifestIndex, mediaImporter)
                onMediaImported(thumbnailResolution.imported)
                block.copy(
                    uri = mediaResolution.uri,
                    thumbnailUri = thumbnailResolution.uri,
                )
            }
            is SerializableAudioBlock -> {
                val resolution = resolveMediaReference(block.uri, manifestIndex, mediaImporter)
                onMediaImported(resolution.imported)
                block.copy(uri = resolution.uri)
            }
            is SerializableCameraBlock -> {
                val resolution = resolveMediaReference(block.uri, manifestIndex, mediaImporter)
                onMediaImported(resolution.imported)
                block.copy(uri = resolution.uri)
            }
        }

    private fun ExportDraft.parseSelectedJournalIds(): List<Uuid> =
        if (journalIds.isNotEmpty()) {
            journalIds.mapNotNull { runCatching { Uuid.parse(it) }.getOrNull() }
        } else {
            journalId?.let { value ->
                runCatching { listOf(Uuid.parse(value)) }.getOrNull() ?: emptyList()
            } ?: emptyList()
        }

    private fun app.logdate.client.domain.export.ExportLocation.toNoteLocation(): NoteLocation {
        val coords =
            NoteCoordinates(
                latitude = latitude,
                longitude = longitude,
                altitude = altitude,
                accuracy = accuracy,
            )
        val place =
            placeName?.let {
                NotePlace(
                    id = Uuid.random(),
                    name = it,
                    latitude = latitude,
                    longitude = longitude,
                )
            }
        return NoteLocation(coordinates = coords, place = place)
    }
}

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

private data class MediaResolution(
    val uri: String?,
    val imported: Boolean,
    val allowSourceFallback: Boolean,
)

@Serializable
private data class JournalsPayload(
    val journals: List<Journal>,
)

@Serializable
private data class NotesPayload(
    val notes: List<ExportNote>,
)

@Serializable
private data class DraftsPayload(
    val drafts: List<ExportDraft>,
)

@Serializable
private data class JournalNotesPayload(
    @SerialName("journal_notes")
    val journalNotes: List<ExportJournalNoteRelation>,
)

private fun ExportPlace.toPlace(): Place.UserDefined =
    Place.UserDefined(
        id = Uuid.parse(id),
        displayName = displayName,
        lat = latitude,
        lng = longitude,
        radiusMeters = radiusMeters,
        description = description,
    )

private fun ExportLocationHistoryItem.toLocationLogRecord(): LocationLogRecord =
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
