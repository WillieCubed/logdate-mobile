package app.logdate.client.notifications

import kotlin.test.Test
import kotlin.test.assertEquals

class LiveUpdatesTest {
    @Test
    fun `nothing is promoted while Live Updates are off`() {
        assertEquals(LiveUpdate.None, recordingLiveUpdate(enabled = false, isRecording = true))
        assertEquals(LiveUpdate.None, recordingLiveUpdate(enabled = false, isRecording = false))
    }

    @Test
    fun `an active recording is promoted and its chip counts up the elapsed time`() {
        assertEquals(LiveUpdate(promoted = true, chipText = null), recordingLiveUpdate(enabled = true, isRecording = true))
    }

    @Test
    fun `a paused recording stays promoted and its chip says paused`() {
        assertEquals(
            LiveUpdate(promoted = true, chipText = LiveUpdateChip.Paused),
            recordingLiveUpdate(enabled = true, isRecording = false),
        )
    }

    @Test
    fun `the switch defaults to off`() {
        assertEquals(false, LiveUpdatesSwitch.Off.enabled)
    }

    @Test
    fun `data work is never promoted with Live Updates off`() {
        assertEquals(TransferLiveUpdate(promoted = false, chipPercent = null), transferLiveUpdate(enabled = false, percent = 40))
    }

    @Test
    fun `data work is promoted at once and the chip shows the percent once known`() {
        assertEquals(TransferLiveUpdate(promoted = true, chipPercent = null), transferLiveUpdate(enabled = true, percent = null))
        assertEquals(TransferLiveUpdate(promoted = true, chipPercent = 40), transferLiveUpdate(enabled = true, percent = 40))
        assertEquals(TransferLiveUpdate(promoted = true, chipPercent = 100), transferLiveUpdate(enabled = true, percent = 140))
    }

    @Test
    fun `phase starts split the bar into segments that fill it`() {
        assertEquals(listOf(100), progressSegmentLengths(emptyList()))
        assertEquals(listOf(5, 5, 10, 80), progressSegmentLengths(listOf(0, 0, 5, 10, 20)))
        assertEquals(listOf(40, 60), progressSegmentLengths(listOf(40, 40, 100, -3)))
    }
}
