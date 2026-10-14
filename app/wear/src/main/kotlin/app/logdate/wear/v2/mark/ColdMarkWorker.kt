package app.logdate.wear.v2.mark

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.wear.launch.awaitWearStartup
import app.logdate.wear.sync.v2.removal.WatchRemovalLock
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import org.koin.java.KoinJavaComponent
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Retains the original click through slow encrypted startup, without acknowledging an unsaved mark. */
class ColdMarkWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!applicationContext.awaitWearStartup()) return Result.retry()
        return try {
            val koin = KoinJavaComponent.getKoin()
            if (koin.get<WatchRemovalLock>().state() != null) return Result.success()
            if (!koin.get<FeatureFlagStore>().isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)) return Result.success()
            val capturedAt = inputData.getString(CAPTURED_AT)?.let(Instant::parse) ?: return Result.failure()
            val zone = inputData.getString(ZONE) ?: return Result.failure()
            val id = inputData.getString(ID)?.let(Uuid::parse) ?: return Result.failure()
            if (koin.get<BackgroundMarker>().mark(capturedAt, zone, id)) Result.success() else Result.retry()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Napier.e("The queued mark could not be saved yet", error)
            Result.retry()
        }
    }

    companion object {
        private const val CAPTURED_AT = "captured_at"
        private const val ZONE = "time_zone"
        private const val ID = "mark_id"

        fun request(
            capturedAt: Instant,
            timeZoneId: String,
            markId: Uuid,
        ): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<ColdMarkWorker>()
                .setInputData(
                    workDataOf(
                        CAPTURED_AT to capturedAt.toString(),
                        ZONE to timeZoneId,
                        ID to markId.toString(),
                    ),
                ).build()
    }
}
