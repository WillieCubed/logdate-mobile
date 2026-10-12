package app.logdate.client.domain.reminders

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.WatchPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class ReminderPreferencesState(
    val ready: Boolean = false,
    val rolloutEnabled: Boolean = false,
    val enabled: Boolean = false,
    val capturePlaces: Boolean = false,
)

/** The single reminder choice shared by phone settings, onboarding and the watch. */
class ReminderPreferences(
    private val shared: WatchPreferencesRepository,
    private val flags: FeatureFlagStore,
) {
    fun observe(): Flow<ReminderPreferencesState> =
        combine(
            flags.observe(FeatureFlag.WATCH_EXPERIENCE_V2),
            shared.observe(SharedPreferenceKey.RemindersEnabled),
            shared.observe(SharedPreferenceKey.CapturePlaces),
        ) { rollout, enabled, places -> ReminderPreferencesState(true, rollout, enabled, places) }

    suspend fun setPlacesEnabled(enabled: Boolean): Boolean {
        if (!flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)) return false
        shared.set(SharedPreferenceKey.CapturePlaces, enabled)
        return shared.get(SharedPreferenceKey.CapturePlaces) == enabled
    }

    suspend fun setEnabled(enabled: Boolean): Boolean {
        if (!flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)) return false
        shared.set(SharedPreferenceKey.RemindersEnabled, enabled)
        return shared.get(SharedPreferenceKey.RemindersEnabled) == enabled
    }
}
