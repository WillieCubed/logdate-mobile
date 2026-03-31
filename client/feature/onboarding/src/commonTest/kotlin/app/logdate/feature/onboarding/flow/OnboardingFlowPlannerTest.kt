package app.logdate.feature.onboarding.flow

import app.logdate.client.domain.dayboundary.HealthConnectStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
                hasCloudAccount = true,
                hasBirthday = false,
                notificationsHandledOnThisDevice = false,
            )

        assertEquals(
            listOf(
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
            OnboardingStep.ACCOUNT,
            firstOnboardingStep(
                entryMode = OnboardingEntryMode.CONTINUE_SETUP,
                snapshot = snapshot,
            ),
        )
    }

    @Test
    fun `completion eligibility requires birthday and notification handling`() {
        assertFalse(
            OnboardingProgressSnapshot(
                hasPersonalIntro = true,
                hasBirthday = false,
                notificationsHandledOnThisDevice = true,
            ).canCompleteFreshOnboarding(),
        )

        assertTrue(
            OnboardingProgressSnapshot(
                hasPersonalIntro = true,
                hasBirthday = true,
                notificationsHandledOnThisDevice = true,
            ).canCompleteFreshOnboarding(),
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
}
