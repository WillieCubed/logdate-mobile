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

        val triggerContext =
            runCatching {
                AmbientPromptTriggerContext.valueOf(
                    inputData.getString(KEY_TRIGGER_CONTEXT) ?: AmbientPromptTriggerContext.PERIODIC.name,
                )
            }.getOrDefault(AmbientPromptTriggerContext.PERIODIC)

        val candidates = generateAmbientPromptCandidates(triggerContext)
        val candidate =
            candidates.firstOrNull { ambientPromptHistoryRepository.canShow(it) }
                ?: return Result.success()

        if (flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)) return Result.success()
        val posted = notificationCoordinator.post(candidate)
        if (!posted) {
            Napier.w("Ambient prompt notification could not be posted")
            return Result.success()
        }

        ambientPromptHistoryRepository.recordShown(candidate)
        return Result.success()
    }

    companion object {
        const val KEY_TRIGGER_CONTEXT = "ambient_trigger_context"
    }
}
