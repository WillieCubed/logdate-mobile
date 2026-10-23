package app.logdate.client.sync.space.migration

import app.logdate.client.media.MediaFileSource
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.CloudMediaDataSource
import app.logdate.client.sync.cloud.MediaFile
import app.logdate.client.sync.cloud.MediaUploadResult
import app.logdate.client.sync.metadata.MediaSyncRef
import app.logdate.client.sync.metadata.MediaSyncRefStore
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.space.SpaceMediaReference
import app.logdate.shared.model.sync.SpaceMigrationErrorCodes
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** The key a moved item's media file is saved under: a note's file, a draft block's, or a journal's cover. */
internal fun noteMediaKey(noteId: Uuid) = "note:$noteId"

internal fun assetMediaKey(
    ownerId: Uuid,
    blockId: Uuid,
    kind: String,
) = "draft:$ownerId:$blockId:$kind"

/**
 * The media references the move works with: the legacy ones the sync engine saved, read but never
 * changed, and the personal-space ones the move makes, saved in [checkpoints] until the switch.
 * Writing the new ones to the real store before the switch would send space references to legacy
 * storage, which other devices read.
 *
 * Each legacy reference read is remembered under the file's content id, so [MigrationMediaSource]
 * can ask the server to copy that file instead of uploading it again.
 */
internal class MigrationMediaRefs(
    private val legacy: MediaSyncRefStore,
    private val checkpoints: SpaceMigrationCheckpoints,
    private val scope: UploadScope,
) : MediaSyncRefStore {
    private val legacyByContent = mutableMapOf<String, MediaSyncRef>()
    private val uploadFromDevice = mutableSetOf<String>()
    private val moved = mutableMapOf<String, MigratedMedia>()

    /** Every media file this run gave a personal-space reference, by key. */
    fun movedMedia(): Map<String, MigratedMedia> = moved.toMap()

    /** Marks the file under [key] to be uploaded from this device, because its copy didn't match. */
    suspend fun markForUpload(key: String) {
        val previous = moved.remove(key) ?: checkpoints.media(scope, key) ?: return
        checkpoints.saveMedia(scope, key, previous.copy(spaceReference = "", uploadFromDevice = true))
    }

    /** The legacy reference last read for the file of [contentId], or null when there is none. */
    fun legacyFor(contentId: Uuid): MediaSyncRef? = legacyByContent[contentId.toString()]

    /** Remembers [ref] as the legacy copy of the file of [contentId], found some other way than reading it here. */
    fun rememberLegacy(
        contentId: Uuid,
        ref: MediaSyncRef,
    ) {
        legacyByContent[contentId.toString()] = ref
    }

    /** Whether the file of [contentId] must be uploaded from this device, because the copied one didn't match. */
    fun mustUpload(contentId: Uuid): Boolean = contentId.toString() in uploadFromDevice

    override suspend fun get(noteId: Uuid): MediaSyncRef? = read(noteMediaKey(noteId), noteId, legacy.get(noteId))

    override suspend fun getDraftAsset(
        draftId: Uuid,
        blockId: Uuid,
        kind: String,
    ): MediaSyncRef? = read(assetMediaKey(draftId, blockId, kind), blockId, legacy.getDraftAsset(draftId, blockId, kind))

    override suspend fun upsert(ref: MediaSyncRef) = save(noteMediaKey(Uuid.parse(ref.noteId)), ref)

    override suspend fun upsertDraftAsset(
        draftId: Uuid,
        blockId: Uuid,
        kind: String,
        ref: MediaSyncRef,
    ) = save(assetMediaKey(draftId, blockId, kind), ref)

    override suspend fun delete(noteId: Uuid) = Unit

    private suspend fun read(
        key: String,
        contentId: Uuid,
        legacyRef: MediaSyncRef?,
    ): MediaSyncRef? {
        legacyRef?.let { legacyByContent[contentId.toString()] = it }
        val saved = checkpoints.media(scope, key) ?: return null
        if (saved.uploadFromDevice) uploadFromDevice += contentId.toString()
        if (saved.spaceReference.isBlank()) return null
        moved[key] = saved
        return MediaSyncRef(
            noteId = contentId.toString(),
            localUri = saved.localUri.orEmpty(),
            remoteUrl = saved.spaceReference,
            mediaId = SpaceMediaReference.parse(saved.spaceReference)?.cid.orEmpty(),
            updatedAt = 0,
            serverOrigin = scope.serverOrigin,
            ownerId = scope.ownerId,
        )
    }

    private suspend fun save(
        key: String,
        ref: MediaSyncRef,
    ) {
        val previous = legacyByContent[ref.noteId]
        val saved =
            MigratedMedia(
                spaceReference = ref.remoteUrl,
                legacyRemoteUrl = previous?.remoteUrl?.takeIf { SpaceMediaReference.parse(it) == null },
                legacyMediaId = previous?.mediaId?.takeIf { SpaceMediaReference.parse(previous.remoteUrl) == null },
                localUri = ref.localUri,
            )
        checkpoints.saveMedia(scope, key, saved)
        moved[key] = saved
    }
}

/**
 * Puts a moved item's media file into the personal space. A file the apps already encrypted is
 * copied by the server from its legacy copy, so it isn't uploaded again; a file the server encrypted
 * itself, or one with no legacy copy, is encrypted on this device and uploaded.
 */
internal class MigrationMediaSource(
    private val api: SpaceMigrationApi,
    private val migrationId: String,
    private val refs: MigrationMediaRefs,
    private val space: CloudMediaDataSource,
) : CloudMediaDataSource {
    override suspend fun uploadMedia(
        accessToken: String,
        contentId: Uuid,
        media: MediaFileSource,
    ): Result<MediaUploadResult> {
        val legacyId = refs.legacyFor(contentId)?.let(::legacyMediaIdOf)?.takeUnless { refs.mustUpload(contentId) }
        if (legacyId != null) {
            try {
                val copied = api.registerMedia(accessToken, migrationId, legacyId)
                val reference = SpaceMediaReference(copied.cid, contentId, media.mimeType, copied.size, media.sizeBytes)
                return Result.success(MediaUploadResult(copied.cid, reference.format(), Clock.System.now()))
            } catch (refused: CloudApiException) {
                if (refused.errorCode !in UPLOAD_INSTEAD) return Result.failure(refused)
            }
        }
        return space.uploadMedia(accessToken, contentId, media)
    }

    override suspend fun downloadMedia(
        accessToken: String,
        mediaId: String,
    ): Result<MediaFile> = Result.failure(UnsupportedOperationException("The move only uploads media"))

    private companion object {
        /** Answers that mean the file has to be uploaded from this device instead of copied. */
        val UPLOAD_INSTEAD = setOf(SpaceMigrationErrorCodes.MEDIA_NOT_CLIENT_ENCRYPTED, "MEDIA_NOT_FOUND")
    }
}

/** The legacy media id a reference names: its own id, or the segment after `media` in its URL. */
internal fun legacyMediaIdOf(ref: MediaSyncRef): String? {
    if (SpaceMediaReference.parse(ref.remoteUrl) != null) return null
    ref.mediaId.takeIf { it.isNotBlank() }?.let { return it }
    return legacyMediaIdOfUrl(ref.remoteUrl)
}

internal fun legacyMediaIdOfUrl(url: String): String? {
    if (SpaceMediaReference.parse(url) != null) return null
    val segments =
        url
            .substringBefore('#')
            .substringBefore('?')
            .trim()
            .split('/')
    val index = segments.indexOfLast { it == "media" }
    return segments.getOrNull(index + 1)?.takeIf { index >= 0 && it.isNotBlank() }
}
