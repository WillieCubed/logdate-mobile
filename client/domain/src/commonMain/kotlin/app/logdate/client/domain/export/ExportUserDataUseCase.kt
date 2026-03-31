package app.logdate.client.domain.export

import app.logdate.client.device.AppInfoProvider
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.client.repository.places.UserPlacesRepository
import app.logdate.client.repository.profile.ProfileRepository
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import app.logdate.shared.model.Place
import app.logdate.shared.model.SerializableAudioBlock
import app.logdate.shared.model.SerializableCameraBlock
import app.logdate.shared.model.SerializableEntryBlock
import app.logdate.shared.model.SerializableImageBlock
import app.logdate.shared.model.SerializableTextBlock
import app.logdate.shared.model.SerializableVideoBlock
import app.logdate.shared.model.profile.LogDateProfile
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Use case for exporting all user data to JSON format according to the LogDate export specification.
 */
class ExportUserDataUseCase(
    private val journalRepository: JournalRepository,
    private val journalNotesRepository: JournalNotesRepository,
    private val profileRepository: ProfileRepository,
    private val userPlacesRepository: UserPlacesRepository,
    private val locationHistoryRepository: LocationHistoryRepository,
    private val userStateRepository: UserStateRepository,
    private val deviceIdProvider: DeviceIdProvider,
    private val appInfoProvider: AppInfoProvider,
) {
    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }

    /**
     * Exports user data as a Flow that emits progress updates.
     *
     * @param includeJournals Whether to include journals in the export
     * @param includeNotes Whether to include notes in the export
     * @param includeDrafts Whether to include drafts in the export
     * @param includeMedia Whether to include media files in the export
     * @param dateRangeCutoff Optional cutoff instant — notes and drafts created before this are excluded.
     *                        Journals are always fully included when [includeJournals] is true.
     * @return Flow of ExportProgress containing current status and completion percentage
     */
    fun exportUserData(
        includeJournals: Boolean = true,
        includeNotes: Boolean = true,
        includeDrafts: Boolean = true,
        includeMedia: Boolean = true,
        dateRangeCutoff: kotlin.time.Instant? = null,
    ): Flow<ExportProgress> =
        channelFlow {
            send(ExportProgress.Starting)

            try {
                send(ExportProgress.InProgress(0.1f, "Collecting data..."))

                val (journals, notes, drafts, profile, places, locationHistory) =
                    coroutineScope {
                        val journalsDeferred =
                            async {
                                if (includeJournals) {
                                    journalRepository.allJournalsObserved.first()
                                } else {
                                    emptyList()
                                }
                            }
                        val notesDeferred =
                            async {
                                if (includeNotes) {
                                    val allNotes = journalNotesRepository.allNotesObserved.first()
                                    if (dateRangeCutoff != null) {
                                        allNotes.filter { it.creationTimestamp >= dateRangeCutoff }
                                    } else {
                                        allNotes
                                    }
                                } else {
                                    emptyList()
                                }
                            }
                        val draftsDeferred =
                            async {
                                if (includeDrafts) {
                                    val allDrafts = journalRepository.getAllDrafts()
                                    if (dateRangeCutoff != null) {
                                        allDrafts.filter { it.createdAt >= dateRangeCutoff }
                                    } else {
                                        allDrafts
                                    }
                                } else {
                                    emptyList()
                                }
                            }
                        val profileDeferred = async { profileRepository.getCurrentProfile() }
                        val placesDeferred = async { userPlacesRepository.getAllPlaces() }
                        val locationHistoryDeferred =
                            async {
                                locationHistoryRepository
                                    .getAllLocationHistory()
                                    .filter { item -> dateRangeCutoff == null || item.timestamp >= dateRangeCutoff }
                            }

                        ExportCollectionResult(
                            journals = journalsDeferred.await(),
                            notes = notesDeferred.await(),
                            drafts = draftsDeferred.await(),
                            profile = profileDeferred.await(),
                            places = placesDeferred.await(),
                            locationHistory = locationHistoryDeferred.await(),
                        )
                    }

                val appInfo = appInfoProvider.getAppInfo()
                val deviceId = deviceIdProvider.getDeviceId().value.toString()
                val userId =
                    userStateRepository
                        .userData
                        .first()
                        .displayName
                        .trim()
                        .ifBlank { "local-user" }

                send(ExportProgress.InProgress(0.7f, "Preparing export data..."))

                val exportNotes =
                    notes.map { note ->
                        when (note) {
                            is JournalNote.Text ->
                                ExportNote(
                                    id = note.uid.toString(),
                                    type = "text",
                                    content = note.content,
                                    createdAt = note.creationTimestamp,
                                    updatedAt = note.lastUpdated,
                                    location = note.location?.toExportLocation(),
                                    syncVersion = note.syncVersion,
                                )
                            is JournalNote.Image ->
                                ExportNote(
                                    id = note.uid.toString(),
                                    type = "image",
                                    mediaPath = note.mediaRef,
                                    caption = note.caption.takeIf { it.isNotEmpty() },
                                    createdAt = note.creationTimestamp,
                                    updatedAt = note.lastUpdated,
                                    location = note.location?.toExportLocation(),
                                    syncVersion = note.syncVersion,
                                )
                            is JournalNote.Audio ->
                                ExportNote(
                                    id = note.uid.toString(),
                                    type = "audio",
                                    mediaPath = note.mediaRef,
                                    durationMs = note.durationMs,
                                    createdAt = note.creationTimestamp,
                                    updatedAt = note.lastUpdated,
                                    location = note.location?.toExportLocation(),
                                    syncVersion = note.syncVersion,
                                )
                            is JournalNote.Video ->
                                ExportNote(
                                    id = note.uid.toString(),
                                    type = "video",
                                    mediaPath = note.mediaRef,
                                    caption = note.caption.takeIf { it.isNotEmpty() },
                                    createdAt = note.creationTimestamp,
                                    updatedAt = note.lastUpdated,
                                    location = note.location?.toExportLocation(),
                                    syncVersion = note.syncVersion,
                                )
                        }
                    }

                val exportDrafts = drafts.map { it.toExportDraft() }
                val exportPlaces = places.mapNotNull { it.toExportPlaceOrNull() }
                val exportLocationHistory = locationHistory.map { it.toExportLocationHistoryItem() }
                val exportRelations =
                    if (includeJournals && includeNotes) {
                        val journalIds = journals.map { it.id }.toSet()
                        val notesByUid = notes.associateBy { it.uid }
                        journalNotesRepository
                            .getAllJournalNoteLinks()
                            .filter { (journalId, noteId) ->
                                journalId in journalIds && noteId in notesByUid
                            }.mapNotNull { (journalId, noteId) ->
                                val note = notesByUid[noteId] ?: return@mapNotNull null
                                ExportJournalNoteRelation(
                                    journalId = journalId.toString(),
                                    noteId = noteId.toString(),
                                    addedAt = note.creationTimestamp,
                                    syncVersion = note.syncVersion,
                                )
                            }
                    } else {
                        emptyList()
                    }

                val mediaFiles =
                    if (includeMedia) {
                        getMediaFilesToExport(exportNotes, exportDrafts)
                    } else {
                        emptyList()
                    }

                val stats =
                    ExportStats(
                        journalCount = journals.size,
                        noteCount = notes.size,
                        draftCount = exportDrafts.size,
                        mediaCount = mediaFiles.size,
                        placeCount = exportPlaces.size,
                        locationHistoryCount = exportLocationHistory.size,
                        hasProfile = profile != LogDateProfile(),
                    )

                val exportMetadata =
                    ExportMetadata(
                        exportDate = Clock.System.now(),
                        userId = userId,
                        deviceId = deviceId,
                        appVersion = appInfo.versionName,
                        stats = stats,
                    )

                send(
                    ExportProgress.Completed(
                        ExportResult(
                            json = json,
                            exportMetadata = exportMetadata,
                            journals = journals,
                            exportNotes = exportNotes,
                            exportRelations = exportRelations,
                            exportDrafts = exportDrafts,
                            profilePayload =
                                profile
                                    .takeIf { it != LogDateProfile() }
                                    ?.let { ProfilePayload(profile = it) },
                            placesPayload =
                                exportPlaces
                                    .takeIf { it.isNotEmpty() }
                                    ?.let { PlacesPayload(places = it) },
                            locationHistoryPayload =
                                exportLocationHistory
                                    .takeIf { it.isNotEmpty() }
                                    ?.let { LocationHistoryPayload(locationHistory = it) },
                            mediaFiles = mediaFiles,
                            stats = stats,
                        ),
                    ),
                )
            } catch (exception: Exception) {
                send(ExportProgress.Failed(exception.message ?: "Unknown error occurred"))
            }
        }

    private fun createMediaPath(
        uri: String,
        seed: String,
        timestamp: kotlin.time.Instant,
    ): String {
        val year = timestamp.toString().substring(0, 4)
        val formattedTimestamp = timestamp.toString().replace(":", "-")
        val rawFileName =
            uri
                .substringAfterLast("/")
                .substringBefore("?")
                .ifBlank { "media" }
        val extension =
            rawFileName
                .substringAfterLast(".", "")
                .takeIf { rawFileName.contains(".") && it.isNotBlank() }
                ?.sanitizePathSegment()
        val baseName = rawFileName.substringBeforeLast(".", rawFileName).sanitizePathSegment().ifBlank { "media" }
        val uniqueSuffix = seed.sanitizePathSegment().ifBlank { uri.stableSuffix() }
        val fileName =
            if (extension != null) {
                "${formattedTimestamp}_${baseName}_$uniqueSuffix.$extension"
            } else {
                "${formattedTimestamp}_${baseName}_$uniqueSuffix"
            }

        return "${ExportFileStructure.MEDIA_FOLDER}/$year/$fileName"
    }

    private fun getMediaFilesToExport(
        notes: List<ExportNote>,
        drafts: List<ExportDraft>,
    ): List<ExportMediaFile> {
        val mediaFilesBySource = linkedMapOf<String, ExportMediaFile>()

        notes.forEach { note ->
            val mediaPath = note.mediaPath ?: return@forEach
            mediaFilesBySource.getOrPut(mediaPath) {
                ExportMediaFile(
                    exportPath = createMediaPath(mediaPath, note.id, note.createdAt),
                    sourceUri = mediaPath,
                )
            }
        }

        drafts.forEach { draft ->
            draft.mediaReferences.forEachIndexed { index, mediaRef ->
                mediaFilesBySource.getOrPut(mediaRef) {
                    ExportMediaFile(
                        exportPath = createMediaPath(mediaRef, "${draft.id}_$index", draft.createdAt),
                        sourceUri = mediaRef,
                    )
                }
            }
        }

        return mediaFilesBySource.values.toList()
    }

    private fun String.sanitizePathSegment(maxLength: Int = 48): String =
        replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
            .take(maxLength)

    private fun String.stableSuffix(): String {
        val hash =
            fold(17) { acc, char ->
                (acc * 31) + char.code
            }
        return hash.toUInt().toString(16)
    }

    private fun NoteLocation.toExportLocation(): ExportLocation? {
        val lat = effectiveLatitude ?: return null
        val lng = effectiveLongitude ?: return null
        return ExportLocation(
            latitude = lat,
            longitude = lng,
            placeName = displayName,
            altitude = coordinates?.altitude,
            accuracy = coordinates?.accuracy,
        )
    }

    private fun SerializableEntryBlock.toMediaReferences(): List<String> =
        when (this) {
            is SerializableImageBlock -> listOfNotNull(uri)
            is SerializableVideoBlock -> listOfNotNull(uri, thumbnailUri)
            is SerializableAudioBlock -> listOfNotNull(uri)
            is SerializableCameraBlock -> listOfNotNull(uri)
            is SerializableTextBlock -> emptyList()
        }

    private fun EditorDraft.toExportDraft(): ExportDraft {
        val location =
            blocks.firstNotNullOfOrNull { block ->
                val latitude = block.locationLat
                val longitude = block.locationLng
                if (latitude != null && longitude != null) {
                    ExportLocation(
                        latitude = latitude,
                        longitude = longitude,
                        altitude = block.altitude,
                    )
                } else {
                    null
                }
            }

        val content =
            blocks
                .filterIsInstance<SerializableTextBlock>()
                .joinToString("\n") { it.content }

        val mediaReferences =
            blocks
                .flatMap { it.toMediaReferences() }
                .distinct()

        return ExportDraft(
            id = id.toString(),
            journalId = selectedJournalIds.firstOrNull()?.toString(),
            journalIds = selectedJournalIds.map { it.toString() },
            content = content,
            createdAt = createdAt,
            updatedAt = lastModifiedAt,
            location = location,
            mediaReferences = mediaReferences,
            blocks = blocks,
        )
    }
}

sealed class ExportProgress {
    data object Starting : ExportProgress()

    data class InProgress(
        val percentage: Float,
        val message: String,
    ) : ExportProgress()

    data class Completed(
        val result: ExportResult,
    ) : ExportProgress()

    data class Failed(
        val reason: String,
    ) : ExportProgress()
}

/**
 * Result of a completed export. Holds domain objects and serializes each
 * category lazily so only one JSON string is in memory at a time.
 */
class ExportResult(
    private val json: Json,
    private val exportMetadata: ExportMetadata,
    private val journals: List<Journal>,
    private val exportNotes: List<ExportNote>,
    private val exportRelations: List<ExportJournalNoteRelation>,
    private val exportDrafts: List<ExportDraft>,
    private val profilePayload: ProfilePayload?,
    private val placesPayload: PlacesPayload?,
    private val locationHistoryPayload: LocationHistoryPayload?,
    val mediaFiles: List<ExportMediaFile>,
    val stats: ExportStats,
) {
    fun serializeMetadata(): String = json.encodeToString(exportMetadata)

    fun serializeJournals(): String = json.encodeToString(mapOf("journals" to journals))

    fun serializeNotes(): String = json.encodeToString(mapOf("notes" to exportNotes))

    fun serializeJournalNotes(): String = json.encodeToString(mapOf("journal_notes" to exportRelations))

    fun serializeDrafts(): String = json.encodeToString(mapOf("drafts" to exportDrafts))

    fun serializeProfile(): String? = profilePayload?.let { json.encodeToString(it) }

    fun serializePlaces(): String? = placesPayload?.let { json.encodeToString(it) }

    fun serializeLocationHistory(): String? = locationHistoryPayload?.let { json.encodeToString(it) }

    fun serializeMediaManifest(): String? = mediaFiles.takeIf { it.isNotEmpty() }?.let { json.encodeToString(ExportMediaManifest(it)) }
}

/**
 * Represents a media file to be included in the export.
 */
@Serializable
data class ExportMediaFile(
    val exportPath: String,
    val sourceUri: String,
)

private fun Place.toExportPlaceOrNull(): ExportPlace? =
    when (this) {
        is Place.UserDefined ->
            ExportPlace(
                id = id.toString(),
                displayName = displayName,
                latitude = lat,
                longitude = lng,
                radiusMeters = radiusMeters,
                description = description,
            )
    }

private fun LocationHistoryItem.toExportLocationHistoryItem(): ExportLocationHistoryItem =
    ExportLocationHistoryItem(
        sampleId = sampleId,
        userId = userId,
        deviceId = deviceId,
        timestamp = timestamp,
        loggedAt = loggedAt,
        latitude = location.latitude,
        longitude = location.longitude,
        altitudeMeters = location.altitude.value,
        confidence = confidence,
        isGenuine = isGenuine,
        capturePipeline = capturePipeline.name,
        captureSource = captureSource.name,
        accuracyMeters = accuracyMeters,
        speedMetersPerSecond = speedMetersPerSecond,
        bearingDegrees = bearingDegrees,
        isMock = isMock,
    )

private data class ExportCollectionResult(
    val journals: List<Journal>,
    val notes: List<JournalNote>,
    val drafts: List<EditorDraft>,
    val profile: LogDateProfile,
    val places: List<Place>,
    val locationHistory: List<LocationHistoryItem>,
)
