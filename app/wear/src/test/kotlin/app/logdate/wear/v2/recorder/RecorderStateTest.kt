package app.logdate.wear.v2.recorder

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class RecorderStateTest {
    private val saved = RecorderState.Saved(noteId = Uuid.random(), duration = 4.seconds, feelingsOffered = true)

    @Test
    fun `only a live recording locks Home on Today`() {
        assertTrue(RecorderState.Live(LiveMode.HOLDING).locksPaging)
        assertTrue(RecorderState.Live(LiveMode.LOCKED).locksPaging)
        listOf(
            RecorderState.Ready,
            RecorderState.Arming,
            RecorderState.Saving,
            saved,
            RecorderState.TooShort,
            RecorderState.Failed(RecorderFailure.SAVE_FAILED),
        ).forEach { state -> assertFalse(state.locksPaging, "$state") }
    }

    @Test
    fun `Record starts a recording when nothing is under way and no save is waiting`() {
        listOf(
            RecorderState.Ready,
            saved,
            RecorderState.TooShort,
            RecorderState.Failed(RecorderFailure.RECORDING_LOST),
            RecorderState.Failed(RecorderFailure.MICROPHONE_DENIED),
        ).forEach { state -> assertTrue(state.canStartRecording, "$state") }
        listOf(
            RecorderState.Arming,
            RecorderState.Live(LiveMode.LOCKED),
            RecorderState.Saving,
            RecorderState.Failed(RecorderFailure.SAVE_FAILED),
        ).forEach { state -> assertFalse(state.canStartRecording, "$state") }
    }
}
