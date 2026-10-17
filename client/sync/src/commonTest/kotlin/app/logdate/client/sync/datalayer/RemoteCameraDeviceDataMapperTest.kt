package app.logdate.client.sync.datalayer

import app.logdate.client.media.device.MediaDeviceCategory
import app.logdate.client.media.device.MediaDeviceKind
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.client.media.device.MediaDeviceUiState
import app.logdate.client.text.toUiText
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoteCameraDeviceDataMapperTest {
    @Test
    fun `camera selection round trips through string data map`() =
        runTest {
            val selection =
                MediaDeviceSelectionUiState(
                    kind = MediaDeviceKind.CAMERA,
                    devices =
                        listOf(
                            MediaDeviceUiState(
                                id = "camera-back",
                                label = "Back camera".toUiText(),
                                kind = MediaDeviceKind.CAMERA,
                                category = MediaDeviceCategory.BACK_CAMERA,
                            ),
                            MediaDeviceUiState(
                                id = "usb-camera-1",
                                label = "USB document camera".toUiText(),
                                kind = MediaDeviceKind.CAMERA,
                                category = MediaDeviceCategory.USB,
                                isExternal = true,
                            ),
                        ),
                    selectedDeviceId = "usb-camera-1",
                )

            val restored = RemoteCameraDeviceDataMapper.fromDataMap(RemoteCameraDeviceDataMapper.toDataMap(selection))

            assertEquals("usb-camera-1", restored.selectedDeviceId)
            assertEquals("USB document camera".toUiText(), restored.selectedDevice?.label)
            assertEquals(MediaDeviceCategory.USB, restored.selectedDevice?.category)
            assertTrue(restored.selectedDevice?.isExternal == true)
        }
}
