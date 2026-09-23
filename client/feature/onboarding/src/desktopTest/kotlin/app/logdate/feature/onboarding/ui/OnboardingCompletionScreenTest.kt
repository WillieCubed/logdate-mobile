package app.logdate.feature.onboarding.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.feature.core.streak.CampfireViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * The last onboarding screen used to return with nothing rendered while it waited on the
 * campfire feature flag's first emission (`val showCampfire = isCampfireEnabled ?: return`),
 * showing a blank frame instead of a loading state.
 */
@OptIn(ExperimentalTestApi::class)
class OnboardingCompletionScreenTest {
    private lateinit var onboardingViewModel: OnboardingViewModel

    @BeforeTest
    fun setup() {
        val fakeNotesRepository = FakeJournalNotesRepository()
        val fakeStreakSettingsRepository = FakeStreakSettingsRepository()
        val identityKeyManager = IdentityKeyManager(InMemorySecureStorage(), FakeCryptoManager())
        runBlocking { identityKeyManager.setupNewIdentity() }

        onboardingViewModel =
            buildOnboardingViewModel(
                notesRepository = fakeNotesRepository,
                userStateRepository = FakeUserStateRepository(),
                memoriesSettingsRepository = FakeMemoriesSettingsRepository(),
                locationSettingsRepository = FakeLocationTrackingSettingsRepository(),
                dayBoundarySettingsRepository = FakeDayBoundarySettingsRepository(),
                healthRepository = FakeLocalFirstHealthRepository(),
                profileRepository = FakeProfileRepository(),
                accountRepository = FakeAccountRepository(),
                sessionStorage = FakeSessionStorage(),
                streakSettingsRepository = fakeStreakSettingsRepository,
                onboardingDeviceStateRepository = FakeOnboardingDeviceStateRepository(),
                identityKeyManager = identityKeyManager,
            )
    }

    @Test
    fun `shows a loading state instead of a blank frame while the campfire flag is unresolved`() =
        runComposeUiTest {
            // Never emits, so isCampfireEnabled stays at its initial null value.
            val campfireViewModel = CampfireViewModel(campfireFlow = MutableSharedFlow(), campfireEnabledFlow = MutableSharedFlow())

            setContent {
                OnboardingCompletionScreen(
                    onFinish = {},
                    viewModel = onboardingViewModel,
                    campfireViewModel = campfireViewModel,
                )
            }

            onNodeWithTag(ONBOARDING_COMPLETION_LOADING_TAG).assertExists()
            onNodeWithTag(ONBOARDING_COMPLETION_ROOT_TAG).assertDoesNotExist()
        }

    @Test
    fun `shows content once the campfire flag resolves`() =
        runComposeUiTest {
            val campfireViewModel =
                CampfireViewModel(
                    campfireFlow = MutableSharedFlow(),
                    campfireEnabledFlow = MutableStateFlow(false),
                )

            setContent {
                OnboardingCompletionScreen(
                    onFinish = {},
                    viewModel = onboardingViewModel,
                    campfireViewModel = campfireViewModel,
                )
            }

            onNodeWithTag(ONBOARDING_COMPLETION_LOADING_TAG).assertDoesNotExist()
            onNodeWithTag(ONBOARDING_COMPLETION_ROOT_TAG).assertExists()
        }
}
