package app.logdate.client.tiles

import kotlin.test.Test
import kotlin.test.assertEquals

class CaptureTileStateTest {
    @Test
    fun `the voice tile is inactive while nothing is recording`() {
        assertEquals(
            VoiceTileAppearance(active = false, status = VoiceTileStatus.Idle),
            voiceTileAppearance(isRecording = false, isPaused = false),
        )
    }

    @Test
    fun `the voice tile is active and says so while recording`() {
        assertEquals(
            VoiceTileAppearance(active = true, status = VoiceTileStatus.Recording),
            voiceTileAppearance(isRecording = true, isPaused = false),
        )
    }

    @Test
    fun `a paused recording keeps the tile active and says it is paused`() {
        assertEquals(
            VoiceTileAppearance(active = true, status = VoiceTileStatus.Paused),
            voiceTileAppearance(isRecording = true, isPaused = true),
        )
    }

    @Test
    fun `tapping the voice tile records unless a recording is already running`() {
        assertEquals(VoiceTileAction.StartRecording, voiceTileAction(isRecording = false))
        assertEquals(VoiceTileAction.ShowRecording, voiceTileAction(isRecording = true))
    }
}
