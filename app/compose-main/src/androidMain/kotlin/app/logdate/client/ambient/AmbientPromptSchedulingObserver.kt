package app.logdate.client.ambient

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.WatchPreferencesRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AmbientPromptSchedulingObserver(
    private val memoriesSettingsRepository: MemoriesSettingsRepository,
    private val scheduler: AmbientPromptScheduler,
    private val applicationScope: CoroutineScope,
    private val flags: FeatureFlagStore,
    private val shared: WatchPreferencesRepository,
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return

        job =
            applicationScope.launch {
                combine(
                    memoriesSettingsRepository.observeSettings(),
                    flags.observe(FeatureFlag.WATCH_EXPERIENCE_V2),
                    shared.observe(SharedPreferenceKey.RemindersEnabled),
                ) { settings, _, _ -> settings }.collectLatest { settings ->
                    Napier.d("Refreshing ambient prompt schedules after settings change")
                    scheduler.refreshSchedules(settings)
                }
            }
    }
}
