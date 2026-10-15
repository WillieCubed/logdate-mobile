package app.logdate.client.sync

import app.logdate.client.device.crypto.IdentityKeyNotFoundException
import app.logdate.client.media.MediaFileSource
import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaPayload
import app.logdate.client.media.storage.StoredMediaReferences
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.mediaRefOrNull
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.CloudMediaDataSource
import app.logdate.client.sync.cloud.MediaReadException
import app.logdate.client.sync.metadata.MediaSyncRef
import app.logdate.client.sync.metadata.MediaSyncRefStore
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.SerializableAudioBlock
import app.logdate.shared.model.SerializableCameraBlock
import app.logdate.shared.model.SerializableImageBlock
import app.logdate.shared.model.SerializableVideoBlock
import app.logdate.shared.model.diagnostics.DiagnosticReason
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Uploads and downloads a note's media file alongside its metadata, tracking the local/remote URI pair in [mediaSyncRefStore]. */
internal class SyncMediaTransfer(
    private val mediaManager: MediaManager,
    private val mediaSyncRefStore: MediaSyncRefStore,
    private val cloudMediaDataSource: CloudMediaDataSource,
    private val mediaReferences: StoredMediaReferences = StoredMediaReferences.Unchanged,
) {
    /** Whether two stored references name the same local file, however each is spelled. */
    fun sameLocalFile(
        first: String,
        second: String,
    ): Boolean = first == second || mediaReferences.storedReference(first) == mediaReferences.storedReference(second)

    fun isRemoteRef(mediaRef: String): Boolean = mediaRef.startsWith("http://") || mediaRef.startsWith("https://")

    /** Resolve every local attachment before the draft record can reference it remotely. */
    suspend fun uploadDraftMediaIfNeeded(
        accessToken: String,
        draft: EditorDraft,
    ): Result<EditorDraft> =
        runCatching {
            draft.copy(
                blocks =
                    draft.blocks.map { block ->
                        when (block) {
                            is SerializableImageBlock ->
                                block.copy(
                                    uri = uploadDraftAsset(accessToken, draft.id, block.id, "image", block.uri),
                                )
                            is SerializableVideoBlock ->
                                block.copy(
                                    uri = uploadDraftAsset(accessToken, draft.id, block.id, "video", block.uri),
                                    thumbnailUri = uploadDraftAsset(accessToken, draft.id, block.id, "thumbnail", block.thumbnailUri),
                                )
                            is SerializableAudioBlock ->
                                block.copy(
                                    uri = uploadDraftAsset(accessToken, draft.id, block.id, "audio", block.uri),
                                )
                            is SerializableCameraBlock ->
                                block.copy(
                                    uri = uploadDraftAsset(accessToken, draft.id, block.id, "camera", block.uri),
                                )
                            else -> block
                        }
                    },
            )
        }

    private suspend fun uploadDraftAsset(
        accessToken: String,
        draftId: Uuid,
        blockId: Uuid,
        kind: String,
        uri: String?,
    ): String? {
        if (uri == null || isRemoteRef(uri)) return uri
        val cached = mediaSyncRefStore.getDraftAsset(draftId, blockId, kind)
        if (cached != null && matchesLocalFile(cached, uri) && cached.remoteUrl.isNotBlank()) return cached.remoteUrl
        val media = mediaManager.openMedia(uri)
        val named = MediaFileSource("draft-$kind-${media.fileName}", media.mimeType, media.sizeBytes) { media.open() }
        val uploaded = cloudMediaDataSource.uploadMedia(accessToken, blockId, named).getOrThrow()
        mediaSyncRefStore.upsertDraftAsset(
            draftId,
            blockId,
            kind,
            MediaSyncRef(
                noteId = blockId.toString(),
                localUri = uri,
                remoteUrl = uploaded.downloadUrl,
                mediaId = uploaded.mediaId,
                updatedAt = Clock.System.now().toEpochMilliseconds(),
            ),
        )
        return uploaded.downloadUrl
    }

    /** Media is repaired after the draft text has been saved; failures leave the retry row intact. */
    suspend fun hydrateDraftMedia(
        accessToken: String,
        draft: EditorDraft,
    ): DraftMediaHydrationResult {
        val mappings = mutableListOf<DraftAssetMapping>()
        val blocks = draft.blocks.toMutableList()
        val failures = mutableListOf<DiagnosticReason>()

        suspend fun recoverAsset(
            blockId: Uuid,
            kind: String,
            reference: String?,
        ): String? =
            try {
                hydrateDraftAsset(accessToken, draft.id, blockId, kind, reference, mappings)
            } catch (error: DraftMediaFailure) {
                failures += error.reason
                reference
            }
        draft.blocks.forEachIndexed { index, block ->
            when (block) {
                is SerializableImageBlock ->
                    blocks[index] =
                        block.copy(uri = recoverAsset(block.id, "image", block.uri))
                is SerializableVideoBlock -> {
                    val video = block.copy(uri = recoverAsset(block.id, "video", block.uri))
                    blocks[index] = video
                    blocks[index] =
                        video.copy(
                            thumbnailUri =
                                recoverAsset(block.id, "thumbnail", block.thumbnailUri),
                        )
                }
                is SerializableAudioBlock ->
                    blocks[index] =
                        block.copy(uri = recoverAsset(block.id, "audio", block.uri))
                is SerializableCameraBlock ->
                    blocks[index] =
                        block.copy(uri = recoverAsset(block.id, "camera", block.uri))
                else -> Unit
            }
        }
        val updated = draft.copy(blocks = blocks)
        val reason =
            failures.firstOrNull {
                it == DiagnosticReason.KEY_RECOVERY_REQUIRED || it == DiagnosticReason.SIGN_IN_REQUIRED
            } ?: failures.firstOrNull()
        return if (reason == null) {
            DraftMediaHydrationResult.Available(updated, mappings)
        } else {
            DraftMediaHydrationResult.Pending(reason, updated, mappings)
        }
    }

    private suspend fun hydrateDraftAsset(
        accessToken: String,
        draftId: Uuid,
        blockId: Uuid,
        kind: String,
        uri: String?,
        mappings: MutableList<DraftAssetMapping>,
    ): String? {
        if (uri == null) return null
        if (!isRemoteRef(uri) && mediaManager.exists(uri)) return uri
        val cached = mediaSyncRefStore.getDraftAsset(draftId, blockId, kind)
        val remote = if (isRemoteRef(uri)) uri else cached?.takeIf { matchesLocalFile(it, uri) }?.remoteUrl
        if (remote == null || !isRemoteRef(remote)) throw DraftMediaFailure(DiagnosticReason.MISSING_MEDIA)
        if (cached?.remoteUrl == remote && !isRemoteRef(cached.localUri) && mediaManager.exists(cached.localUri)) {
            return cached.localUri
        }
        val mediaId = extractMediaId(remote) ?: throw DraftMediaFailure(DiagnosticReason.MISSING_MEDIA)
        val downloaded =
            try {
                cloudMediaDataSource.downloadMedia(accessToken, mediaId).getOrThrow()
            } catch (
                cancelled: CancellationException,
            ) {
                throw cancelled
            } catch (error: Exception) {
                throw DraftMediaFailure(
                    when {
                        error is IdentityKeyNotFoundException -> DiagnosticReason.KEY_RECOVERY_REQUIRED
                        error is CloudApiException && error.statusCode == 404 -> DiagnosticReason.MISSING_MEDIA
                        error is CloudApiException && error.statusCode == 401 -> DiagnosticReason.SIGN_IN_REQUIRED
                        error is app.logdate.client.sync.cloud.MediaDecryptionException -> DiagnosticReason.CORRUPT_PAYLOAD
                        else -> DiagnosticReason.UNKNOWN
                    },
                )
            }
        if (downloaded.contentId != blockId) throw DraftMediaFailure(DiagnosticReason.CORRUPT_PAYLOAD)
        val localUri =
            try {
                mediaManager.saveMedia(
                    MediaPayload(downloaded.fileName, downloaded.mimeType, downloaded.data.size.toLong(), downloaded.data),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                throw DraftMediaFailure(DiagnosticReason.LOCAL_STORAGE)
            }
        if (isRemoteRef(localUri) || !mediaManager.exists(localUri)) throw DraftMediaFailure(DiagnosticReason.LOCAL_STORAGE)
        mappings +=
            DraftAssetMapping(
                draftId,
                blockId,
                kind,
                MediaSyncRef(
                    noteId = blockId.toString(),
                    localUri = localUri,
                    remoteUrl = remote,
                    mediaId = mediaId,
                    updatedAt = Clock.System.now().toEpochMilliseconds(),
                    // Keep a retry connected to its old path if a concurrent editor save rejects the draft patch.
                    localAliases =
                        if (cached?.remoteUrl == remote) {
                            (cached.localAliases + cached.localUri + listOfNotNull(uri.takeUnless(::isRemoteRef)))
                                .asReversed()
                                .distinct()
                                .take(8)
                                .asReversed()
                        } else {
                            emptyList()
                        },
                ),
            )
        return localUri
    }

    suspend fun uploadIfNeeded(
        accessToken: String,
        note: JournalNote,
    ): Result<JournalNote> {
        val mediaRef = note.mediaRefOrNull() ?: return Result.success(note)
        if (isRemoteRef(mediaRef)) {
            return Result.success(note)
        }
        val cached = mediaSyncRefStore.get(note.uid)
        if (cached != null && matchesLocalFile(cached, mediaRef) && cached.remoteUrl.isNotBlank()) {
            return Result.success(note.withMediaRef(cached.remoteUrl))
        }

        val media =
            runCatching { mediaManager.openMedia(mediaRef) }
                .getOrElse { error -> return unreadableMedia(mediaRef, error) }

        return runCatching {
            val upload = cloudMediaDataSource.uploadMedia(accessToken, note.uid, media)
            upload.exceptionOrNull()?.let { error ->
                // The file is read while the request is sent, so it can vanish after it was opened.
                if (error is MediaReadException) return unreadableMedia(mediaRef, error)
            }
            upload.getOrThrow()
        }.map { upload ->
            val remoteUrl = upload.downloadUrl
            mediaSyncRefStore.upsert(
                MediaSyncRef(
                    noteId = note.uid.toString(),
                    localUri = mediaRef,
                    remoteUrl = remoteUrl,
                    mediaId = upload.mediaId,
                    updatedAt = Clock.System.now().toEpochMilliseconds(),
                ),
            )
            note.withMediaRef(remoteUrl)
        }
    }

    /**
     * Distinguishes "the bytes are gone" from "the read happened to fail". Only the former is
     * hopeless; an upload that fails for any other reason is still worth retrying. If the check
     * itself fails we cannot show the file is still there, and the read has already failed -- treat
     * it as gone rather than blocking the queue.
     */
    private suspend fun unreadableMedia(
        mediaRef: String,
        error: Throwable,
    ): Result<JournalNote> {
        val stillOnDisk = runCatching { mediaManager.exists(mediaRef) }.getOrDefault(false)
        return if (!stillOnDisk) {
            Result.failure(MissingMediaException(mediaRef, error))
        } else {
            Result.failure(error)
        }
    }

    suspend fun downloadIfNeeded(
        accessToken: String,
        note: JournalNote,
    ): JournalNote =
        when (val result = hydrate(accessToken, note)) {
            is MediaHydrationResult.Available -> {
                result.mapping?.let { mediaSyncRefStore.upsert(it) }
                result.note
            }
            is MediaHydrationResult.Pending -> throw MediaRecoveryException(result.reason)
        }

    suspend fun hydrate(
        accessToken: String,
        note: JournalNote,
    ): MediaHydrationResult {
        val mediaRef = note.mediaRefOrNull() ?: return MediaHydrationResult.Available(note)
        if (!isRemoteRef(mediaRef) && mediaManager.exists(mediaRef)) return MediaHydrationResult.Available(note)
        val remote =
            if (isRemoteRef(mediaRef)) {
                mediaRef
            } else {
                mediaSyncRefStore.get(note.uid)?.takeIf { matchesLocalFile(it, mediaRef) }?.remoteUrl
            }
        if (remote == null || !isRemoteRef(remote)) return MediaHydrationResult.Pending(DiagnosticReason.MISSING_MEDIA)
        val mediaId = extractMediaId(remote) ?: return MediaHydrationResult.Pending(DiagnosticReason.MISSING_MEDIA)
        val downloaded =
            try {
                cloudMediaDataSource.downloadMedia(accessToken, mediaId).getOrThrow()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val reason =
                    when {
                        error is IdentityKeyNotFoundException -> DiagnosticReason.KEY_RECOVERY_REQUIRED
                        error is CloudApiException && error.statusCode == 404 -> DiagnosticReason.MISSING_MEDIA
                        error is CloudApiException && error.statusCode == 401 -> DiagnosticReason.SIGN_IN_REQUIRED
                        error is app.logdate.client.sync.cloud.MediaDecryptionException -> DiagnosticReason.CORRUPT_PAYLOAD
                        else -> DiagnosticReason.UNKNOWN
                    }
                return MediaHydrationResult.Pending(reason)
            }
        return try {
            val localUri =
                mediaManager.saveMedia(
                    MediaPayload(
                        fileName = downloaded.fileName,
                        mimeType = downloaded.mimeType,
                        sizeBytes = downloaded.data.size.toLong(),
                        data = downloaded.data,
                    ),
                )
            check(!isRemoteRef(localUri) && mediaManager.exists(localUri))
            MediaHydrationResult.Available(
                note.withMediaRef(localUri),
                MediaSyncRef(
                    noteId = note.uid.toString(),
                    localUri = localUri,
                    remoteUrl = remote,
                    mediaId = mediaId,
                    updatedAt = Clock.System.now().toEpochMilliseconds(),
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            MediaHydrationResult.Pending(DiagnosticReason.LOCAL_STORAGE)
        }
    }

    private fun matchesLocalFile(
        mapping: MediaSyncRef,
        reference: String,
    ): Boolean = sameLocalFile(mapping.localUri, reference) || mapping.localAliases.any { sameLocalFile(it, reference) }

    private fun extractMediaId(mediaRef: String): String? =
        runCatching {
            val normalized =
                mediaRef
                    .substringBefore('#')
                    .substringBefore('?')
                    .trim()
            val segments = normalized.split('/')
            val mediaIndex = segments.indexOfLast { it == "media" }
            if (mediaIndex >= 0) {
                segments.getOrNull(mediaIndex + 1)?.takeIf { it.isNotBlank() }
            } else {
                segments.lastOrNull()?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()

    private fun JournalNote.withMediaRef(mediaRef: String): JournalNote =
        when (this) {
            is JournalNote.Image -> copy(mediaRef = mediaRef)
            is JournalNote.Video -> copy(mediaRef = mediaRef)
            is JournalNote.Audio -> copy(mediaRef = mediaRef)
            else -> this
        }
}

internal sealed interface MediaHydrationResult {
    data class Available(
        val note: JournalNote,
        val mapping: MediaSyncRef? = null,
    ) : MediaHydrationResult

    data class Pending(
        val reason: DiagnosticReason,
    ) : MediaHydrationResult
}

internal data class DraftAssetMapping(
    val draftId: Uuid,
    val blockId: Uuid,
    val kind: String,
    val ref: MediaSyncRef,
)

internal sealed interface DraftMediaHydrationResult {
    data class Available(
        val draft: EditorDraft,
        val mappings: List<DraftAssetMapping>,
    ) : DraftMediaHydrationResult

    data class Pending(
        val reason: DiagnosticReason,
        val draft: EditorDraft,
        val mappings: List<DraftAssetMapping>,
    ) : DraftMediaHydrationResult
}

private class DraftMediaFailure(
    val reason: DiagnosticReason,
) : Exception()

internal class MediaRecoveryException(
    val reason: DiagnosticReason,
) : Exception(reason.name)
