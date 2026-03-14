package app.logdate.navigation.routes

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Route for the unified cloud account setup flow from settings.
 *
 * This reuses [CloudAccountOnboardingScreen][app.logdate.feature.core.account.CloudAccountOnboardingScreen]
 * but skips the welcome pitch (since the user already committed from the settings promo).
 *
 * @property startOnSignIn When true, starts on the sign-in step instead of account creation.
 */
@Serializable
data class CloudAccountSetupFlowRoute(
    val startOnSignIn: Boolean = false,
) : NavKey
