package app.logdate.client.ambient

import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AmbientPromptSchedulingObserver(
    private val memoriesSettingsRepository: MemoriesSettingsRepository,
    private val scheduler: AmbientPromptScheduler,
    private val applicationScope: CoroutineScope,
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return

        job =
            applicationScope.launch {
                memoriesSettingsRepository.observeSettings().collectLatest { settings ->
                    Napier.d("Refreshing ambient prompt schedules after settings change")
                    scheduler.refreshSchedules(settings)
                }
            }
    }
}
