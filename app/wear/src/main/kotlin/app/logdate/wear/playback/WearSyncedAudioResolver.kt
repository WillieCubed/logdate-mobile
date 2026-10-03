package app.logdate.wear.playback

import android.content.Context
import android.net.Uri
import app.logdate.client.media.storage.MediaFileResolver
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.wear.sync.SyncedAudioCache
import app.logdate.wear.sync.WearDataLayerClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File

interface WearSyncedAudioResolver {
    suspend fun resolvePlayableUri(note: JournalNote.Audio): Result<String>
}

/**
 * Finds a playable file for an audio note. When the watch has none, the audio is pulled from the phone
 * into [syncedAudioCache], or taken from an earlier pull, and the note is pointed at it so the next
 * play needs no transfer.
 */
class PhoneSyncedAudioResolver(
    private val context: Context,
    private val syncedAudioCache: SyncedAudioCache,
    private val dataLayerClient: WearDataLayerClient,
    private val notesRepository: JournalNotesRepository,
    private val ioDispatcher: CoroutineDispatcher,
    private val mediaFiles: MediaFileResolver,
) : WearSyncedAudioResolver {
    override suspend fun resolvePlayableUri(note: JournalNote.Audio): Result<String> {
        val result =
            withContext(ioDispatcher) {
                runCatching {
                    if (isLocallyPlayable(note.mediaRef)) {
                        return@runCatching note.mediaRef
                    }
                    val file = syncedAudioCache.cachedFile(note.uid) ?: pullFromPhone(note)
                    (notesRepository as? SyncableJournalNotesRepository)?.updateMediaRef(note.uid, file.absolutePath)
                    file.absolutePath
                }
            }
        result.onFailure { error ->
            Napier.w("Failed to resolve synced audio for note ${note.uid}", error)
        }
        return result
    }

    private suspend fun pullFromPhone(note: JournalNote.Audio): File {
        check(dataLayerClient.isPhoneConnected()) {
            "No connected phone available to download audio ${note.uid}"
        }
        val pulled =
            syncedAudioCache.pull(note.uid, inferExtension(note.mediaRef)) { partPath ->
                dataLayerClient.downloadAudioFromPhone(note.uid, partPath)
            }
        return checkNotNull(pulled) { "Phone audio transfer failed for note ${note.uid}" }
    }

    private fun isLocallyPlayable(mediaRef: String): Boolean {
        if (mediaRef.startsWith("content://")) {
            return runCatching {
                context.contentResolver.openAssetFileDescriptor(Uri.parse(mediaRef), "r")?.use { true } ?: false
            }.getOrDefault(false)
        }

        return mediaFiles.filePath(mediaRef)?.let { File(it).exists() } ?: false
    }

    private fun inferExtension(mediaRef: String): String {
        val normalized = mediaRef.substringBefore('?').substringBefore('#')
        val extension = normalized.substringAfterLast('.', "")
        return extension.takeIf { it.matches(SAFE_EXTENSION) } ?: "m4a"
    }

    private companion object {
        /** The extension becomes part of a file name, so anything but a short alphanumeric one is replaced. */
        val SAFE_EXTENSION = Regex("[A-Za-z0-9]{1,5}")
    }
}
