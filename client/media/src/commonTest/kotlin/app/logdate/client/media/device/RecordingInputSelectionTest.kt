package app.logdate.client.media.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordingInputSelectionTest {
    private val builtIn = MediaDeviceUiState("built-in", "Built-in microphone", MediaDeviceKind.AUDIO_INPUT, MediaDeviceCategory.BUILT_IN)
    private val usb = MediaDeviceUiState("usb", "USB microphone", MediaDeviceKind.AUDIO_INPUT, MediaDeviceCategory.USB, isExternal = true)
    private val requested = MediaDeviceSelectionUiState(MediaDeviceKind.AUDIO_INPUT, listOf(builtIn, usb), usb.id)

    @Test
    fun `requested device is not labeled in use before the recorder confirms it`() {
        val state = MediaDeviceSelectionResolver.resolveRecordingAudioInput(requested, null, null)
        assertNull(state.selectedDeviceId)
        assertFalse(state.isSelectionConfirmed)
        assertNull(state.selectedDevice)
        assertTrue(requireNotNull(state.routeControlMessage).contains("Checking"))
    }

    @Test
    fun `actual routed device wins over a different requested microphone`() {
        val state = MediaDeviceSelectionResolver.resolveRecordingAudioInput(requested, builtIn.id, null)
        assertEquals(builtIn.id, state.selectedDeviceId)
        assertEquals(builtIn, state.selectedDevice)
        assertTrue(requireNotNull(state.routeControlMessage).contains(builtIn.label))
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
        val state = MediaDeviceSelectionResolver.resolveRecordingAudioInput(requested, builtIn.id, "Could not switch microphone")
        assertEquals(builtIn.id, state.selectedDeviceId)
        assertEquals("Could not switch microphone", state.routeControlMessage)
    }

    @Test
    fun `disconnected actual input is never labeled in use`() {
        val state = MediaDeviceSelectionResolver.resolveRecordingAudioInput(requested.copy(devices = listOf(builtIn)), usb.id, null)
        assertNull(state.selectedDeviceId)
        assertFalse(state.isSelectionConfirmed)
    }
}
