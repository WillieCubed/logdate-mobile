package app.logdate.client.media.audio.transcription

import android.content.Context
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

/**
 * Android implementation of [TranscriptionManager] using WorkManager.
 */
class AndroidTranscriptionManager(
    private val context: Context,
) : TranscriptionManager {
    private val workManager by lazy { WorkManager.getInstance(context) }
    private val workQueue by lazy {
        AndroidTranscriptionWorkQueue(
            find = { workManager.getWorkInfosForUniqueWork(it).get() },
            enqueueUnique = { name, request -> workManager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request).result.get() },
            update = { workManager.updateWork(it).get() },
            canExpedite = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        )
    }

    companion object {
        const val WORK_NAME_PREFIX = "transcription_"
        const val KEY_NOTE_ID = "noteId"
        const val KEY_AUDIO_URI = "audioUri"
        const val NOTE_TAG_PREFIX = "transcription_note_"
    }

    override suspend fun enqueueTranscription(
        noteId: Uuid,
        audioUri: String,
    ): Boolean = enqueueTranscription(noteId, audioUri, "")

    override suspend fun enqueueTranscription(
        noteId: Uuid,
        audioUri: String,
        mediaRevision: String,
    ): Boolean = enqueueTranscription(noteId, audioUri, mediaRevision, TranscriptionPriority.RECOVERY)

    override suspend fun enqueueTranscription(
        noteId: Uuid,
        audioUri: String,
        mediaRevision: String,
        priority: TranscriptionPriority,
    ): Boolean {
        Napier.d("Enqueuing transcription for note $noteId, URI: $audioUri")

        return withContext(Dispatchers.IO) {
            try {
                workQueue.enqueue(noteId, audioUri, mediaRevision, priority)
                true
            } catch (e: Exception) {
                Napier.e("Failed to enqueue transcription", e)
                false
            }
        }
    }

    override suspend fun cancelTranscription(noteId: Uuid): Boolean {
        Napier.d("Canceling transcription for note $noteId")

        return withContext(Dispatchers.IO) {
            try {
                workManager.cancelAllWorkByTag(NOTE_TAG_PREFIX + noteId).result.get()
                workManager.cancelUniqueWork(WORK_NAME_PREFIX + noteId).result.get()
                true
            } catch (e: Exception) {
                Napier.e("Failed to cancel transcription", e)
                false
            }
        }
    }

    override suspend fun cancelAllTranscriptions(): Int {
        Napier.d("Canceling all transcription jobs")

        return withContext(Dispatchers.IO) {
            try {
                // Get all transcription work info
                val workInfos = workManager.getWorkInfosByTag(TranscriptionWorker.TAG).get()

                // Cancel all work
                workManager.cancelAllWorkByTag(TranscriptionWorker.TAG).result.get()

                // Return count of canceled jobs
                workInfos.count { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            } catch (e: Exception) {
                Napier.e("Failed to cancel all transcriptions", e)
                0
            }
        }
    }
}
