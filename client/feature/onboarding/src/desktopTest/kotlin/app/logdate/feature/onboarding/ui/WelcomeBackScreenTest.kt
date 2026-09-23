package app.logdate.feature.onboarding.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.domain.dayboundary.ObserveHealthConnectStatusUseCase
import app.logdate.client.domain.identity.ObserveUserIdentityUseCase
import app.logdate.client.domain.streak.CalculateStreakUseCase
import app.logdate.client.domain.streak.RefreshStreakUseCase
import app.logdate.feature.onboarding.flow.OnboardingStep
import app.logdate.shared.model.profile.LogDateProfile
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The "sign in with an existing account" onboarding path finishes on [WelcomeBackScreen]. Unlike
 * [OnboardingCompletionScreen] (the brand-new-account path), it used to navigate to Home without
 * ever marking onboarding complete, so returning users were sent right back into onboarding on
 * their next launch despite having a valid, authenticated session.
 */
@OptIn(ExperimentalTestApi::class)
class WelcomeBackScreenTest {
    private lateinit var fakeNotesRepository: FakeJournalNotesRepository
    private lateinit var fakeUserStateRepository: FakeUserStateRepository
    private lateinit var fakeMemoriesSettingsRepository: FakeMemoriesSettingsRepository
    private lateinit var fakeLocationSettingsRepository: FakeLocationTrackingSettingsRepository
    private lateinit var fakeDayBoundarySettingsRepository: FakeDayBoundarySettingsRepository
    private lateinit var fakeHealthRepository: FakeLocalFirstHealthRepository
    private lateinit var fakeProfileRepository: FakeProfileRepository
    private lateinit var fakeAccountRepository: FakeAccountRepository
    private lateinit var fakeSessionStorage: FakeSessionStorage
    private lateinit var fakeStreakSettingsRepository: FakeStreakSettingsRepository
    private lateinit var fakeOnboardingDeviceStateRepository: FakeOnboardingDeviceStateRepository
    private lateinit var identityKeyManager: IdentityKeyManager
    private lateinit var onboardingViewModel: OnboardingViewModel
    private lateinit var welcomeBackViewModel: WelcomeBackViewModel

    @BeforeTest
    fun setup() {
        fakeNotesRepository = FakeJournalNotesRepository()
        fakeUserStateRepository = FakeUserStateRepository()
        fakeMemoriesSettingsRepository = FakeMemoriesSettingsRepository()
        fakeLocationSettingsRepository = FakeLocationTrackingSettingsRepository()
        fakeDayBoundarySettingsRepository = FakeDayBoundarySettingsRepository()
        fakeHealthRepository = FakeLocalFirstHealthRepository()
        fakeProfileRepository = FakeProfileRepository()
        fakeAccountRepository = FakeAccountRepository()
        fakeSessionStorage = FakeSessionStorage()
        fakeStreakSettingsRepository = FakeStreakSettingsRepository()
        fakeOnboardingDeviceStateRepository = FakeOnboardingDeviceStateRepository()
        identityKeyManager = IdentityKeyManager(InMemorySecureStorage(), FakeCryptoManager())
        runBlocking { identityKeyManager.setupNewIdentity() }

        val refreshStreakUseCase =
            RefreshStreakUseCase(
                calculateStreakUseCase = CalculateStreakUseCase(fakeNotesRepository),
                streakSettingsRepository = fakeStreakSettingsRepository,
            )

        onboardingViewModel =
            OnboardingViewModel(
                journalNotesRepository = fakeNotesRepository,
                userStateRepository = fakeUserStateRepository,
                memoriesSettingsRepository = fakeMemoriesSettingsRepository,
                locationTrackingSettingsRepository = fakeLocationSettingsRepository,
                dayBoundarySettingsRepository = fakeDayBoundarySettingsRepository,
                observeHealthConnectStatus = ObserveHealthConnectStatusUseCase(fakeHealthRepository),
                observeUserIdentity =
                    ObserveUserIdentityUseCase(
                        profileRepository = fakeProfileRepository,
                        userStateRepository = fakeUserStateRepository,
                        accountRepository = fakeAccountRepository,
                        sessionStorage = fakeSessionStorage,
                    ),
                onboardingDeviceStateRepository = fakeOnboardingDeviceStateRepository,
                refreshStreakUseCase = refreshStreakUseCase,
                identityKeyManager = identityKeyManager,
            )
        welcomeBackViewModel = WelcomeBackViewModel(refreshStreakUseCase)
    }

    @AfterTest
    fun tearDown() {
        runBlocking { identityKeyManager.clearIdentityKey() }
    }

    @Test
    fun `finishing welcome back marks onboarding complete when all required steps are done`() =
        runComposeUiTest {
            fakeProfileRepository.setProfile(LogDateProfile(displayName = "Alex", bio = "Bio"))
            runBlocking {
                fakeUserStateRepository.setBirthday(Instant.fromEpochMilliseconds(946684800000))
            }
            runBlocking {
                fakeOnboardingDeviceStateRepository.markRecommendationsHandled()
                fakeOnboardingDeviceStateRepository.markLocationHandled()
                fakeOnboardingDeviceStateRepository.markDayBoundariesHandled()
                fakeOnboardingDeviceStateRepository.markNotificationsHandled()
            }

            var finished = false
            var incompleteStep: OnboardingStep? = null

            setContent {
                WelcomeBackScreen(
                    onFinish = { finished = true },
                    onRequirementsIncomplete = { incompleteStep = it },
                    viewModel = welcomeBackViewModel,
                    onboardingViewModel = onboardingViewModel,
                )
            }

            waitUntil(timeoutMillis = 10_000) { finished || incompleteStep != null }

            assertTrue(finished, "expected onFinish to be called")
            assertNull(incompleteStep)
            assertTrue(fakeUserStateRepository.isOnboardingComplete)
        }

    @Test
    fun `finishing welcome back routes to the missing step instead of finishing`() =
        runComposeUiTest {
            // Birthday deliberately left unset.
            fakeProfileRepository.setProfile(LogDateProfile(displayName = "Alex", bio = "Bio"))
            runBlocking {
                fakeOnboardingDeviceStateRepository.markRecommendationsHandled()
                fakeOnboardingDeviceStateRepository.markLocationHandled()
                fakeOnboardingDeviceStateRepository.markDayBoundariesHandled()
                fakeOnboardingDeviceStateRepository.markNotificationsHandled()
            }

            var finished = false
            var incompleteStep: OnboardingStep? = null

            setContent {
                WelcomeBackScreen(
                    onFinish = { finished = true },
                    onRequirementsIncomplete = { incompleteStep = it },
                    viewModel = welcomeBackViewModel,
                    onboardingViewModel = onboardingViewModel,
                )
            }

            waitUntil(timeoutMillis = 10_000) { finished || incompleteStep != null }

            assertEquals(OnboardingStep.BIRTHDAY, incompleteStep)
            assertTrue(!finished, "onFinish must not be called when required steps are incomplete")
            assertTrue(!fakeUserStateRepository.isOnboardingComplete)
        }
}
