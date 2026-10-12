package app.logdate.client.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.reminders.PhoneReminderDelivery
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException

class MomentReminderWorker(
    context: Context,
    parameters: WorkerParameters,
    private val flags: FeatureFlagStore,
    private val delivery: PhoneReminderDelivery,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)) return Result.success()
        try {
            delivery.run()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Signal reminder evaluation unavailable; waiting for the next evaluation", e)
        }
        return Result.success()
    }
}
