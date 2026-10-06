package app.logdate.client.sync.cloud

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.shared.model.PhotoPresentation
import app.logdate.shared.model.sync.VersionConstraint
import kotlinx.serialization.json.Json
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Data source for syncing journal content (notes) with LogDate Cloud.
 *
 * Handles uploading, downloading, updating, and deleting journal notes
 * across all types: Text, Image, Video, and Audio.
 */
interface CloudContentDataSource {
    /**
     * Uploads a new note to the cloud.
     */
    suspend fun uploadNote(
        accessToken: String,
        note: JournalNote,
    ): Result<SyncUploadResult>

    /**
     * Updates an existing note in the cloud.
     */
    suspend fun updateNote(
        accessToken: String,
        note: JournalNote,
    ): Result<SyncUploadResult>

    /**
     * Deletes a note from the cloud.
     */
    suspend fun deleteNote(
        accessToken: String,
        noteId: Uuid,
    ): Result<Unit>

    /**
     * Downloads all note changes since the specified timestamp.
     */
    suspend fun getContentChanges(
        accessToken: String,
        since: Instant,
        limit: Int? = null,
    ): Result<ContentSyncResult>
}

/**
 * Result of a content sync operation containing changes and deletions.
 */
data class ContentSyncResult(
    val changes: List<JournalNote>,
    val deletions: List<Uuid>,
    val lastSyncTimestamp: Instant,
    val hasMore: Boolean = false,
    /** Records on this page this device cannot read. See [UnreadablePayloadException]. */
    val unreadable: List<Uuid> = emptyList(),
    val failures: List<RemoteRecordFailure> = emptyList(),
    val unreadableVersions: Map<Uuid, Long> = emptyMap(),
)

/**
 * Default implementation of CloudContentDataSource using the CloudApiClient.
 */
class DefaultCloudContentDataSource(
    private val cloudApiClient: CloudApiClient,
    private val syncPayloadCipher: SyncPayloadCipher? = null,
) : CloudContentDataSource {
    override suspend fun uploadNote(
        accessToken: String,
        note: JournalNote,
    ): Result<SyncUploadResult> {
        val request =
            try {
                note.toUploadRequest()
            } catch (error: Exception) {
                return Result.failure(error)
            }
        return cloudApiClient.uploadContent(accessToken, request).map {
            SyncUploadResult(
                serverVersion = it.serverVersion,
                syncedAt = Instant.fromEpochMilliseconds(it.uploadedAt),
            )
        }
    }

    override suspend fun updateNote(
        accessToken: String,
        note: JournalNote,
    ): Result<SyncUploadResult> {
        val request =
            try {
                note.toUpdateRequest()
            } catch (error: Exception) {
                return Result.failure(error)
            }
        return cloudApiClient.updateContent(accessToken, note.uid.toString(), request).map {
            SyncUploadResult(
                serverVersion = it.serverVersion,
                syncedAt = Instant.fromEpochMilliseconds(it.updatedAt),
            )
        }
    }

    override suspend fun deleteNote(
        accessToken: String,
        noteId: Uuid,
    ): Result<Unit> = cloudApiClient.deleteContent(accessToken, noteId.toString())

    override suspend fun getContentChanges(
        accessToken: String,
        since: Instant,
        limit: Int?,
    ): Result<ContentSyncResult> =
        cloudApiClient.getContentChanges(accessToken, since.toEpochMilliseconds(), limit).mapRecordPage { response ->
            response.toContentSyncResult()
        }

    private suspend fun JournalNote.toUploadRequest(): ContentUploadRequest =
        ContentUploadRequest(
            id = uid.toString(),
            type =
                when (this) {
                    is JournalNote.Text -> "TEXT"
                    is JournalNote.Image -> "IMAGE"
                    is JournalNote.Video -> "VIDEO"
                    is JournalNote.Audio -> "AUDIO"
                },
            content =
                when (this) {
                    is JournalNote.Text -> encryptNoteText(uid, content)
                    else -> null
                },
            mediaUri =
                when (this) {
                    is JournalNote.Image -> mediaRef
                    is JournalNote.Video -> mediaRef
                    is JournalNote.Audio -> mediaRef
                    else -> null
                },
            durationMs =
                when (this) {
                    is JournalNote.Audio -> durationMs
                    else -> 0
                },
            createdAt = creationTimestamp.toEpochMilliseconds(),
            lastUpdated = lastUpdated.toEpochMilliseconds(),
            syncVersion = syncVersion,
            caption =
                when (this) {
                    is JournalNote.Image -> caption.takeIf { it.isNotBlank() }?.let { encryptNoteCaption(uid, it) }
                    is JournalNote.Video -> caption.takeIf { it.isNotBlank() }?.let { encryptNoteCaption(uid, it) }
                    else -> null
                },
            photoPresentation = (this as? JournalNote.Image)?.presentation?.name,
            location = encryptNoteLocation(uid, location),
        )

    private suspend fun JournalNote.toUpdateRequest(): ContentUpdateRequest =
        ContentUpdateRequest(
            content =
                when (this) {
                    is JournalNote.Text -> encryptNoteText(uid, content)
                    else -> null
                },
            mediaUri =
                when (this) {
                    is JournalNote.Image -> mediaRef
                    is JournalNote.Video -> mediaRef
                    is JournalNote.Audio -> mediaRef
                    else -> null
                },
            durationMs =
                when (this) {
                    is JournalNote.Audio -> durationMs
                    else -> 0
                },
            lastUpdated = lastUpdated.toEpochMilliseconds(),
            syncVersion = syncVersion,
            versionConstraint =
                if (syncVersion > 0) {
                    VersionConstraint.Known(syncVersion)
                } else {
                    VersionConstraint.None
                },
            caption =
                when (this) {
                    is JournalNote.Image -> encryptNoteCaption(uid, caption)
                    is JournalNote.Video -> encryptNoteCaption(uid, caption)
                    else -> null
                },
            photoPresentation = (this as? JournalNote.Image)?.presentation?.name,
            location = encryptNoteLocation(uid, location),
        )

    private suspend fun ContentChangesResponse.toContentSyncResult(): ContentSyncResult {
        val records = changes.readEach(idOf = { it.id }, versionOf = { it.serverVersion }) { it.toJournalNote() }
        val removed = deletions.readEach(idOf = { it.id }, versionOf = { it.serverVersion }) { Uuid.parse(it.id) }
        return ContentSyncResult(
            changes = records.readable,
            deletions = removed.readable,
            lastSyncTimestamp = Instant.fromEpochMilliseconds(lastTimestamp),
            hasMore = hasMore,
            unreadable = records.unreadable,
            unreadableVersions = records.unreadableVersions,
            failures = records.failures + removed.failures,
        )
    }

    private suspend fun ContentChange.toJournalNote(): JournalNote {
        val uid = Uuid.parse(id)
        val creationTimestamp = Instant.fromEpochMilliseconds(createdAt)
        val lastUpdated = Instant.fromEpochMilliseconds(lastUpdated)

        return when (type) {
            "TEXT" ->
                JournalNote.Text(
                    uid = uid,
                    creationTimestamp = creationTimestamp,
                    lastUpdated = lastUpdated,
                    content = decryptNoteText(uid, content ?: ""),
                    location = decryptNoteLocation(uid, location),
                    syncVersion = serverVersion,
                )
            "IMAGE" ->
                JournalNote.Image(
                    uid = uid,
                    creationTimestamp = creationTimestamp,
                    lastUpdated = lastUpdated,
                    mediaRef = mediaUri ?: "",
                    caption = decryptNoteCaption(uid, caption.orEmpty()),
                    presentation = PhotoPresentation.entries.firstOrNull { it.name == photoPresentation } ?: PhotoPresentation.EdgeToEdge,
                    location = decryptNoteLocation(uid, location),
                    syncVersion = serverVersion,
                )
            "VIDEO" ->
                JournalNote.Video(
                    uid = uid,
                    creationTimestamp = creationTimestamp,
                    lastUpdated = lastUpdated,
                    mediaRef = mediaUri ?: "",
                    caption = decryptNoteCaption(uid, caption.orEmpty()),
                    location = decryptNoteLocation(uid, location),
                    syncVersion = serverVersion,
                )
            "AUDIO" ->
                JournalNote.Audio(
                    uid = uid,
                    creationTimestamp = creationTimestamp,
                    lastUpdated = lastUpdated,
                    mediaRef = mediaUri ?: "",
                    durationMs = durationMs,
                    location = decryptNoteLocation(uid, location),
                    syncVersion = serverVersion,
                )
            else -> throw UnsupportedRemoteFormatException()
        }
    }

    private suspend fun encryptNoteText(
        noteId: Uuid,
        content: String,
    ): String = syncPayloadCipher?.encryptString(noteTextFieldId(noteId), content) ?: content

    private suspend fun decryptNoteText(
        noteId: Uuid,
        content: String,
    ): String = syncPayloadCipher?.decryptString(noteTextFieldId(noteId), content) ?: content

    private suspend fun encryptNoteCaption(
        noteId: Uuid,
        caption: String,
    ): String = syncPayloadCipher?.encryptString(noteCaptionFieldId(noteId), caption) ?: caption

    private suspend fun decryptNoteCaption(
        noteId: Uuid,
        caption: String,
    ): String = syncPayloadCipher?.decryptString(noteCaptionFieldId(noteId), caption) ?: caption

    private fun noteCaptionFieldId(noteId: Uuid): String = "sync:note:$noteId:caption"

    private val locationJson = Json { ignoreUnknownKeys = true }

    private fun noteTextFieldId(noteId: Uuid): String = "sync:note:$noteId:text"

    /**
     * Encrypts a note's location before upload.
     *
     * Location gets the same treatment as note text rather than travelling in the clear: the
     * server would otherwise hold a precise location history for every entry.
     */
    private suspend fun encryptNoteLocation(
        noteId: Uuid,
        location: NoteLocation?,
    ): String? {
        if (location == null || !location.hasLocation) return null
        val plaintext = locationJson.encodeToString(NoteLocation.serializer(), location)
        return syncPayloadCipher?.encryptString(noteLocationFieldId(noteId), plaintext) ?: plaintext
    }

    private suspend fun decryptNoteLocation(
        noteId: Uuid,
        payload: String?,
    ): NoteLocation? {
        if (payload.isNullOrBlank()) return null
        val plaintext = syncPayloadCipher?.decryptString(noteLocationFieldId(noteId), payload) ?: payload
        return locationJson.decodeFromString(NoteLocation.serializer(), plaintext)
    }

    private fun noteLocationFieldId(noteId: Uuid): String = "sync:note:$noteId:location"
}
