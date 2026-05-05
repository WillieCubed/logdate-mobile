package app.logdate.feature.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Initial back-stack entry pushed while the app is bootstrapping resources. `LogDateNavDisplay`
 * registers a no-op entry for this key; the launch lifecycle replaces the entry with
 * `OnboardingStart` or `HomeRoute` once the global UI state resolves.
 */
@Serializable
data object BaseRoute : NavKey
