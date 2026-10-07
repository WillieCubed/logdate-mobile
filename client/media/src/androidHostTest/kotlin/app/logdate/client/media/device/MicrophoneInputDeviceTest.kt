package app.logdate.client.media.device

import android.media.AudioDeviceInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MicrophoneInputDeviceTest {
    @Test
    fun `system capture ports are not selectable microphones`() {
        listOf(
            AudioDeviceInfo.TYPE_UNKNOWN,
            AudioDeviceInfo.TYPE_TELEPHONY,
            AudioDeviceInfo.TYPE_REMOTE_SUBMIX,
            AudioDeviceInfo.TYPE_FM_TUNER,
            AudioDeviceInfo.TYPE_TV_TUNER,
        ).forEach { type ->
            assertFalse(isMicrophoneInputType(type), "System input type $type must not appear in the microphone picker")
        }
    }

    @Test
    fun `built in and connected microphones remain selectable`() {
        listOf(
            AudioDeviceInfo.TYPE_BUILTIN_MIC,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
        ).forEach { type ->
            assertTrue(isMicrophoneInputType(type), "Microphone input type $type must remain available")
        }
    }
}
