package app.logdate.client.ambient

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.logdate.client.domain.recommendation.AmbientPromptHistoryRepository
import app.logdate.client.domain.recommendation.AmbientPromptTriggerContext
import app.logdate.client.domain.recommendation.GenerateAmbientPromptCandidatesUseCase
import io.github.aakira.napier.Napier
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AmbientPromptWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val generateAmbientPromptCandidates: GenerateAmbientPromptCandidatesUseCase by inject()
    private val ambientPromptHistoryRepository: AmbientPromptHistoryRepository by inject()
    private val notificationCoordinator: AmbientPromptNotificationCoordinator by inject()

    override suspend fun doWork(): Result {
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
