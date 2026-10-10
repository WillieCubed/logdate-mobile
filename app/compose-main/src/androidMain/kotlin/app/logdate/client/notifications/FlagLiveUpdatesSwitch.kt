package app.logdate.client.notifications

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * The [LiveUpdatesSwitch] the app provides: on while the LIVE_UPDATES feature flag is on.
 *
 * Notification builders read [enabled] synchronously, so the flag is followed eagerly from app start.
 */
class FlagLiveUpdatesSwitch(
    flags: FeatureFlagStore,
    scope: CoroutineScope,
) : LiveUpdatesSwitch {
    private val state: StateFlow<Boolean> =
        flags.observe(FeatureFlag.LIVE_UPDATES).stateIn(scope, SharingStarted.Eagerly, FeatureFlag.LIVE_UPDATES.defaultEnabled)

    override val enabled: Boolean get() = state.value
}
