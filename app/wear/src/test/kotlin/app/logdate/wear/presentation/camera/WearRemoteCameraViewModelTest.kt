package app.logdate.wear.presentation.camera

import app.logdate.wear.sync.WearDataLayerClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WearRemoteCameraViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dataLayerClient: WearDataLayerClient

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dataLayerClient = mockk(relaxed = true)
        coEvery { dataLayerClient.isPhoneConnected(any()) } returns true
        coEvery { dataLayerClient.sendMessage(any(), any()) } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): WearRemoteCameraViewModel {
        return WearRemoteCameraViewModel(dataLayerClient)
    }

    // -----------------------------------------------------------------------
    // Initial state
    // -----------------------------------------------------------------------

    @Test
    fun `initial state is Idle`() = runTest {
        val viewModel = createViewModel()
        assertEquals(RemoteCameraPhase.IDLE, viewModel.uiState.value.phase)
    }

    // -----------------------------------------------------------------------
    // requestCamera -- success
    // -----------------------------------------------------------------------

    @Test
    fun `requestCamera transitions to REQUESTING`() = runTest {
        // Delay the phone check so we can observe the intermediate REQUESTING state
        coEvery { dataLayerClient.isPhoneConnected(any()) } coAnswers { delay(500); true }
        val viewModel = createViewModel()

        viewModel.requestCamera()
        advanceTimeBy(50)

        assertEquals(RemoteCameraPhase.REQUESTING, viewModel.uiState.value.phase)
    }

    @Test
    fun `requestCamera checks phone connectivity`() = runTest {
        val viewModel = createViewModel()

        viewModel.requestCamera()
        advanceUntilIdle()

        coVerify { dataLayerClient.isPhoneConnected(any()) }
    }

    @Test
    fun `requestCamera sends open message to phone`() = runTest {
        val viewModel = createViewModel()

        viewModel.requestCamera()
        advanceUntilIdle()

        coVerify { dataLayerClient.sendMessage("/logdate/camera/open", any()) }
    }

    @Test
    fun `requestCamera transitions to READY after sending`() = runTest {
        val viewModel = createViewModel()

        viewModel.requestCamera()
        advanceUntilIdle()

        assertEquals(RemoteCameraPhase.READY, viewModel.uiState.value.phase)
    }

    // -----------------------------------------------------------------------
    // requestCamera -- phone not connected
    // -----------------------------------------------------------------------

    @Test
    fun `requestCamera transitions to ERROR when phone not connected`() = runTest {
        coEvery { dataLayerClient.isPhoneConnected(any()) } returns false
        val viewModel = createViewModel()

        viewModel.requestCamera()
        advanceUntilIdle()

        assertEquals(RemoteCameraPhase.ERROR, viewModel.uiState.value.phase)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `requestCamera does not send message when phone not connected`() = runTest {
        coEvery { dataLayerClient.isPhoneConnected(any()) } returns false
        val viewModel = createViewModel()

        viewModel.requestCamera()
        advanceUntilIdle()

        coVerify(exactly = 0) { dataLayerClient.sendMessage(any(), any()) }
    }

    // -----------------------------------------------------------------------
    // requestCamera -- message fails
    // -----------------------------------------------------------------------

    @Test
    fun `requestCamera transitions to ERROR when message fails`() = runTest {
        coEvery { dataLayerClient.sendMessage(any(), any()) } returns false
        val viewModel = createViewModel()

        viewModel.requestCamera()
        advanceUntilIdle()

        assertEquals(RemoteCameraPhase.ERROR, viewModel.uiState.value.phase)
    }

    // -----------------------------------------------------------------------
    // capture -- success
    // -----------------------------------------------------------------------

    @Test
    fun `capture sends capture message to phone`() = runTest {
        val viewModel = createViewModel()
        viewModel.requestCamera()
        advanceUntilIdle()

        viewModel.capture()
        advanceUntilIdle()

        coVerify { dataLayerClient.sendMessage("/logdate/camera/capture", any()) }
    }

    @Test
    fun `capture transitions to CAPTURING`() = runTest {
        val viewModel = createViewModel()
        viewModel.requestCamera()
        advanceUntilIdle()

        // Delay the capture message so we can observe the intermediate CAPTURING state
        coEvery { dataLayerClient.sendMessage("/logdate/camera/capture", any()) } coAnswers { delay(500); true }
        viewModel.capture()
        advanceTimeBy(50)

        assertEquals(RemoteCameraPhase.CAPTURING, viewModel.uiState.value.phase)
    }

    @Test
    fun `capture transitions to PREVIEW after message sent`() = runTest {
        val viewModel = createViewModel()
        viewModel.requestCamera()
        advanceUntilIdle()

        viewModel.capture()
        advanceUntilIdle()

        assertEquals(RemoteCameraPhase.PREVIEW, viewModel.uiState.value.phase)
    }

    // -----------------------------------------------------------------------
    // capture -- guards
    // -----------------------------------------------------------------------

    @Test
    fun `capture is ignored when not in READY phase`() = runTest {
        val viewModel = createViewModel()

        viewModel.capture()
        advanceUntilIdle()

        coVerify(exactly = 0) { dataLayerClient.sendMessage("/logdate/camera/capture", any()) }
    }

    // -----------------------------------------------------------------------
    // capture -- message fails
    // -----------------------------------------------------------------------

    @Test
    fun `capture transitions to ERROR when message fails`() = runTest {
        coEvery { dataLayerClient.sendMessage("/logdate/camera/open", any()) } returns true
        coEvery { dataLayerClient.sendMessage("/logdate/camera/capture", any()) } returns false
        val viewModel = createViewModel()
        viewModel.requestCamera()
        advanceUntilIdle()

        viewModel.capture()
        advanceUntilIdle()

        assertEquals(RemoteCameraPhase.ERROR, viewModel.uiState.value.phase)
    }

    // -----------------------------------------------------------------------
    // captureMore -- returns to READY
    // -----------------------------------------------------------------------

    @Test
    fun `captureMore transitions back to READY`() = runTest {
        val viewModel = createViewModel()
        viewModel.requestCamera()
        advanceUntilIdle()
        viewModel.capture()
        advanceUntilIdle()
        assertEquals(RemoteCameraPhase.PREVIEW, viewModel.uiState.value.phase)

        viewModel.captureMore()
        advanceUntilIdle()

        assertEquals(RemoteCameraPhase.READY, viewModel.uiState.value.phase)
    }

    @Test
    fun `captureMore is ignored when not in PREVIEW phase`() = runTest {
        val viewModel = createViewModel()

        viewModel.captureMore()
        advanceUntilIdle()

        assertEquals(RemoteCameraPhase.IDLE, viewModel.uiState.value.phase)
    }

    // -----------------------------------------------------------------------
    // dismiss -- returns to IDLE
    // -----------------------------------------------------------------------

    @Test
    fun `dismiss sends close message and returns to IDLE`() = runTest {
        val viewModel = createViewModel()
        viewModel.requestCamera()
        advanceUntilIdle()

        viewModel.dismiss()
        advanceUntilIdle()

        coVerify { dataLayerClient.sendMessage("/logdate/camera/close", any()) }
        assertEquals(RemoteCameraPhase.IDLE, viewModel.uiState.value.phase)
    }

    @Test
    fun `dismiss from PREVIEW returns to IDLE`() = runTest {
        val viewModel = createViewModel()
        viewModel.requestCamera()
        advanceUntilIdle()
        viewModel.capture()
        advanceUntilIdle()

        viewModel.dismiss()
        advanceUntilIdle()

        assertEquals(RemoteCameraPhase.IDLE, viewModel.uiState.value.phase)
    }

    @Test
    fun `dismiss from ERROR returns to IDLE`() = runTest {
        coEvery { dataLayerClient.isPhoneConnected(any()) } returns false
        val viewModel = createViewModel()
        viewModel.requestCamera()
        advanceUntilIdle()
        assertEquals(RemoteCameraPhase.ERROR, viewModel.uiState.value.phase)

        viewModel.dismiss()
        advanceUntilIdle()

        assertEquals(RemoteCameraPhase.IDLE, viewModel.uiState.value.phase)
    }

    @Test
    fun `dismiss clears error message`() = runTest {
        coEvery { dataLayerClient.isPhoneConnected(any()) } returns false
        val viewModel = createViewModel()
        viewModel.requestCamera()
        advanceUntilIdle()

        viewModel.dismiss()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.errorMessage)
    }

    // -----------------------------------------------------------------------
    // requestCamera -- guard against duplicate requests
    // -----------------------------------------------------------------------

    @Test
    fun `requestCamera is ignored when already in READY phase`() = runTest {
        val viewModel = createViewModel()

        viewModel.requestCamera()
        advanceUntilIdle()
        assertEquals(RemoteCameraPhase.READY, viewModel.uiState.value.phase)

        viewModel.requestCamera() // should be ignored
        advanceUntilIdle()

        coVerify(exactly = 1) { dataLayerClient.sendMessage("/logdate/camera/open", any()) }
    }

    // -----------------------------------------------------------------------
    // navigateBack flag
    // -----------------------------------------------------------------------

    @Test
    fun `dismiss sets navigateBack to true`() = runTest {
        val viewModel = createViewModel()

        viewModel.dismiss()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.navigateBack)
    }
}
