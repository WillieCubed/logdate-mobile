package app.logdate.client.platform

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * One feature flag, readable synchronously. Notification and broadcast code runs outside coroutines,
 * so the flag is followed eagerly from app start and [enabled] is always current.
 */
class FeatureFlagState(
    flags: FeatureFlagStore,
    flag: FeatureFlag,
    scope: CoroutineScope,
) {
    private val state: StateFlow<Boolean> = flags.observe(flag).stateIn(scope, SharingStarted.Eagerly, flag.defaultEnabled)

    val enabled: Boolean get() = state.value
}
