package app.logdate.client.sync

import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaPayload
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.mediaRefOrNull
import app.logdate.client.sync.cloud.CloudMediaDataSource
import app.logdate.client.sync.cloud.MediaFile
import app.logdate.client.sync.metadata.MediaSyncRef
import app.logdate.client.sync.metadata.MediaSyncRefStore
import io.github.aakira.napier.Napier
import kotlin.time.Clock

/** Uploads and downloads a note's media file alongside its metadata, tracking the local/remote URI pair in [mediaSyncRefStore]. */
internal class SyncMediaTransfer(
    private val mediaManager: MediaManager,
    private val mediaSyncRefStore: MediaSyncRefStore,
    private val cloudMediaDataSource: CloudMediaDataSource,
) {
    fun isRemoteRef(mediaRef: String): Boolean = mediaRef.startsWith("http://") || mediaRef.startsWith("https://")

    suspend fun uploadIfNeeded(
        accessToken: String,
        note: JournalNote,
    ): Result<JournalNote> {
        val mediaRef = note.mediaRefOrNull() ?: return Result.success(note)
        if (isRemoteRef(mediaRef)) {
            return Result.success(note)
        }
        val cached = mediaSyncRefStore.get(note.uid)
        if (cached != null && cached.localUri == mediaRef && cached.remoteUrl.isNotBlank()) {
            return Result.success(note.withMediaRef(cached.remoteUrl))
        }

        val payload =
            runCatching { mediaManager.readMedia(mediaRef) }
                .getOrElse { error ->
                    // Distinguish "the bytes are gone" from "the read happened to fail". Only the
                    // former is hopeless; an upload that fails for any other reason is still worth
                    // retrying.
                    // If the check itself fails we cannot show the file is still there, and the
                    // read has already failed -- treat it as gone rather than blocking the queue.
                    val stillOnDisk = runCatching { mediaManager.exists(mediaRef) }.getOrDefault(false)
                    return if (!stillOnDisk) {
                        Result.failure(MissingMediaException(mediaRef, error))
                    } else {
                        Result.failure(error)
                    }
                }

        return runCatching {
            val uploadResult =
                cloudMediaDataSource.uploadMedia(
                    accessToken,
                    MediaFile(
                        contentId = note.uid,
                        fileName = payload.fileName,
                        mimeType = payload.mimeType,
                        sizeBytes = payload.sizeBytes,
                        data = payload.data,
                    ),
                )
            uploadResult.getOrThrow()
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

    suspend fun downloadIfNeeded(
        accessToken: String,
        note: JournalNote,
    ): JournalNote {
        val mediaRef = note.mediaRefOrNull() ?: return note
        if (!isRemoteRef(mediaRef)) {
            return note
        }
        val mediaId = extractMediaId(mediaRef) ?: return note
        val downloadResult = cloudMediaDataSource.downloadMedia(accessToken, mediaId)
        if (downloadResult.isFailure) {
            Napier.w("Failed to download media for note ${note.uid}: ${downloadResult.exceptionOrNull()?.message}")
            return note
        }
        val mediaFile = downloadResult.getOrThrow()
        return runCatching {
            val localUri =
                mediaManager.saveMedia(
                    MediaPayload(
                        fileName = mediaFile.fileName,
                        mimeType = mediaFile.mimeType,
                        sizeBytes = mediaFile.sizeBytes,
                        data = mediaFile.data,
                    ),
                )
            mediaSyncRefStore.upsert(
                MediaSyncRef(
                    noteId = note.uid.toString(),
                    localUri = localUri,
                    remoteUrl = mediaRef,
                    mediaId = mediaId,
                    updatedAt = Clock.System.now().toEpochMilliseconds(),
                ),
            )
            note.withMediaRef(localUri)
        }.getOrElse { error ->
            Napier.w("Failed to persist downloaded media for note ${note.uid}", error)
            note
        }
    }

    private fun extractMediaId(mediaRef: String): String? =
        runCatching {
            val normalized =
                mediaRef
                    .substringBefore('#')
                    .substringBefore('?')
                    .trim()

            val lastSlashIndex = normalized.lastIndexOf('/')
            if (lastSlashIndex == -1 || lastSlashIndex == normalized.lastIndex) {
                null
            } else {
                normalized.substring(lastSlashIndex + 1).takeIf { it.isNotBlank() }
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
