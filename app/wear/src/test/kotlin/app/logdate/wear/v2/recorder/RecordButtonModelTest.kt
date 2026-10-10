package app.logdate.wear.v2.recorder

import app.logdate.wear.R
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class RecordButtonModelTest {
    @Test
    fun `a ready Record is armed to start a recording`() {
        val model = RecorderState.Ready.recordButton()

        assertEquals(RecordButtonAction.START, model.action)
        assertTrue(model.armed)
        assertEquals(0f, model.morph)
        assertEquals(R.string.wear_today_record, model.descriptionRes)
    }

    @Test
    fun `while holding Record turns toward Stop as the finger slides to the lock`() {
        val model = RecorderState.Live(LiveMode.HOLDING, lockProgress = 0.4f).recordButton()

        assertEquals(RecordButtonAction.NONE, model.action)
        assertFalse(model.armed)
        assertEquals(0.4f, model.morph)
    }

    @Test
    fun `a locked recording has no Record button because Stop is in Today's list`() {
        assertFalse(RecorderState.Live(LiveMode.LOCKED, lockProgress = 1f).showsRecordButton)
    }

    @Test
    fun `Record shows in every other state including while a finger still holds it`() {
        val states =
            listOf(
                RecorderState.Ready,
                RecorderState.Arming,
                RecorderState.Live(LiveMode.HOLDING, lockProgress = 0.9f),
                RecorderState.Saving,
                RecorderState.Saved(Uuid.random(), 4.seconds, feelingsOffered = true),
                RecorderState.TooShort,
            ) + RecorderFailure.entries.map { RecorderState.Failed(it) }

        states.forEach { state -> assertTrue(state.showsRecordButton, "$state") }
    }

    @Test
    fun `Record waits while a recording saves`() {
        assertFalse(RecorderState.Saving.recordButton().enabled)
    }

    @Test
    fun `after a failed save Record saves the same recording again`() {
        val model = RecorderState.Failed(RecorderFailure.SAVE_FAILED).recordButton()

        assertEquals(RecordButtonAction.RETRY_SAVE, model.action)
        assertFalse(model.armed)
    }

    @Test
    fun `Record is armed exactly when a touch can start a recording`() {
        val states =
            listOf(
                RecorderState.Ready,
                RecorderState.Arming,
                RecorderState.Live(LiveMode.HOLDING),
                RecorderState.Live(LiveMode.LOCKED),
                RecorderState.Saving,
                RecorderState.Saved(Uuid.random(), 4.seconds, feelingsOffered = true),
                RecorderState.TooShort,
            ) + RecorderFailure.entries.map { RecorderState.Failed(it) }

        states.forEach { state -> assertEquals(state.canStartRecording, state.recordButton().armed, "$state") }
    }
}
