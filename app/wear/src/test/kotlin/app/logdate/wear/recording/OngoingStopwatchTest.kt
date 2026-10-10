package app.logdate.wear.recording

import app.logdate.client.media.audio.RecordingNotificationState
import org.junit.Test
import kotlin.test.assertEquals

class OngoingStopwatchTest {
    @Test
    fun `a running recording counts up from when its recorded time began`() {
        val state = RecordingNotificationState(isRecording = true, startTimeMillis = 1_000L, recordedMillis = 42_000L)

        assertEquals(
            OngoingStopwatch(timeZeroMillis = 58_000L, pausedAtMillis = null),
            ongoingStopwatch(state, nowElapsedRealtime = 100_000L),
        )
    }

    @Test
    fun `a paused recording stops the stopwatch at the time recorded`() {
        val state = RecordingNotificationState(isRecording = false, startTimeMillis = 1_000L, recordedMillis = 42_000L)

        assertEquals(
            OngoingStopwatch(timeZeroMillis = 58_000L, pausedAtMillis = 100_000L),
            ongoingStopwatch(state, nowElapsedRealtime = 100_000L),
        )
    }
}
