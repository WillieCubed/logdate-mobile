package app.logdate.client.testing.launch

import app.logdate.client.testing.navigation.NavigationTestDestination
import app.logdate.client.testing.onboarding.OnboardingTestFixture

/**
 * Process-local launch overrides for instrumented tests that need MainActivity
 * to start from a stable app state without replaying a longer setup flow.
 */
object ActivityLaunchTestOverrides {
    var onboardingFixture: OnboardingTestFixture? = null
    var navigationDestination: NavigationTestDestination? = null

    fun clear() {
        onboardingFixture = null
        navigationDestination = null
    }
}
