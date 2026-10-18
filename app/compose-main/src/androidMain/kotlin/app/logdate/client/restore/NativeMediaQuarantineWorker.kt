package app.logdate.client.restore

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.logdate.client.data.notes.NativeMediaQuarantine
import app.logdate.client.device.restore.PostRestoreDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.util.concurrent.TimeUnit

/** Retries native media reconciliation until every availability check can be completed. */
internal class NativeMediaQuarantineWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters),
    KoinComponent {
    override suspend fun doWork(): Result {
        val detector = PostRestoreDetector(applicationContext)
        val deliveryId = inputData.getString(INPUT_DELIVERY_ID) ?: detector.pendingMediaQuarantineId() ?: return Result.success()
        val recovery =
            NativeMediaQuarantineRecovery(
                pendingDelivery = detector::pendingMediaQuarantineId,
                quarantine = { isCurrent -> get<NativeMediaQuarantine>().run(isCurrent) },
                acknowledge = detector::acknowledgeMediaQuarantine,
                enqueue = { NativeMediaQuarantineScheduler.enqueue(applicationContext, it) },
            )
        return when (recovery.run(deliveryId)) {
            NativeMediaQuarantineResult.COMPLETE -> Result.success()
            NativeMediaQuarantineResult.HANDED_OFF -> Result.success()
            NativeMediaQuarantineResult.RETRY -> Result.retry()
        }
    }

    companion object {
        const val INPUT_DELIVERY_ID = "native_media_delivery_id"
    }
}

internal object NativeMediaQuarantineScheduler {
    suspend fun enqueue(
        context: Context,
        deliveryId: String,
    ) {
        val request =
            OneTimeWorkRequestBuilder<NativeMediaQuarantineWorker>()
                .setInputData(workDataOf(NativeMediaQuarantineWorker.INPUT_DELIVERY_ID to deliveryId))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        withContext(Dispatchers.IO) {
            WorkManager
                .getInstance(context.applicationContext)
                .enqueueUniqueWork(workName(deliveryId), ExistingWorkPolicy.KEEP, request)
                .result
                .get()
        }
    }

    internal fun workName(deliveryId: String) = "native_media_quarantine:$deliveryId"

    private const val RETRY_BACKOFF_SECONDS = 30L
}
