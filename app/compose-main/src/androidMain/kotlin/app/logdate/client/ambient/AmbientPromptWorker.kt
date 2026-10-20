package app.logdate.client.ambient

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.recommendation.AmbientPromptHistoryRepository
import app.logdate.client.domain.recommendation.AmbientPromptTriggerContext
import app.logdate.client.domain.recommendation.GenerateAmbientPromptCandidatesUseCase
import app.logdate.client.domain.reminders.PhoneReminderDelivery
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AmbientPromptWorker(
    context: Context,
    params: WorkerParameters,
    private val flags: FeatureFlagStore,
    private val reminders: PhoneReminderDelivery,
    private val generateAmbientPromptCandidates: GenerateAmbientPromptCandidatesUseCase,
    private val ambientPromptHistoryRepository: AmbientPromptHistoryRepository,
    private val notificationCoordinator: AmbientPromptNotificationCoordinator,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)) {
            try {
                reminders.run()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.w("Moment reminder evaluation unavailable; waiting for the next evaluation", e)
            }
            return Result.success()
        }

        return try {
            runLegacyReminder()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Napier.w("Ambient reminder recovery is pending", error)
            Result.retry()
        }
    }

    private suspend fun runLegacyReminder(): Result {
        val triggerContext =
            runCatching {
                AmbientPromptTriggerContext.valueOf(
                    inputData.getString(KEY_TRIGGER_CONTEXT) ?: AmbientPromptTriggerContext.PERIODIC.name,
                )
            }.getOrDefault(AmbientPromptTriggerContext.PERIODIC)

        val candidates = generateAmbientPromptCandidates(triggerContext)
        return deliveryMutex.withLock {
            val candidate =
                candidates.firstOrNull { ambientPromptHistoryRepository.canShow(it) }
                    ?: return@withLock Result.success()
            if (flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)) return@withLock Result.success()
            currentCoroutineContext().ensureActive()
            // A replacement request cannot interrupt suppression persistence after a successful post.
            withContext(NonCancellable) {
                if (notificationCoordinator.post(candidate)) {
                    ambientPromptHistoryRepository.recordShown(candidate)
                } else {
                    Napier.w("Ambient prompt notification could not be posted")
                }
                Result.success()
            }
        }
    }

    companion object {
        const val KEY_TRIGGER_CONTEXT = "ambient_trigger_context"

        private val deliveryMutex = Mutex()
    }
}
