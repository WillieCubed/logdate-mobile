package app.logdate.navigation

import androidx.navigation3.runtime.NavKey
import app.logdate.feature.core.main.HomeRoute
import app.logdate.feature.core.navigation.BaseRoute
import app.logdate.feature.onboarding.navigation.OnboardingBaseRoute
import app.logdate.feature.onboarding.navigation.OnboardingStart

/**
 * What the launch lifecycle should do to the back stack once global UI state resolves.
 */
sealed interface NavBootstrapAction {
    /** Keep the back stack exactly as-is. */
    data object None : NavBootstrapAction

    /** Replace the entire back stack with [key]. */
    data class ResetTo(
        val key: NavKey,
    ) : NavBootstrapAction
}

/**
 * Decides how to bootstrap [backStack] for the resolved onboarding and lock state.
 *
 * The back stack survives activity recreation (rotation, theme change, process death) via
 * `rememberNavBackStack`, so this only replaces a stack that is still on the [BaseRoute]
 * placeholder or is stranded in a flow the user has since left. A stack the user actually
 * navigated is never discarded.
 */
fun resolveNavBootstrapAction(
    backStack: List<NavKey>,
    isOnboarded: Boolean,
    requiresUnlock: Boolean,
): NavBootstrapAction {
    val top = backStack.lastOrNull()
    if (!isOnboarded) {
        return if (top is OnboardingBaseRoute) {
            NavBootstrapAction.None
        } else {
            NavBootstrapAction.ResetTo(OnboardingStart)
        }
    }
    if (requiresUnlock) return NavBootstrapAction.None
    return if (top == null || top == BaseRoute || top is OnboardingBaseRoute) {
        NavBootstrapAction.ResetTo(HomeRoute)
    } else {
        NavBootstrapAction.None
    }
}
