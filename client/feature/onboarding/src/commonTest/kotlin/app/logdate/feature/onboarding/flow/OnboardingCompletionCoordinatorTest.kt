package app.logdate.feature.onboarding.flow

import app.logdate.feature.onboarding.ui.FakeAccountRepository
import app.logdate.feature.onboarding.ui.FakeDayBoundarySettingsRepository
import app.logdate.feature.onboarding.ui.FakeJournalNotesRepository
import app.logdate.feature.onboarding.ui.FakeLocalFirstHealthRepository
import app.logdate.feature.onboarding.ui.FakeLocationTrackingSettingsRepository
import app.logdate.feature.onboarding.ui.FakeMemoriesSettingsRepository
import app.logdate.feature.onboarding.ui.FakeOnboardingDeviceStateRepository
import app.logdate.feature.onboarding.ui.FakeProfileRepository
import app.logdate.feature.onboarding.ui.FakeSessionStorage
import app.logdate.feature.onboarding.ui.FakeStreakSettingsRepository
import app.logdate.feature.onboarding.ui.FakeUserStateRepository
import app.logdate.feature.onboarding.ui.buildOnboardingCompletionCoordinator
import app.logdate.shared.model.profile.LogDateProfile
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Leaving onboarding while `is_onboarded` is still false puts the user on Home only until the
 * activity is next recreated, at which point the launch bootstrap sends them back to onboarding.
 * Finishing must therefore depend on the flag actually being saved.
 */
class OnboardingCompletionCoordinatorTest {
    private val userStateRepository = FakeUserStateRepository()
    private val profileRepository = FakeProfileRepository()
    private val onboardingDeviceStateRepository = FakeOnboardingDeviceStateRepository()

    private val coordinator =
        buildOnboardingCompletionCoordinator(
            notesRepository = FakeJournalNotesRepository(),
            userStateRepository = userStateRepository,
            memoriesSettingsRepository = FakeMemoriesSettingsRepository(),
            locationSettingsRepository = FakeLocationTrackingSettingsRepository(),
            dayBoundarySettingsRepository = FakeDayBoundarySettingsRepository(),
            healthRepository = FakeLocalFirstHealthRepository(),
            profileRepository = profileRepository,
            accountRepository = FakeAccountRepository(),
            sessionStorage = FakeSessionStorage(),
            streakSettingsRepository = FakeStreakSettingsRepository(),
            onboardingDeviceStateRepository = onboardingDeviceStateRepository,
        )

    @Test
    fun `finishing saves the onboarded flag`() =
        runTest {
            completeRequiredSteps()

            val result = coordinator.finishOnboarding()

            assertEquals(OnboardingFinishResult.Finished, result)
            assertTrue(userStateRepository.isOnboardingComplete)
        }

    @Test
    fun `a failed save reports the failure instead of finishing`() =
        runTest {
            completeRequiredSteps()
            val failure = IllegalStateException("disk full")
            userStateRepository.setOnboardingCompleteFailure = failure

            val result = coordinator.finishOnboarding()

            val saveFailed = assertIs<OnboardingFinishResult.SaveFailed>(result)
            assertSame(failure, saveFailed.error)
            assertFalse(userStateRepository.isOnboardingComplete)
        }

    @Test
    fun `an incomplete required step is reported without saving`() =
        runTest {
            completeRequiredSteps()
            profileRepository.setProfile(LogDateProfile(displayName = ""))

            val result = coordinator.finishOnboarding()

            assertEquals(OnboardingFinishResult.IncompleteStep(OnboardingStep.PERSONAL_INTRO), result)
            assertFalse(userStateRepository.isOnboardingComplete)
        }

    private suspend fun completeRequiredSteps() {
        profileRepository.setProfile(LogDateProfile(displayName = "Alex"))
        userStateRepository.setBirthday(Instant.fromEpochMilliseconds(946_684_800_000))
        onboardingDeviceStateRepository.markRecommendationsHandled()
        onboardingDeviceStateRepository.markLocationHandled()
        onboardingDeviceStateRepository.markDayBoundariesHandled()
        onboardingDeviceStateRepository.markNotificationsHandled()
    }
}
