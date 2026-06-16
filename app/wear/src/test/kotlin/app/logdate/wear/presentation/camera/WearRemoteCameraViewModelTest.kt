package app.logdate.wear.presentation.camera

import app.logdate.client.media.device.MediaDeviceCategory
import app.logdate.client.media.device.MediaDeviceKind
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.client.media.device.MediaDeviceUiState
import app.logdate.client.sync.datalayer.RemoteCameraCaptureResult
import app.logdate.wear.sync.WearDataLayerClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

/**
 * Tests the [WearRemoteCameraViewModel], which facilitates remote control of the phone's camera
 * from a Wear OS device.
 *
 * The suite covers the coordination between the watch and phone via the Wear Data Layer,
 * ensuring that requests to open the camera, capture images, and dismiss the remote session
 * are correctly messaged and that the UI state accurately reflects the remote operation's progress.
 */
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

    private fun createViewModel(
        cameraDeviceStore: RemoteCameraDeviceStore = TestRemoteCameraDeviceStore(),
        captureResultStore: RemoteCameraCaptureResultStore = TestRemoteCameraCaptureResultStore(),
    ): WearRemoteCameraViewModel =
        WearRemoteCameraViewModel(
            dataLayerClient = dataLayerClient,
            cameraDeviceStore = cameraDeviceStore,
            captureResultStore = captureResultStore,
        )

    // -----------------------------------------------------------------------
    // Initial state
    // -----------------------------------------------------------------------

    @Test
    fun `initial state is Idle`() =
        runTest {
            val viewModel = createViewModel()
            assertEquals(RemoteCameraPhase.IDLE, viewModel.uiState.value.phase)
        }

    // -----------------------------------------------------------------------
    // requestCamera -- success
    // -----------------------------------------------------------------------

    @Test
    fun `requestCamera transitions to REQUESTING`() =
        runTest {
            // Delay the phone check so we can observe the intermediate REQUESTING state
            coEvery { dataLayerClient.isPhoneConnected(any()) } coAnswers {
                delay(500)
                true
            }
            val viewModel = createViewModel()

            viewModel.requestCamera()
            advanceTimeBy(50)

            assertEquals(RemoteCameraPhase.REQUESTING, viewModel.uiState.value.phase)
        }

    @Test
    fun `requestCamera checks phone connectivity`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.requestCamera()
            advanceUntilIdle()

            coVerify { dataLayerClient.isPhoneConnected(any()) }
        }

    @Test
    fun `requestCamera sends open message to phone`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.requestCamera()
            advanceUntilIdle()

            coVerify { dataLayerClient.sendMessage("/logdate/camera/open", any()) }
        }

    @Test
    fun `requestCamera transitions to READY after sending`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.requestCamera()
            advanceUntilIdle()

            assertEquals(RemoteCameraPhase.READY, viewModel.uiState.value.phase)
        }

    // -----------------------------------------------------------------------
    // requestCamera -- phone not connected
    // -----------------------------------------------------------------------

    @Test
    fun `requestCamera transitions to ERROR when phone not connected`() =
        runTest {
            coEvery { dataLayerClient.isPhoneConnected(any()) } returns false
            val viewModel = createViewModel()

            viewModel.requestCamera()
            advanceUntilIdle()

            assertEquals(RemoteCameraPhase.ERROR, viewModel.uiState.value.phase)
            assertNotNull(viewModel.uiState.value.errorMessage)
        }

    @Test
    fun `requestCamera does not send message when phone not connected`() =
        runTest {
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
    fun `requestCamera transitions to ERROR when message fails`() =
        runTest {
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
    fun `capture sends capture message to phone`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.requestCamera()
            advanceUntilIdle()

            viewModel.capture()
            advanceUntilIdle()

            coVerify { dataLayerClient.sendMessage("/logdate/camera/capture", any()) }
        }

    @Test
    fun `capture transitions to CAPTURING`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.requestCamera()
            advanceUntilIdle()

            // Delay the capture message so we can observe the intermediate CAPTURING state
            coEvery { dataLayerClient.sendMessage("/logdate/camera/capture", any()) } coAnswers {
                delay(500)
                true
            }
            viewModel.capture()
            advanceTimeBy(50)

            assertEquals(RemoteCameraPhase.CAPTURING, viewModel.uiState.value.phase)
        }

    @Test
    fun `capture remains CAPTURING until phone confirms save`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.requestCamera()
            advanceUntilIdle()

            viewModel.capture()
            advanceUntilIdle()

            assertEquals(RemoteCameraPhase.CAPTURING, viewModel.uiState.value.phase)
        }

    @Test
    fun `capture transitions to PREVIEW when phone confirms save`() =
        runTest {
            val captureResultStore = TestRemoteCameraCaptureResultStore()
            val viewModel = createViewModel(captureResultStore = captureResultStore)
            viewModel.requestCamera()
            advanceUntilIdle()
            viewModel.capture()
            advanceUntilIdle()

            captureResultStore.publish(
                RemoteCameraCaptureResult(
                    isSaved = true,
                    message = "Remote photo saved",
                    mediaType = "photo",
                ),
            )
            advanceUntilIdle()

            assertEquals(RemoteCameraPhase.PREVIEW, viewModel.uiState.value.phase)
            assertEquals("Remote photo saved", viewModel.uiState.value.captureStatusMessage)
        }

    @Test
    fun `capture transitions to ERROR when phone confirms failure`() =
        runTest {
            val captureResultStore = TestRemoteCameraCaptureResultStore()
            val viewModel = createViewModel(captureResultStore = captureResultStore)
            viewModel.requestCamera()
            advanceUntilIdle()
            viewModel.capture()
            advanceUntilIdle()

            captureResultStore.publish(
                RemoteCameraCaptureResult(
                    isSaved = false,
                    message = "Could not save remote camera capture",
                ),
            )
            advanceUntilIdle()

            assertEquals(RemoteCameraPhase.ERROR, viewModel.uiState.value.phase)
            assertEquals("Could not save remote camera capture", viewModel.uiState.value.errorMessage)
        }

    // -----------------------------------------------------------------------
    // capture -- guards
    // -----------------------------------------------------------------------

    @Test
    fun `capture is ignored when not in READY phase`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.capture()
            advanceUntilIdle()

            coVerify(exactly = 0) { dataLayerClient.sendMessage("/logdate/camera/capture", any()) }
        }

    // -----------------------------------------------------------------------
    // capture -- message fails
    // -----------------------------------------------------------------------

    @Test
    fun `capture transitions to ERROR when message fails`() =
        runTest {
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
    fun `captureMore transitions back to READY`() =
        runTest {
            val captureResultStore = TestRemoteCameraCaptureResultStore()
            val viewModel = createViewModel(captureResultStore = captureResultStore)
            viewModel.requestCamera()
            advanceUntilIdle()
            viewModel.capture()
            advanceUntilIdle()
            captureResultStore.publish(
                RemoteCameraCaptureResult(
                    isSaved = true,
                    message = "Remote photo saved",
                    mediaType = "photo",
                ),
            )
            advanceUntilIdle()
            assertEquals(RemoteCameraPhase.PREVIEW, viewModel.uiState.value.phase)

            viewModel.captureMore()
            advanceUntilIdle()

            assertEquals(RemoteCameraPhase.READY, viewModel.uiState.value.phase)
        }

    @Test
    fun `captureMore is ignored when not in PREVIEW phase`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.captureMore()
            advanceUntilIdle()

            assertEquals(RemoteCameraPhase.IDLE, viewModel.uiState.value.phase)
        }

    // -----------------------------------------------------------------------
    // camera selection
    // -----------------------------------------------------------------------

    @Test
    fun `selectFrontCamera sends front selection and updates label`() =
        runTest {
            var selectedPayload: ByteArray? = null
            coEvery { dataLayerClient.sendMessage("/logdate/camera/select", any()) } coAnswers {
                selectedPayload = secondArg()
                true
            }
            val viewModel = createViewModel()
            viewModel.requestCamera()
            advanceUntilIdle()

            viewModel.selectFrontCamera()
            advanceUntilIdle()

            coVerify { dataLayerClient.sendMessage("/logdate/camera/select", any()) }
            assertEquals("front", selectedPayload?.decodeToString())
            assertEquals("Front camera", viewModel.uiState.value.selectedCameraLabel)
        }

    @Test
    fun `selectBackCamera sends back selection and updates label`() =
        runTest {
            var selectedPayload: ByteArray? = null
            coEvery { dataLayerClient.sendMessage("/logdate/camera/select", any()) } coAnswers {
                selectedPayload = secondArg()
                true
            }
            val viewModel = createViewModel()
            viewModel.requestCamera()
            advanceUntilIdle()

            viewModel.selectBackCamera()
            advanceUntilIdle()

            coVerify { dataLayerClient.sendMessage("/logdate/camera/select", any()) }
            assertEquals("back", selectedPayload?.decodeToString())
            assertEquals("Back camera", viewModel.uiState.value.selectedCameraLabel)
        }

    @Test
    fun `selectCameraDevice sends exact phone camera device id and updates selected row`() =
        runTest {
            val cameraSelection =
                MediaDeviceSelectionUiState(
                    kind = MediaDeviceKind.CAMERA,
                    devices =
                        listOf(
                            MediaDeviceUiState(
                                id = "camera-back",
                                label = "Back camera",
                                kind = MediaDeviceKind.CAMERA,
                                category = MediaDeviceCategory.BACK_CAMERA,
                            ),
                            MediaDeviceUiState(
                                id = "usb-camera-1",
                                label = "USB document camera",
                                kind = MediaDeviceKind.CAMERA,
                                category = MediaDeviceCategory.USB,
                                isExternal = true,
                            ),
                        ),
                    selectedDeviceId = "camera-back",
                )
            val cameraStore =
                object : RemoteCameraDeviceStore {
                    override val cameraSelection = MutableStateFlow(cameraSelection)
                }
            var selectedPayload: ByteArray? = null
            coEvery { dataLayerClient.sendMessage("/logdate/camera/select", any()) } coAnswers {
                selectedPayload = secondArg()
                true
            }
            val viewModel = createViewModel(cameraDeviceStore = cameraStore)
            viewModel.requestCamera()
            advanceUntilIdle()

            viewModel.selectCameraDevice("usb-camera-1")
            advanceUntilIdle()

            coVerify { dataLayerClient.sendMessage("/logdate/camera/select", any()) }
            assertEquals("device:usb-camera-1", selectedPayload?.decodeToString())
            assertEquals("USB document camera", viewModel.uiState.value.selectedCameraLabel)
            assertEquals("usb-camera-1", viewModel.uiState.value.selectedCameraDeviceId)
        }

    @Test
    fun `camera store updates selected label and available cameras`() =
        runTest {
            val cameraStore = TestRemoteCameraDeviceStore()
            val viewModel = createViewModel(cameraDeviceStore = cameraStore)
            viewModel.requestCamera()
            advanceUntilIdle()

            val externalSelection =
                MediaDeviceSelectionUiState(
                    kind = MediaDeviceKind.CAMERA,
                    devices =
                        listOf(
                            MediaDeviceUiState(
                                id = "camera-back",
                                label = "Back camera",
                                kind = MediaDeviceKind.CAMERA,
                                category = MediaDeviceCategory.BACK_CAMERA,
                            ),
                            MediaDeviceUiState(
                                id = "usb-camera-1",
                                label = "USB document camera",
                                kind = MediaDeviceKind.CAMERA,
                                category = MediaDeviceCategory.USB,
                                isExternal = true,
                            ),
                        ),
                    selectedDeviceId = "usb-camera-1",
                )

            cameraStore.update(externalSelection)
            advanceUntilIdle()

            assertEquals("USB document camera", viewModel.uiState.value.selectedCameraLabel)
            assertEquals("usb-camera-1", viewModel.uiState.value.selectedCameraDeviceId)
            assertEquals(2, viewModel.uiState.value.availableCameras.size)
            assertEquals("usb-camera-1", viewModel.uiState.value.availableCameras[1].id)
        }

    @Test
    fun `switchCamera sends switch message and toggles label`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.requestCamera()
            advanceUntilIdle()

            viewModel.switchCamera()
            advanceUntilIdle()

            coVerify { dataLayerClient.sendMessage("/logdate/camera/switch", any()) }
            assertEquals("Front camera", viewModel.uiState.value.selectedCameraLabel)
        }

    @Test
    fun `camera selection is ignored when not ready`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.selectFrontCamera()
            viewModel.switchCamera()
            advanceUntilIdle()

            coVerify(exactly = 0) { dataLayerClient.sendMessage("/logdate/camera/select", any()) }
            coVerify(exactly = 0) { dataLayerClient.sendMessage("/logdate/camera/switch", any()) }
        }

    // -----------------------------------------------------------------------
    // dismiss -- returns to IDLE
    // -----------------------------------------------------------------------

    @Test
    fun `dismiss sends close message and returns to IDLE`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.requestCamera()
            advanceUntilIdle()

            viewModel.dismiss()
            advanceUntilIdle()

            coVerify { dataLayerClient.sendMessage("/logdate/camera/close", any()) }
            assertEquals(RemoteCameraPhase.IDLE, viewModel.uiState.value.phase)
        }

    @Test
    fun `dismiss from PREVIEW returns to IDLE`() =
        runTest {
            val captureResultStore = TestRemoteCameraCaptureResultStore()
            val viewModel = createViewModel(captureResultStore = captureResultStore)
            viewModel.requestCamera()
            advanceUntilIdle()
            viewModel.capture()
            advanceUntilIdle()
            captureResultStore.publish(
                RemoteCameraCaptureResult(
                    isSaved = true,
                    message = "Remote photo saved",
                    mediaType = "photo",
                ),
            )
            advanceUntilIdle()
            assertEquals(RemoteCameraPhase.PREVIEW, viewModel.uiState.value.phase)

            viewModel.dismiss()
            advanceUntilIdle()

            assertEquals(RemoteCameraPhase.IDLE, viewModel.uiState.value.phase)
        }

    @Test
    fun `dismiss from ERROR returns to IDLE`() =
        runTest {
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
    fun `dismiss clears error message`() =
        runTest {
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
    fun `requestCamera is ignored when already in READY phase`() =
        runTest {
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
    fun `dismiss sets navigateBack to true`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.dismiss()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.navigateBack)
        }

    private class TestRemoteCameraDeviceStore(
        initialSelection: MediaDeviceSelectionUiState = defaultRemoteCameraSelection(),
    ) : RemoteCameraDeviceStore {
        private val state = MutableStateFlow(initialSelection)
        override val cameraSelection = state

        fun update(selection: MediaDeviceSelectionUiState) {
            state.value = selection
        }
    }

    private class TestRemoteCameraCaptureResultStore : RemoteCameraCaptureResultStore {
        private val results = MutableSharedFlow<RemoteCameraCaptureResult>(extraBufferCapacity = 1)
        override val captureResults = results.asSharedFlow()

        fun publish(result: RemoteCameraCaptureResult) {
            results.tryEmit(result)
        }
    }
}
