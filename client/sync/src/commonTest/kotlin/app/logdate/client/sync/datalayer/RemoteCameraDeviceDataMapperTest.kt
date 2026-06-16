package app.logdate.client.sync.datalayer

import app.logdate.client.media.device.MediaDeviceCategory
import app.logdate.client.media.device.MediaDeviceKind
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.client.media.device.MediaDeviceUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoteCameraDeviceDataMapperTest {
    @Test
    fun cameraSelectionRoundTripsThroughStringDataMap() {
        val selection =
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

        val restored = RemoteCameraDeviceDataMapper.fromDataMap(RemoteCameraDeviceDataMapper.toDataMap(selection))

        assertEquals("usb-camera-1", restored.selectedDeviceId)
        assertEquals("USB document camera", restored.selectedDevice?.label)
        assertEquals(MediaDeviceCategory.USB, restored.selectedDevice?.category)
        assertTrue(restored.selectedDevice?.isExternal == true)
    }
}
