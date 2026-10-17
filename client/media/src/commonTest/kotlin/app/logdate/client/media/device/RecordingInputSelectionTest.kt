package app.logdate.client.media.device

import app.logdate.client.text.toUiText
import logdate.client.media.generated.resources.Res
import logdate.client.media.generated.resources.media_device_microphone_checking
import logdate.client.media.generated.resources.media_device_microphone_not_active
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordingInputSelectionTest {
    private val builtIn =
        MediaDeviceUiState("built-in", "Built-in microphone".toUiText(), MediaDeviceKind.AUDIO_INPUT, MediaDeviceCategory.BUILT_IN)
    private val usb =
        MediaDeviceUiState("usb", "USB microphone".toUiText(), MediaDeviceKind.AUDIO_INPUT, MediaDeviceCategory.USB, isExternal = true)
    private val requested = MediaDeviceSelectionUiState(MediaDeviceKind.AUDIO_INPUT, listOf(builtIn, usb), usb.id)

    @Test
    fun `requested device is not labeled in use before the recorder confirms it`() {
        val state = MediaDeviceSelectionResolver.resolveRecordingAudioInput(requested, null, null)
        assertNull(state.selectedDeviceId)
        assertFalse(state.isSelectionConfirmed)
        assertNull(state.selectedDevice)
        assertEquals(Res.string.media_device_microphone_checking.toUiText(), state.routeControlMessage)
    }

    @Test
    fun `actual routed device wins over a different requested microphone`() {
        val state = MediaDeviceSelectionResolver.resolveRecordingAudioInput(requested, builtIn.id, null)
        assertEquals(builtIn.id, state.selectedDeviceId)
        assertEquals(builtIn, state.selectedDevice)
        assertEquals(Res.string.media_device_microphone_not_active.toUiText(builtIn.label), state.routeControlMessage)
    }

    @Test
    fun `confirmed selected input clears pending feedback`() {
        val state = MediaDeviceSelectionResolver.resolveRecordingAudioInput(requested, usb.id, null)
        assertEquals(usb.id, state.selectedDeviceId)
        assertTrue(state.isSelectionConfirmed)
        assertNull(state.routeControlMessage)
    }

    @Test
    fun `rejected switch keeps the actual microphone and surfaces its error`() {
        val error = "Could not switch microphone".toUiText()
        val state = MediaDeviceSelectionResolver.resolveRecordingAudioInput(requested, builtIn.id, error)
        assertEquals(builtIn.id, state.selectedDeviceId)
        assertEquals(error, state.routeControlMessage)
    }

    @Test
    fun `disconnected actual input is never labeled in use`() {
        val state = MediaDeviceSelectionResolver.resolveRecordingAudioInput(requested.copy(devices = listOf(builtIn)), usb.id, null)
        assertNull(state.selectedDeviceId)
        assertFalse(state.isSelectionConfirmed)
    }
}
