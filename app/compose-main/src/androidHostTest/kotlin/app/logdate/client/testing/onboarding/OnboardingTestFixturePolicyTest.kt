package app.logdate.client.testing.onboarding

import app.logdate.feature.onboarding.flow.OnboardingEntryMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OnboardingTestFixturePolicyTest {
    @Test
    fun `release builds reject onboarding launch overrides`() {
        val fixture =
            OnboardingTestFixture(
                isOnboarded = true,
                entryMode = OnboardingEntryMode.CONTINUE_SETUP,
            )

        assertNull(
            selectOnboardingTestFixture(
                isDebuggable = false,
                skipOnboardingRequested = true,
                intentFixture = fixture,
                processFixture = OnboardingTestFixture.FRESH_ONBOARDING,
            ),
        )
    }

    @Test
    fun `debug bypass selects the onboarded home fixture`() {
        assertEquals(
            OnboardingTestFixture.ONBOARDED_HOME,
            selectOnboardingTestFixture(
                isDebuggable = true,
                skipOnboardingRequested = true,
                intentFixture = OnboardingTestFixture.FRESH_ONBOARDING,
                processFixture = null,
            ),
        )
    }

    @Test
    fun `debug launch fixtures keep working without the bypass`() {
        val fixture =
            OnboardingTestFixture(
                entryMode = OnboardingEntryMode.CONTINUE_SETUP,
                hasPersonalIntro = true,
            )

        assertEquals(
            fixture,
            selectOnboardingTestFixture(
                isDebuggable = true,
                skipOnboardingRequested = false,
                intentFixture = fixture,
                processFixture = OnboardingTestFixture.FRESH_ONBOARDING,
            ),
        )
    }
}
