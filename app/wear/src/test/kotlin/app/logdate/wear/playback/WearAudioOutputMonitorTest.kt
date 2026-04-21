package app.logdate.wear.playback

import android.media.AudioDeviceInfo
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Tests for [WearAudioOutputMonitor], ensuring the correct derivation of audio output states
 * from Android's hardware device information.
 *
 * This suite validates the mapping of various [AudioDeviceInfo] combinations to high-level
 * [AudioOutputState]s, confirming that the system accurately identifies when audio will play
 * through the built-in speaker, a Bluetooth headset, both, or if no valid output is available.
 */
class WearAudioOutputMonitorTest {
    @Test
    fun `speaker and bluetooth returns SpeakerAndBluetooth`() {
        val types =
            listOf(
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            )
        assertEquals(AudioOutputState.SpeakerAndBluetooth, WearAudioOutputMonitor.deriveOutputState(types))
    }

    @Test
    fun `speaker only returns SpeakerOnly`() {
        val types = listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
        assertEquals(AudioOutputState.SpeakerOnly, WearAudioOutputMonitor.deriveOutputState(types))
    }

    @Test
    fun `bluetooth A2DP only returns BluetoothOnly`() {
        val types = listOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        assertEquals(AudioOutputState.BluetoothOnly, WearAudioOutputMonitor.deriveOutputState(types))
    }

    @Test
    fun `BLE headset returns BluetoothOnly`() {
        val types = listOf(AudioDeviceInfo.TYPE_BLE_HEADSET)
        assertEquals(AudioOutputState.BluetoothOnly, WearAudioOutputMonitor.deriveOutputState(types))
    }

    @Test
    fun `BLE speaker returns BluetoothOnly`() {
        val types = listOf(AudioDeviceInfo.TYPE_BLE_SPEAKER)
        assertEquals(AudioOutputState.BluetoothOnly, WearAudioOutputMonitor.deriveOutputState(types))
    }

    @Test
    fun `BLE broadcast returns BluetoothOnly`() {
        val types = listOf(AudioDeviceInfo.TYPE_BLE_BROADCAST)
        assertEquals(AudioOutputState.BluetoothOnly, WearAudioOutputMonitor.deriveOutputState(types))
    }

    @Test
    fun `speaker with BLE headset returns SpeakerAndBluetooth`() {
        val types =
            listOf(
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
            )
        assertEquals(AudioOutputState.SpeakerAndBluetooth, WearAudioOutputMonitor.deriveOutputState(types))
    }

    @Test
    fun `no output devices returns Unavailable`() {
        assertEquals(AudioOutputState.Unavailable, WearAudioOutputMonitor.deriveOutputState(emptyList()))
    }

    @Test
    fun `non-audio devices only returns Unavailable`() {
        val types =
            listOf(
                AudioDeviceInfo.TYPE_BUILTIN_MIC,
                AudioDeviceInfo.TYPE_USB_DEVICE,
            )
        assertEquals(AudioOutputState.Unavailable, WearAudioOutputMonitor.deriveOutputState(types))
    }
}
