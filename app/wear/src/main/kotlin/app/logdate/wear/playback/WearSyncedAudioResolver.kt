package app.logdate.wear.playback

import android.content.Context
import android.net.Uri
import app.logdate.client.media.audio.AudioStorage
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.wear.sync.WearDataLayerClient
import io.github.aakira.napier.Napier
import java.io.File

interface WearSyncedAudioResolver {
    suspend fun resolvePlayableUri(note: JournalNote.Audio): Result<String>
}

class PhoneSyncedAudioResolver(
    private val context: Context,
    private val audioStorage: AudioStorage,
    private val dataLayerClient: WearDataLayerClient,
    private val notesRepository: JournalNotesRepository,
) : WearSyncedAudioResolver {

    override suspend fun resolvePlayableUri(note: JournalNote.Audio): Result<String> =
        runCatching {
            if (isLocallyPlayable(note.mediaRef)) {
                return@runCatching note.mediaRef
            }

            val extension = inferExtension(note.mediaRef)
            val target = audioStorage.createRecordingTarget(extension)
            val downloaded = dataLayerClient.downloadAudioFromPhone(note.uid, target.path)
            check(downloaded) { "Phone audio transfer failed for note ${note.uid}" }

            val syncableRepository = notesRepository as? SyncableJournalNotesRepository
            syncableRepository?.updateMediaRef(note.uid, target.path)

            target.path
        }.onFailure { error ->
            Napier.w("Failed to resolve synced audio for note ${note.uid}", error)
        }

    private fun isLocallyPlayable(mediaRef: String): Boolean {
        if (mediaRef.startsWith("http://") || mediaRef.startsWith("https://")) {
            return true
        }

        if (mediaRef.startsWith("content://")) {
            return runCatching {
                context.contentResolver.openAssetFileDescriptor(Uri.parse(mediaRef), "r")?.use { true } ?: false
            }.getOrDefault(false)
        }

        val filePath =
            when {
                mediaRef.startsWith("file://") -> Uri.parse(mediaRef).path
                mediaRef.startsWith("/") -> mediaRef
                else -> null
            }

        return filePath?.let { File(it).exists() } ?: false
    }

    private fun inferExtension(mediaRef: String): String {
        val normalized = mediaRef.substringBefore('?').substringBefore('#')
        val extension = normalized.substringAfterLast('.', "")
        return extension.takeIf { it.isNotBlank() && it.length <= 5 } ?: "m4a"
    }
}
