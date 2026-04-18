package app.logdate.wear.presentation.onboarding

import app.cash.turbine.test
import app.logdate.wear.sync.WearDataLayerClient
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class WearOnboardingViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var dataLayerClient: WearDataLayerClient

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dataLayerClient = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `emits Connected when phone is reachable`() =
        runTest {
            coEvery { dataLayerClient.isPhoneConnected(any()) } returns true

            val viewModel = WearOnboardingViewModel(dataLayerClient)

            viewModel.phoneCheckState.test {
                assertEquals(PhoneCheckState.Connected, expectMostRecentItem())
            }
        }

    @Test
    fun `emits NotConnected when phone is not reachable`() =
        runTest {
            coEvery { dataLayerClient.isPhoneConnected(any()) } returns false

            val viewModel = WearOnboardingViewModel(dataLayerClient)

            viewModel.phoneCheckState.test {
                assertEquals(PhoneCheckState.NotConnected, expectMostRecentItem())
            }
        }

    @Test
    fun `emits NotConnected when check throws exception`() =
        runTest {
            coEvery { dataLayerClient.isPhoneConnected(any()) } throws RuntimeException("no gms")

            val viewModel = WearOnboardingViewModel(dataLayerClient)

            viewModel.phoneCheckState.test {
                assertEquals(PhoneCheckState.NotConnected, expectMostRecentItem())
            }
        }

    @Test
    fun `checkPhoneConnection retries the check`() =
        runTest {
            coEvery { dataLayerClient.isPhoneConnected(any()) } returns false

            val viewModel = WearOnboardingViewModel(dataLayerClient)

            viewModel.phoneCheckState.test {
                assertEquals(PhoneCheckState.NotConnected, expectMostRecentItem())
            }

            // Now the phone becomes available
            coEvery { dataLayerClient.isPhoneConnected(any()) } returns true
            viewModel.checkPhoneConnection()

            viewModel.phoneCheckState.test {
                assertEquals(PhoneCheckState.Connected, expectMostRecentItem())
            }
        }
}
