package app.logdate.client.media.audio.transcription

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.uuid.Uuid

/** Keeps a single WorkManager identity when foreground audio promotes queued recovery. */
internal class AndroidTranscriptionWorkQueue(
    private val find: (String) -> List<WorkInfo>,
    private val enqueueUnique: (String, OneTimeWorkRequest) -> Unit,
    private val update: (OneTimeWorkRequest) -> Unit,
    private val canExpedite: Boolean,
) {
    fun enqueue(
        noteId: Uuid,
        audioUri: String,
        mediaRevision: String,
        priority: TranscriptionPriority,
    ) {
        val revisionKey =
            MessageDigest
                .getInstance("SHA-256")
                .digest("${audioUri.length}:$audioUri:${mediaRevision.length}:$mediaRevision".encodeToByteArray())
                .joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
        val workName = AndroidTranscriptionManager.WORK_NAME_PREFIX + noteId + "_" + revisionKey
        val existing = find(workName).firstOrNull { !it.state.isFinished }
        if (existing != null) {
            if (existing.state != WorkInfo.State.RUNNING &&
                priority == TranscriptionPriority.FOREGROUND &&
                FOREGROUND_TAG !in existing.tags
            ) {
                // updateWork defers changes if this worker starts between the query and update.
                update(buildTranscriptionWorkRequest(noteId, audioUri, priority, canExpedite, existing))
            }
            return
        }
        enqueueUnique(workName, buildTranscriptionWorkRequest(noteId, audioUri, priority, canExpedite))
    }

    companion object {
        const val FOREGROUND_TAG = "transcription_foreground"
    }
}

internal fun buildTranscriptionWorkRequest(
    noteId: Uuid,
    audioUri: String,
    priority: TranscriptionPriority = TranscriptionPriority.RECOVERY,
    canExpedite: Boolean = false,
    existing: WorkInfo? = null,
): OneTimeWorkRequest {
    val inputData =
        Data
            .Builder()
            .putString(AndroidTranscriptionManager.KEY_NOTE_ID, noteId.toString())
            .putString(AndroidTranscriptionManager.KEY_AUDIO_URI, audioUri)
            .build()
    val constraints = Constraints.Builder().setRequiresBatteryNotLow(priority == TranscriptionPriority.RECOVERY).build()
    return OneTimeWorkRequestBuilder<TranscriptionWorker>()
        .setInputData(inputData)
        .setConstraints(constraints)
        .addTag(TranscriptionWorker.TAG)
        .addTag(AndroidTranscriptionManager.NOTE_TAG_PREFIX + noteId)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
        .apply {
            if (existing != null) setId(existing.id)
            if (priority == TranscriptionPriority.FOREGROUND) {
                addTag(AndroidTranscriptionWorkQueue.FOREGROUND_TAG)
                if (canExpedite) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }
        }.build()
}
