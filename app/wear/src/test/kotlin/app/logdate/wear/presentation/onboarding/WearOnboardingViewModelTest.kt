package app.logdate.wear.presentation.onboarding

import app.cash.turbine.test
import app.logdate.wear.v2.phone.HandoffResult
import app.logdate.wear.v2.phone.PhoneHandoff
import app.logdate.wear.v2.phone.PhoneLink
import app.logdate.wear.v2.phone.PhoneLinkRepository
import io.mockk.coEvery
import io.mockk.coVerify
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
    private lateinit var phoneLinks: PhoneLinkRepository
    private lateinit var phoneHandoff: PhoneHandoff

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        phoneLinks = mockk()
        phoneHandoff = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `connected phone offers its setup destination`() =
        runTest {
            coEvery { phoneLinks.current() } returns PhoneLink.Connected("phone-1", "Pixel")
            coEvery { phoneHandoff.open(any(), any()) } returns HandoffResult.SENT

            val viewModel = WearOnboardingViewModel(phoneLinks, phoneHandoff)
            viewModel.phoneCheckState.test {
                assertEquals(PhoneCheckState.Connected("phone-1", "Pixel"), expectMostRecentItem())
            }
            viewModel.openPhoneSetup()

            coVerify(exactly = 1) { phoneHandoff.open("studio.hypertext.logdate://settings/watch", "phone-1") }
            assertEquals(PhoneActionState.SENT, viewModel.phoneActionState.value)
        }

    @Test
    fun `missing phone app offers its Play listing`() =
        runTest {
            coEvery { phoneLinks.current() } returns PhoneLink.AppMissing("phone-1")
            coEvery { phoneHandoff.openAppListing("phone-1") } returns HandoffResult.SENT

            val viewModel = WearOnboardingViewModel(phoneLinks, phoneHandoff)
            assertEquals(PhoneCheckState.AppMissing("phone-1"), viewModel.phoneCheckState.value)
            viewModel.openPhoneSetup()

            coVerify(exactly = 1) { phoneHandoff.openAppListing("phone-1") }
            assertEquals(PhoneActionState.SENT, viewModel.phoneActionState.value)
        }

    @Test
    fun `disconnected phone can be checked again without opening a link`() =
        runTest {
            coEvery { phoneLinks.current() } returnsMany listOf(PhoneLink.NotConnected, PhoneLink.Connected("phone-1", "Pixel"))

            val viewModel = WearOnboardingViewModel(phoneLinks, phoneHandoff)
            assertEquals(PhoneCheckState.NotConnected, viewModel.phoneCheckState.value)
            viewModel.openPhoneSetup()
            coVerify(exactly = 0) { phoneHandoff.open(any(), any()) }

            viewModel.checkPhoneConnection()
            assertEquals(PhoneCheckState.Connected("phone-1", "Pixel"), viewModel.phoneCheckState.value)
        }

    @Test
    fun `unsupported phone remains an optional watch-only path`() =
        runTest {
            coEvery { phoneLinks.current() } returns PhoneLink.Unsupported
            val viewModel = WearOnboardingViewModel(phoneLinks, phoneHandoff)
            assertEquals(PhoneCheckState.Unsupported, viewModel.phoneCheckState.value)
        }
}
