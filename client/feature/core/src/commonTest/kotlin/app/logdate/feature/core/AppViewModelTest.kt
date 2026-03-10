package app.logdate.feature.core

import app.logdate.client.networking.NetworkAvailabilityMonitor
import app.logdate.client.networking.NetworkState
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.shared.model.user.UserData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ui state becomes loaded with stable initial online network state`() =
        runTest {
            val viewModel =
                AppViewModel(
                    userStateRepository = FakeUserStateRepository(UserData(isOnboarded = true)),
                    biometricGatekeeper = FakeBiometricGatekeeper(),
                    networkMonitor = FakeNetworkAvailabilityMonitor(isAvailable = true),
                )

            advanceUntilIdle()

            val state = assertIs<GlobalAppUiLoadedState>(viewModel.uiState.value)
            assertEquals(true, state.isOnboarded)
            assertEquals(true, state.isOnline)
        }

    @Test
    fun `ui state becomes loaded with stable initial offline network state`() =
        runTest {
            val viewModel =
                AppViewModel(
                    userStateRepository = FakeUserStateRepository(UserData(isOnboarded = false)),
                    biometricGatekeeper = FakeBiometricGatekeeper(),
                    networkMonitor = FakeNetworkAvailabilityMonitor(isAvailable = false),
                )

            advanceUntilIdle()

            val state = assertIs<GlobalAppUiLoadedState>(viewModel.uiState.value)
            assertEquals(false, state.isOnboarded)
            assertEquals(false, state.isOnline)
        }

    private class FakeUserStateRepository(
        initialValue: UserData,
    ) : UserStateRepository {
        override val userData: StateFlow<UserData> = MutableStateFlow(initialValue)

        override suspend fun setBirthday(birthday: Instant) {}

        override suspend fun setIsOnboardingComplete(isComplete: Boolean) {}

        override suspend fun setBiometricEnabled(isEnabled: Boolean) {}

        override suspend fun addFavoriteNote(vararg noteId: String) {}
    }

    private class FakeBiometricGatekeeper : BiometricGatekeeper {
        override val authState: StateFlow<AppAuthState> = MutableStateFlow(AppAuthState.NO_PROMPT_NEEDED)

        override fun authenticate(
            title: String,
            subtitle: String,
            cancelLabel: String,
            requireConfirmation: Boolean,
            requestEnrollmentIfNecessary: Boolean,
            description: String?,
        ) {
        }

        override fun requestEnrollment() {
        }
    }

    private class FakeNetworkAvailabilityMonitor(
        private val isAvailable: Boolean,
    ) : NetworkAvailabilityMonitor {
        private val networkState =
            MutableStateFlow<NetworkState>(
                if (isAvailable) {
                    NetworkState.Connected(Clock.System.now())
                } else {
                    NetworkState.NotConnected(Clock.System.now())
                },
            )

        override fun isNetworkAvailable(): Boolean = isAvailable

        override fun observeNetwork(): SharedFlow<NetworkState> = networkState
    }
}
