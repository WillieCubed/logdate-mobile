package app.logdate.feature.editor.ui.audio

import kotlin.test.Test
import kotlin.test.assertEquals

class RecordingWaveformWindowTest {
    @Test
    fun `visible window discards old samples without rescaling recent audio`() {
        assertEquals(listOf(.2f, .8f, .1f), recordingWaveformWindow(listOf(.9f, .6f, .2f, .8f, .1f), 3))
    }

    @Test
    fun `short recordings retain their sample count instead of stretching to fill the window`() {
        assertEquals(listOf(.2f, .8f), recordingWaveformWindow(listOf(.2f, .8f), 32))
    }

    @Test
    fun `invalid levels and empty viewports cannot produce invalid waveform geometry`() {
        assertEquals(listOf(0f, 0f, 1f), recordingWaveformWindow(listOf(Float.NaN, -1f, 2f), 3))
        assertEquals(emptyList(), recordingWaveformWindow(listOf(.5f), 0))
    }
}
