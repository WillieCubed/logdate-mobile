package app.logdate.client.media.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaDeviceSelectionUiStateTest {
    @Test
    fun `selectedDevice returns explicit selected device`() {
        val selection =
            MediaDeviceSelectionUiState(
                kind = MediaDeviceKind.AUDIO_OUTPUT,
                devices =
                    listOf(
                        DefaultMediaDevices.systemOutput,
                        DefaultMediaDevices.systemOutput.copy(id = "bluetooth", label = "Bluetooth headphones"),
                    ),
                selectedDeviceId = "bluetooth",
            )

        assertEquals("Bluetooth headphones", selection.selectedDevice?.label)
    }

    @Test
    fun `selectedDevice falls back to first available device`() {
        val selection =
            MediaDeviceSelectionUiState(
                kind = MediaDeviceKind.AUDIO_INPUT,
                devices =
                    listOf(
                        DefaultMediaDevices.systemMicrophone.copy(isAvailable = false),
                        DefaultMediaDevices.systemMicrophone.copy(id = "usb", label = "USB microphone"),
                    ),
                selectedDeviceId = "missing",
            )

        assertEquals("USB microphone", selection.selectedDevice?.label)
    }

    @Test
    fun `selectedDevice is null when there are no available devices`() {
        val selection =
            MediaDeviceSelectionUiState(
                kind = MediaDeviceKind.CAMERA,
                devices = emptyList(),
                selectedDeviceId = null,
            )

        assertNull(selection.selectedDevice)
    }

    @Test
    fun `audio input resolver selects preferred microphone when it is available`() {
        val builtIn = DefaultMediaDevices.systemMicrophone.copy(id = "built-in", label = "Built-in microphone")
        val usb =
            DefaultMediaDevices.systemMicrophone.copy(
                id = "usb",
                label = "USB microphone",
                category = MediaDeviceCategory.USB,
                isExternal = true,
            )

        val selection =
            MediaDeviceSelectionResolver.resolveAudioInput(
                devices = listOf(builtIn, usb),
                preferredDeviceId = usb.id,
            )

        assertEquals(usb.id, selection.selectedDeviceId)
        assertEquals("USB microphone", selection.selectedDevice?.label)
        assertEquals(true, selection.isSelectionControllable)
        assertNull(selection.routeControlMessage)
    }

    @Test
    fun `audio input resolver falls back with copy when preferred microphone is unplugged`() {
        val builtIn = DefaultMediaDevices.systemMicrophone.copy(id = "built-in", label = "Built-in microphone")

        val selection =
            MediaDeviceSelectionResolver.resolveAudioInput(
                devices = listOf(builtIn),
                preferredDeviceId = "usb",
            )

        assertEquals(builtIn.id, selection.selectedDeviceId)
        assertEquals("Built-in microphone", selection.selectedDevice?.label)
        assertEquals(
            "Selected microphone is unavailable. LogDate will use Built-in microphone.",
            selection.routeControlMessage,
        )
    }

    @Test
    fun `audio input resolver restores preferred microphone when it is plugged back in`() {
        val builtIn = DefaultMediaDevices.systemMicrophone.copy(id = "built-in", label = "Built-in microphone")
        val usb =
            DefaultMediaDevices.systemMicrophone.copy(
                id = "usb",
                label = "USB microphone",
                category = MediaDeviceCategory.USB,
                isExternal = true,
            )

        val selectionWithoutUsb =
            MediaDeviceSelectionResolver.resolveAudioInput(
                devices = listOf(builtIn),
                preferredDeviceId = usb.id,
            )
        val selectionWithUsb =
            MediaDeviceSelectionResolver.resolveAudioInput(
                devices = listOf(builtIn, usb),
                preferredDeviceId = usb.id,
            )

        assertEquals(builtIn.id, selectionWithoutUsb.selectedDeviceId)
        assertEquals(usb.id, selectionWithUsb.selectedDeviceId)
        assertNull(selectionWithUsb.routeControlMessage)
    }

    @Test
    fun `audio output resolver keeps preferred output visible while using system routing copy`() {
        val speaker = DefaultMediaDevices.systemOutput.copy(id = "speaker", label = "Built-in speaker")
        val headset =
            DefaultMediaDevices.systemOutput.copy(
                id = "headset",
                label = "USB headset",
                category = MediaDeviceCategory.USB,
                isExternal = true,
            )

        val selection =
            MediaDeviceSelectionResolver.resolveAudioOutput(
                devices = listOf(speaker, headset),
                preferredDeviceId = headset.id,
            )

        assertEquals(headset.id, selection.selectedDeviceId)
        assertEquals("USB headset", selection.selectedDevice?.label)
        assertEquals(false, selection.isSelectionControllable)
        assertEquals(
            "Use Android's output switcher to route playback.",
            selection.routeControlMessage,
        )
    }

    @Test
    fun `audio input resolver falls back to system microphone when no devices are available`() {
        val selection = MediaDeviceSelectionResolver.resolveAudioInput(devices = emptyList(), preferredDeviceId = null)

        assertEquals(DefaultMediaDevices.systemMicrophone.id, selection.selectedDeviceId)
        assertEquals("System microphone", selection.selectedDevice?.label)
        assertEquals(false, selection.isSelectionControllable)
        assertEquals(
            "Microphone selection is currently controlled by the system.",
            selection.routeControlMessage,
        )
    }

    @Test
    fun `audio output resolver falls back to system output when no devices are available`() {
        val selection = MediaDeviceSelectionResolver.resolveAudioOutput(devices = emptyList(), preferredDeviceId = null)

        assertEquals(DefaultMediaDevices.systemOutput.id, selection.selectedDeviceId)
        assertEquals("System output", selection.selectedDevice?.label)
        assertEquals(false, selection.isSelectionControllable)
        assertEquals(
            "Use Android's output switcher to route playback.",
            selection.routeControlMessage,
        )
    }

    @Test
    fun `system controlled selection helper uses the shared route copy`() {
        val input = systemControlledSelection(MediaDeviceKind.AUDIO_INPUT)
        val output = systemControlledSelection(MediaDeviceKind.AUDIO_OUTPUT)
        val camera = systemControlledSelection(MediaDeviceKind.CAMERA)

        val overriddenInput =
            input.asSystemControlledSelection(
                routeControlMessage = "Open system settings to change the microphone.",
            )

        assertEquals("System microphone", input.selectedDevice?.label)
        assertEquals(
            "Microphone selection is currently controlled by the system.",
            input.routeControlMessage,
        )

        assertEquals("System output", output.selectedDevice?.label)
        assertEquals(
            "Use Android's output switcher to route playback.",
            output.routeControlMessage,
        )

        assertEquals("Back camera", camera.selectedDevice?.label)
        assertEquals(
            "Camera selection is currently controlled by the system.",
            camera.routeControlMessage,
        )

        assertEquals(false, overriddenInput.isSelectionControllable)
        assertEquals("Open system settings to change the microphone.", overriddenInput.routeControlMessage)
        assertEquals(input.devices, overriddenInput.devices)
    }
}
