package app.logdate.feature.onboarding.flow

import app.logdate.client.datastore.KeyValueStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Persists onboarding device state through the shared [KeyValueStorage] abstraction, which
 * already works on every platform (Android, desktop, iOS) -- unlike the file-marker approach
 * this replaced, which iOS never had a real implementation of (device state there was held in
 * memory only, resetting every time the process died mid-onboarding).
 */
class KeyValueOnboardingDeviceStateRepository(
    private val storage: KeyValueStorage,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : OnboardingDeviceStateRepository {
    override val deviceState: StateFlow<OnboardingDeviceState> =
        combine(
            combine(
                storage.observeBoolean(KEY_RECOMMENDATIONS_HANDLED),
                storage.observeBoolean(KEY_DAY_BOUNDARIES_HANDLED),
                storage.observeBoolean(KEY_LOCATION_HANDLED),
                storage.observeBoolean(KEY_NOTIFICATIONS_HANDLED),
                storage.observeString(KEY_ACTIVE_ENTRY_MODE),
            ) { recommendations, dayBoundaries, location, notifications, entryModeName ->
                PartialDeviceState(
                    recommendationsHandledOnThisDevice = recommendations,
                    dayBoundariesHandledOnThisDevice = dayBoundaries,
                    locationHandledOnThisDevice = location,
                    notificationsHandledOnThisDevice = notifications,
                    activeEntryMode = entryModeName.toEntryModeOrFresh(),
                )
            },
            storage.observeBoolean(KEY_ACCOUNT_HANDLED),
        ) { partial, accountHandled ->
            OnboardingDeviceState(
                recommendationsHandledOnThisDevice = partial.recommendationsHandledOnThisDevice,
                dayBoundariesHandledOnThisDevice = partial.dayBoundariesHandledOnThisDevice,
                locationHandledOnThisDevice = partial.locationHandledOnThisDevice,
                notificationsHandledOnThisDevice = partial.notificationsHandledOnThisDevice,
                accountHandledOnThisDevice = accountHandled,
                activeEntryMode = partial.activeEntryMode,
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = OnboardingDeviceState(),
        )

    override suspend fun markRecommendationsHandled() {
        storage.putBoolean(KEY_RECOMMENDATIONS_HANDLED, true)
    }

    override suspend fun markDayBoundariesHandled() {
        storage.putBoolean(KEY_DAY_BOUNDARIES_HANDLED, true)
    }

    override suspend fun markLocationHandled() {
        storage.putBoolean(KEY_LOCATION_HANDLED, true)
    }

    override suspend fun markNotificationsHandled() {
        storage.putBoolean(KEY_NOTIFICATIONS_HANDLED, true)
    }

    override suspend fun markAccountHandled() {
        storage.putBoolean(KEY_ACCOUNT_HANDLED, true)
    }

    override suspend fun setActiveEntryMode(entryMode: OnboardingEntryMode) {
        storage.putString(KEY_ACTIVE_ENTRY_MODE, entryMode.name)
    }

    override suspend fun clear() {
        // Individual key removal, not storage.clear() -- this KeyValueStorage instance shares
        // its underlying DataStore with unrelated settings (recommendations, location, etc.),
        // so a blanket clear would wipe far more than onboarding's own device-state keys.
        storage.remove(KEY_RECOMMENDATIONS_HANDLED)
        storage.remove(KEY_DAY_BOUNDARIES_HANDLED)
        storage.remove(KEY_LOCATION_HANDLED)
        storage.remove(KEY_NOTIFICATIONS_HANDLED)
        storage.remove(KEY_ACCOUNT_HANDLED)
        storage.remove(KEY_ACTIVE_ENTRY_MODE)
    }

    private fun String?.toEntryModeOrFresh(): OnboardingEntryMode =
        this?.let { name -> runCatching { OnboardingEntryMode.valueOf(name) }.getOrNull() } ?: OnboardingEntryMode.FRESH

    private data class PartialDeviceState(
        val recommendationsHandledOnThisDevice: Boolean,
        val dayBoundariesHandledOnThisDevice: Boolean,
        val locationHandledOnThisDevice: Boolean,
        val notificationsHandledOnThisDevice: Boolean,
        val activeEntryMode: OnboardingEntryMode,
    )

    private companion object {
        const val KEY_RECOMMENDATIONS_HANDLED = "onboarding_recommendations_handled"
        const val KEY_DAY_BOUNDARIES_HANDLED = "onboarding_day_boundaries_handled"
        const val KEY_LOCATION_HANDLED = "onboarding_location_handled"
        const val KEY_NOTIFICATIONS_HANDLED = "onboarding_notifications_handled"
        const val KEY_ACCOUNT_HANDLED = "onboarding_account_handled"
        const val KEY_ACTIVE_ENTRY_MODE = "onboarding_active_entry_mode"
    }
}
