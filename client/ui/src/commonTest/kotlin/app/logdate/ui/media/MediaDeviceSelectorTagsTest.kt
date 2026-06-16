package app.logdate.ui.media

import kotlin.test.Test
import kotlin.test.assertEquals

class MediaDeviceSelectorTagsTest {
    @Test
    fun `chip tag is stable for labels with spaces and punctuation`() {
        assertEquals(
            "media_device_selector_chip_external_display_audio",
            MediaDeviceSelectorTags.chip("External display audio"),
        )
    }

    @Test
    fun `device row tag includes normalized label and device id`() {
        assertEquals(
            "media_device_selector_row_audio_output_bluetooth_headphones",
            MediaDeviceSelectorTags.deviceRow("Audio output", "Bluetooth headphones"),
        )
    }

    @Test
    fun `system settings button tag is stable`() {
        assertEquals(
            "media_device_selector_settings_microphone",
            MediaDeviceSelectorTags.systemSettingsButton("Microphone"),
        )
    }
}
