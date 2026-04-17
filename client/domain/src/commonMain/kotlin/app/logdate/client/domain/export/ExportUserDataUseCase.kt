package app.logdate.client.domain.export

import app.logdate.client.device.AppInfo
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
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

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
    companion object {
        private val EXTENSION_PATTERN = Regex("\\.[a-zA-Z0-9]+$")
    }

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
        dateRangeCutoff: Instant? = null,
    ): Flow<ExportProgress> =
        flow {
            emit(ExportProgress.Starting)

            try {
                val issues = mutableListOf<ExportIssue>()

                emit(ExportProgress.InProgress(0.1f, ExportStage.COLLECTING_JOURNALS))
                val journals =
                    if (includeJournals) {
                        runCatching { journalRepository.allJournalsObserved.first() }
                            .onFailure { issues.record(ExportIssueCode.JOURNALS_UNAVAILABLE, it) }
                            .getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }

                emit(ExportProgress.InProgress(0.3f, ExportStage.COLLECTING_NOTES))
                val notes =
                    if (includeNotes) {
                        val allNotes =
                            runCatching { journalNotesRepository.allNotesObserved.first() }
                                .onFailure { issues.record(ExportIssueCode.NOTES_UNAVAILABLE, it) }
                                .getOrDefault(emptyList())
                        if (dateRangeCutoff != null) {
                            allNotes.filter { it.creationTimestamp >= dateRangeCutoff }
                        } else {
                            allNotes
                        }
                    } else {
                        emptyList()
                    }

                emit(ExportProgress.InProgress(0.5f, ExportStage.COLLECTING_DRAFTS))
                val drafts =
                    if (includeDrafts) {
                        val allDrafts =
                            runCatching { journalRepository.getAllDrafts() }
                                .onFailure { issues.record(ExportIssueCode.DRAFTS_UNAVAILABLE, it) }
                                .getOrDefault(emptyList())
                        if (dateRangeCutoff != null) {
                            allDrafts.filter { it.createdAt >= dateRangeCutoff }
                        } else {
                            allDrafts
                        }
                    } else {
                        emptyList()
                    }

                val appInfo =
                    runCatching { appInfoProvider.getAppInfo() }
                        .onFailure { issues.record(ExportIssueCode.APP_INFO_UNAVAILABLE, it) }
                        .getOrDefault(
                            AppInfo(
                                versionName = "unknown",
                                versionCode = 0,
                                packageName = "unknown",
                            ),
                        )
                val deviceId =
                    runCatching { deviceIdProvider.getDeviceId().value.toString() }
                        .onFailure { issues.record(ExportIssueCode.DEVICE_ID_UNAVAILABLE, it) }
                        .getOrDefault("unknown-device")
                val userId =
                    runCatching {
                        userStateRepository
                            .userData
                            .first()
                            .displayName
                            .trim()
                            .ifBlank { "local-user" }
                    }.onFailure { issues.record(ExportIssueCode.USER_ID_UNAVAILABLE, it) }
                        .getOrDefault("local-user")
                val profile =
                    runCatching { profileRepository.getCurrentProfile() }
                        .onFailure { issues.record(ExportIssueCode.PROFILE_UNAVAILABLE, it) }
                        .getOrDefault(LogDateProfile())
                val places =
                    runCatching { userPlacesRepository.getAllPlaces() }
                        .onFailure { issues.record(ExportIssueCode.PLACES_UNAVAILABLE, it) }
                        .getOrDefault(emptyList())
                val locationHistory =
                    runCatching {
                        locationHistoryRepository
                            .getAllLocationHistory()
                            .filter { item -> dateRangeCutoff == null || item.timestamp >= dateRangeCutoff }
                    }.onFailure { issues.record(ExportIssueCode.LOCATION_HISTORY_UNAVAILABLE, it) }
                        .getOrDefault(emptyList())

                emit(ExportProgress.InProgress(0.7f, ExportStage.PREPARING_DATA))

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
                        runCatching {
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
                        }.onFailure { issues.record(ExportIssueCode.ASSOCIATIONS_UNAVAILABLE, it) }
                            .getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }

                val mediaFiles =
                    if (includeMedia) {
                        runCatching { getMediaFilesToExport(exportNotes, exportDrafts) }
                            .onFailure { issues.record(ExportIssueCode.MEDIA_MANIFEST_UNAVAILABLE, it) }
                            .getOrDefault(emptyList())
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

                emit(
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
                            issues = issues.toList(),
                        ),
                    ),
                )
            } catch (exception: Exception) {
                Napier.e("Export failed", exception)
                emit(ExportProgress.Failed(ExportError.UNKNOWN))
            }
        }

    private fun createMediaPath(
        uri: String,
        seed: String,
        timestamp: Instant,
        noteType: String? = null,
    ): String {
        val rawFileName =
            uri
                .substringAfterLast("/")
                .substringBefore("?")

        val extension =
            EXTENSION_PATTERN
                .find(rawFileName)
                ?.value
                ?.substring(1)
                ?.lowercase()
                ?: run {
                    // Content URIs (e.g. content://media/external/images/media/1000025292)
                    // carry no extension in the last path segment. Infer from the URI
                    // path structure first, then fall back to the note type.
                    val inferred = inferExtensionFromUri(uri, noteType)
                    Napier.w(
                        "No extension found in URI: $uri — " +
                            if (inferred != null) {
                                "using .$inferred inferred from content type"
                            } else {
                                "falling back to .bin; MIME type will be detected on import"
                            },
                    )
                    inferred ?: "bin"
                }

        return "${ExportFileStructure.MEDIA_FOLDER}/$seed.$extension"
    }

    /**
     * Infers a file extension for URIs that carry no extension in their path segment.
     *
     * Bare MediaStore content URIs (e.g. `content://media/external/images/media/123`) have no
     * extension in the last path segment. The URI collection name (/images/, /video/, /audio/)
     * is checked first because it is more reliable than [noteType], which comes from the
     * serialized export model and could be stale.
     */
    private fun inferExtensionFromUri(
        uri: String,
        noteType: String?,
    ): String? {
        val lower = uri.lowercase()
        return when {
            "/images/" in lower -> "jpg"
            "/video/" in lower -> "mp4"
            "/audio/" in lower -> "m4a"
            noteType == "image" -> "jpg"
            noteType == "video" -> "mp4"
            noteType == "audio" -> "m4a"
            else -> null
        }
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
                    exportPath = createMediaPath(mediaPath, note.id, note.createdAt, note.type),
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
        val stage: ExportStage,
    ) : ExportProgress()

    data class Completed(
        val result: ExportResult,
    ) : ExportProgress()

    data class Failed(
        val error: ExportError,
    ) : ExportProgress()
}

/**
 * Typed export progress stages. The presentation layer resolves
 * these to localized strings.
 */
enum class ExportStage {
    COLLECTING_JOURNALS,
    COLLECTING_NOTES,
    COLLECTING_DRAFTS,
    PREPARING_DATA,
    WRITING_ARCHIVE,
}

/**
 * Typed export errors. The presentation layer resolves these
 * to localized user-facing messages.
 */
enum class ExportError {
    UNKNOWN,
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
    internal val issues: List<ExportIssue>,
) {
    fun serializeMetadata(): String = json.encodeToString(exportMetadata)

    fun serializeJournals(): String = json.encodeToString(mapOf("journals" to journals))

    fun serializeNotes(): String = json.encodeToString(mapOf("notes" to exportNotes))

    fun serializeJournalNotes(): String = json.encodeToString(mapOf("journal_notes" to exportRelations))

    fun serializeDrafts(): String = json.encodeToString(mapOf("drafts" to exportDrafts))

    fun serializeProfile(): String? = profilePayload?.let { json.encodeToString(it) }

    fun serializePlaces(): String? = placesPayload?.let { json.encodeToString(it) }

    fun serializeLocationHistory(): String? = locationHistoryPayload?.let { json.encodeToString(it) }

    fun serializeMediaManifest(files: List<ExportMediaFile> = mediaFiles): String? =
        files.takeIf { it.isNotEmpty() }?.let { json.encodeToString(ExportMediaManifest(it)) }

    fun renderIssuesText(additionalIssues: List<ExportIssue> = emptyList()): String? {
        val allIssues = (issues + additionalIssues).distinct()
        if (allIssues.isEmpty()) return null

        return buildString {
            appendLine("LogDate export issues")
            appendLine()
            appendLine("This export completed, but some recoverable problems were detected.")
            appendLine("Affected content may need to be re-exported after the source files or metadata are repaired.")
            appendLine()
            appendLine("Issues:")
            allIssues.forEach { issue ->
                appendLine("- ${issue.describe()}")
            }
            appendLine()
            appendLine("Possible mitigations:")
            appendLine("- Keep this archive; unaffected data was still exported.")
            appendLine("- Re-open the affected entries in LogDate and verify the media still loads.")
            appendLine("- If media is missing, reattach the original image, video, or recording and export again.")
            appendLine(
                "- If the app is referencing an old private file path, saving or re-importing the media can refresh the stored reference.",
            )
        }
    }
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

enum class ExportIssueCode {
    JOURNALS_UNAVAILABLE,
    NOTES_UNAVAILABLE,
    DRAFTS_UNAVAILABLE,
    APP_INFO_UNAVAILABLE,
    DEVICE_ID_UNAVAILABLE,
    USER_ID_UNAVAILABLE,
    PROFILE_UNAVAILABLE,
    PLACES_UNAVAILABLE,
    LOCATION_HISTORY_UNAVAILABLE,
    ASSOCIATIONS_UNAVAILABLE,
    MEDIA_MANIFEST_UNAVAILABLE,
    MEDIA_RECOVERED_NORMALIZED_PATH,
    MEDIA_RECOVERED_APP_PRIVATE_AUDIO,
    MEDIA_RECOVERED_APP_PRIVATE_MEDIA,
    MEDIA_RECOVERED_MEDIA_STORE,
    MEDIA_BYTES_MISSING,
}

data class ExportIssue(
    val code: ExportIssueCode,
    val source: String? = null,
    val detail: String? = null,
)

private fun MutableList<ExportIssue>.record(
    code: ExportIssueCode,
    throwable: Throwable,
    source: String? = null,
) {
    val detail = throwable.message ?: throwable::class.simpleName ?: "unknown error"
    add(ExportIssue(code = code, source = source, detail = detail))
    Napier.w("Export issue recorded: ${code.name}", throwable)
}

private fun ExportIssue.describe(): String =
    when (code) {
        ExportIssueCode.JOURNALS_UNAVAILABLE ->
            "Journals could not be loaded. The export used an empty journals section."
        ExportIssueCode.NOTES_UNAVAILABLE ->
            "Notes could not be loaded. The export used an empty notes section."
        ExportIssueCode.DRAFTS_UNAVAILABLE ->
            "Drafts could not be loaded. The export used an empty drafts section."
        ExportIssueCode.APP_INFO_UNAVAILABLE ->
            "App version metadata could not be read. Default export metadata was written instead."
        ExportIssueCode.DEVICE_ID_UNAVAILABLE ->
            "Device ID metadata could not be read. A fallback device identifier was written instead."
        ExportIssueCode.USER_ID_UNAVAILABLE ->
            "User identity metadata could not be read. A fallback user identifier was written instead."
        ExportIssueCode.PROFILE_UNAVAILABLE ->
            "Profile data could not be loaded. The export omitted the profile section."
        ExportIssueCode.PLACES_UNAVAILABLE ->
            "Places could not be loaded. The export omitted the places section."
        ExportIssueCode.LOCATION_HISTORY_UNAVAILABLE ->
            "Location history could not be loaded. The export omitted the location history section."
        ExportIssueCode.ASSOCIATIONS_UNAVAILABLE ->
            "Journal associations could not be loaded. The export wrote an empty journal_notes section."
        ExportIssueCode.MEDIA_MANIFEST_UNAVAILABLE ->
            "Media manifest preparation failed. Media entries may be incomplete."
        ExportIssueCode.MEDIA_RECOVERED_NORMALIZED_PATH ->
            "Recovered media by normalizing a stale file path${sourceSuffix(source)}"
        ExportIssueCode.MEDIA_RECOVERED_APP_PRIVATE_AUDIO ->
            "Recovered audio from app-private recording storage${sourceSuffix(source)}"
        ExportIssueCode.MEDIA_RECOVERED_APP_PRIVATE_MEDIA ->
            "Recovered media from app-private storage${sourceSuffix(source)}"
        ExportIssueCode.MEDIA_RECOVERED_MEDIA_STORE ->
            "Recovered media from MediaStore${sourceSuffix(source)}"
        ExportIssueCode.MEDIA_BYTES_MISSING ->
            "Media bytes could not be exported${sourceSuffix(source)}"
    }

private fun sourceSuffix(source: String?): String =
    source?.let {
        ": $it"
    } ?: "."
