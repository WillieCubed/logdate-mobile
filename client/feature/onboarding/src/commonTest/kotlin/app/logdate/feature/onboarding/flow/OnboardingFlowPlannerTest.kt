package app.logdate.feature.onboarding.flow

import app.logdate.client.domain.dayboundary.HealthConnectStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for the onboarding flow planning logic.
 *
 * This suite validates the state machine used to determine the sequence of
 * onboarding steps based on the user's entry mode (fresh vs. returning) and
 * the current device's configuration state.
 */
class OnboardingFlowPlannerTest {
    @Test
    fun `fresh flow includes full setup sequence when nothing is configured`() {
        val snapshot = OnboardingProgressSnapshot()

        assertEquals(
            listOf(
                OnboardingStep.PERSONAL_INTRO,
                OnboardingStep.APP_OVERVIEW,
                OnboardingStep.MEMORY_IMPORT,
                OnboardingStep.MEMORY_SELECTION,
                OnboardingStep.ACCOUNT,
                OnboardingStep.BIRTHDAY,
                OnboardingStep.RECOMMENDATIONS,
                OnboardingStep.DAY_BOUNDARIES,
                OnboardingStep.LOCATION,
                OnboardingStep.NOTIFICATIONS,
                OnboardingStep.COMPLETE,
            ),
            onboardingStepsFor(
                entryMode = OnboardingEntryMode.FRESH,
                snapshot = snapshot,
            ),
        )
    }

    @Test
    fun `fresh flow skips day boundaries when health connect is unavailable`() {
        val snapshot =
            OnboardingProgressSnapshot(
                healthConnectStatus = HealthConnectStatus.NOT_AVAILABLE,
            )

        assertFalse(
            onboardingStepsFor(
                entryMode = OnboardingEntryMode.FRESH,
                snapshot = snapshot,
            ).contains(OnboardingStep.DAY_BOUNDARIES),
        )
    }

    @Test
    fun `continue setup flow only includes unresolved setup steps`() {
        val snapshot =
            OnboardingProgressSnapshot(
                hasPersonalIntro = true,
                hasBirthday = false,
                notificationsHandledOnThisDevice = false,
                recommendationsHandledOnThisDevice = true,
                locationHandledOnThisDevice = true,
                dayBoundariesHandledOnThisDevice = true,
            )

        assertEquals(
            listOf(
                OnboardingStep.ACCOUNT,
                OnboardingStep.BIRTHDAY,
                OnboardingStep.NOTIFICATIONS,
                OnboardingStep.WELCOME_BACK,
            ),
            onboardingStepsFor(
                entryMode = OnboardingEntryMode.CONTINUE_SETUP,
                snapshot = snapshot,
            ),
        )
    }

    @Test
    fun `continue setup flow can finish immediately when required setup is already satisfied`() {
        val snapshot =
            OnboardingProgressSnapshot(
                hasPersonalIntro = true,
                hasCloudAccount = true,
                hasBirthday = true,
                notificationsHandledOnThisDevice = true,
                recommendationsHandledOnThisDevice = true,
                locationHandledOnThisDevice = true,
                dayBoundariesHandledOnThisDevice = true,
            )

        assertEquals(
            OnboardingStep.WELCOME_BACK,
            firstOnboardingStep(
                entryMode = OnboardingEntryMode.CONTINUE_SETUP,
                snapshot = snapshot,
            ),
        )
    }

    @Test
    fun `continue setup flow starts with account before unresolved profile steps`() {
        val snapshot = OnboardingProgressSnapshot()

        assertEquals(
            OnboardingStep.PERSONAL_INTRO,
            firstOnboardingStep(
                entryMode = OnboardingEntryMode.CONTINUE_SETUP,
                snapshot = snapshot,
            ),
        )
    }

    @Test
    fun `completion eligibility requires all canonical setup steps to be handled`() {
        assertFalse(
            OnboardingProgressSnapshot(
                hasPersonalIntro = true,
                hasBirthday = true,
                recommendationsHandledOnThisDevice = true,
                locationHandledOnThisDevice = true,
                dayBoundariesHandledOnThisDevice = false,
                notificationsHandledOnThisDevice = true,
            ).canCompleteOnboarding(),
        )

        assertTrue(
            OnboardingProgressSnapshot(
                hasPersonalIntro = true,
                hasBirthday = true,
                recommendationsHandledOnThisDevice = true,
                locationHandledOnThisDevice = true,
                dayBoundariesHandledOnThisDevice = true,
                notificationsHandledOnThisDevice = true,
            ).canCompleteOnboarding(),
        )
    }

    @Test
    fun `fresh flow advances from personal intro to app overview after intro is saved`() {
        val snapshot =
            OnboardingProgressSnapshot(
                hasPersonalIntro = true,
            )

        assertEquals(
            OnboardingStep.APP_OVERVIEW,
            nextOnboardingStepAfter(
                currentStep = OnboardingStep.PERSONAL_INTRO,
                entryMode = OnboardingEntryMode.FRESH,
                snapshot = snapshot,
            ),
        )
    }

    @Test
    fun `fresh flow advances from birthday to recommendations after birthday is saved`() {
        val snapshot =
            OnboardingProgressSnapshot(
                hasBirthday = true,
            )

        assertEquals(
            OnboardingStep.RECOMMENDATIONS,
            nextOnboardingStepAfter(
                currentStep = OnboardingStep.BIRTHDAY,
                entryMode = OnboardingEntryMode.FRESH,
                snapshot = snapshot,
            ),
        )
    }

    @Test
    fun `continue setup flow includes unresolved setup choices after birthday`() {
        val snapshot =
            OnboardingProgressSnapshot(
                hasPersonalIntro = true,
                hasBirthday = true,
                healthConnectStatus = HealthConnectStatus.NOT_AVAILABLE,
            )

        assertEquals(
            listOf(
                OnboardingStep.ACCOUNT,
                OnboardingStep.RECOMMENDATIONS,
                OnboardingStep.LOCATION,
                OnboardingStep.NOTIFICATIONS,
                OnboardingStep.WELCOME_BACK,
            ),
            onboardingStepsFor(
                entryMode = OnboardingEntryMode.CONTINUE_SETUP,
                snapshot = snapshot,
            ),
        )
    }

    @Test
    fun `completion guard redirects to recommendations before notifications when unresolved`() {
        assertEquals(
            OnboardingStep.RECOMMENDATIONS,
            OnboardingProgressSnapshot(
                hasPersonalIntro = true,
                hasBirthday = true,
                notificationsHandledOnThisDevice = true,
            ).firstIncompleteRequiredOnboardingStep(),
        )
    }
}
