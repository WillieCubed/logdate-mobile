package app.logdate.client.media.audio.transcription

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.uuid.Uuid

/**
 * Android implementation of [TranscriptionManager] using WorkManager.
 */
class AndroidTranscriptionManager(
    private val context: Context
) : TranscriptionManager {

    private val workManager by lazy { WorkManager.getInstance(context) }
    
    companion object {
        const val WORK_NAME_PREFIX = "transcription_"
        const val KEY_NOTE_ID = "noteId"
        const val KEY_AUDIO_URI = "audioUri"
    }

    override suspend fun enqueueTranscription(noteId: Uuid, audioUri: String): Boolean {
        Napier.d("Enqueuing transcription for note $noteId, URI: $audioUri")
        
        return withContext(Dispatchers.IO) {
            try {
                // Prepare input data
                val inputData = Data.Builder()
                    .putString(KEY_NOTE_ID, noteId.toString())
                    .putString(KEY_AUDIO_URI, audioUri)
                    .build()
                
                // Define work constraints
                val constraints = Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                
                // Create work request
                val workRequest = OneTimeWorkRequestBuilder<TranscriptionWorker>()
                    .setInputData(inputData)
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        10, // Initial backoff delay in seconds
                        TimeUnit.SECONDS
                    )
                    .build()
                
                // Enqueue unique work to ensure only one transcription job runs for this note
                val workName = WORK_NAME_PREFIX + noteId.toString()
                workManager.enqueueUniqueWork(
                    workName,
                    ExistingWorkPolicy.REPLACE, // Replace any existing work for this note
                    workRequest
                )
                
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
                val workName = WORK_NAME_PREFIX + noteId.toString()
                workManager.cancelUniqueWork(workName)
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
                workManager.cancelAllWorkByTag(TranscriptionWorker.TAG)
                
                // Return count of canceled jobs
                workInfos.count { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            } catch (e: Exception) {
                Napier.e("Failed to cancel all transcriptions", e)
                0
            }
        }
    }
}