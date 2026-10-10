package app.logdate.wear.e2e

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.compose.material3.AppScaffold
import app.logdate.client.domain.today.TodaySummary
import app.logdate.wear.R
import app.logdate.wear.capture.Feeling
import app.logdate.wear.v2.recorder.LiveMode
import app.logdate.wear.v2.recorder.RecorderActions
import app.logdate.wear.v2.recorder.RecorderFailure
import app.logdate.wear.v2.recorder.RecorderState
import app.logdate.wear.v2.recorder.RecorderTags
import app.logdate.wear.v2.recorder.TodayRecorder
import app.logdate.wear.v2.recorder.gesture.RecordGestureDefaults
import app.logdate.wear.v2.recorder.gesture.RecordGestureListener
import app.logdate.wear.v2.theme.LogDateWearTheme
import app.logdate.wear.v2.today.HandoffState
import app.logdate.wear.v2.today.PhoneCard
import app.logdate.wear.v2.today.TodayControls
import app.logdate.wear.v2.today.TodayPage
import app.logdate.wear.v2.today.TodayTags
import app.logdate.wear.v2.today.TodayUiState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * Today with a scripted recorder on a Wear OS emulator: one hold goes on while Today switches to
 * the live recording and reaches the lock, and each state's controls reach the recorder.
 */
@RunWith(AndroidJUnit4::class)
class TodayRecorderTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val recorder = ScriptedRecorder()

    @Test
    fun holding_shows_the_live_recording_and_the_same_hold_reaches_the_lock() {
        show()
        composeRule.onNodeWithTag(TodayTags.RECORD).performTouchInput {
            down(center)
            // A move that does not move: it is sent after the timeout, so the gesture has committed by then.
            move(delayMillis = viewConfiguration.longPressTimeoutMillis + 100)
        }
        composeRule.onNodeWithText(context.getString(R.string.wear_today_recorder_release_to_save)).assertIsDisplayed()

        composeRule.onNodeWithTag(TodayTags.RECORD).performTouchInput {
            repeat(5) { moveBy(Offset(0f, -RecordGestureDefaults.LockDistance.toPx() / 4)) }
            up()
        }

        composeRule.onNodeWithTag(RecorderTags.PAUSE).assertIsDisplayed()
        assertTrue(recorder.locked)
    }

    @Test
    fun stop_saves_a_locked_recording() {
        recorder.state = RecorderState.Live(LiveMode.LOCKED, lockProgress = 1f)
        show()

        composeRule.onNodeWithTag(TodayTags.RECORD).performClick()
        composeRule.waitForIdle()

        assertEquals(1, recorder.stops)
    }

    @Test
    fun a_saved_recording_offers_undo_and_the_five_feelings() {
        recorder.state = RecorderState.Saved(Uuid.random(), 42.seconds, feelingsOffered = true)
        show()

        composeRule.onNodeWithContentDescription(context.getString(R.string.wear_feeling_good)).performClick()
        composeRule.onNodeWithTag(RecorderTags.UNDO).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(Feeling.GOOD), recorder.feelings)
        assertEquals(1, recorder.undos)
    }

    @Test
    fun a_failed_save_is_tried_again_from_its_message_and_from_Record() {
        recorder.state = RecorderState.Failed(RecorderFailure.SAVE_FAILED)
        show()

        composeRule.onNodeWithTag(RecorderTags.FAILURE_ACTION).performClick()
        composeRule.onNodeWithTag(TodayTags.RECORD).performClick()
        composeRule.waitForIdle()

        assertEquals(2, recorder.retries)
    }

    @Test
    fun record_waits_while_a_recording_saves() {
        recorder.state = RecorderState.Saving
        show()

        composeRule.onNodeWithTag(TodayTags.RECORD).assertIsNotEnabled()
    }

    private fun show() {
        composeRule.setContent {
            LogDateWearTheme(dynamicColor = false) {
                AppScaffold {
                    TodayPage(
                        state = today,
                        handoff = HandoffState.IDLE,
                        onGetPhoneApp = {},
                        onHandoffDismissed = {},
                        controls = TodayControls(recorder = TodayRecorder(recorder.state, recorder, recorder)),
                    )
                }
            }
        }
    }

    /** A recorder whose gesture moves it through the states the way the real one does, and counts its controls. */
    private class ScriptedRecorder :
        RecordGestureListener,
        RecorderActions {
        var state: RecorderState by mutableStateOf(RecorderState.Ready)
        var locked = false
        var stops = 0
        var undos = 0
        var retries = 0
        val feelings = mutableListOf<Feeling>()

        override fun onPressed() = Unit

        override fun onYielded() = Unit

        override fun onTapped() {
            state = RecorderState.Live(LiveMode.LOCKED, lockProgress = 1f)
        }

        override fun onCommitted() {
            state = RecorderState.Live(LiveMode.HOLDING)
        }

        override fun onLockProgress(progress: Float) {
            val live = state as? RecorderState.Live ?: return
            state = live.copy(lockProgress = progress)
        }

        override fun onLocked() {
            locked = true
            state = RecorderState.Live(LiveMode.LOCKED, lockProgress = 1f)
        }

        override fun onReleased() {
            state = RecorderState.Saving
        }

        override fun stop() {
            stops++
        }

        override fun togglePause() = Unit

        override fun discard() = Unit

        override fun undo() {
            undos++
        }

        override fun chooseFeeling(feeling: Feeling) {
            feelings += feeling
        }

        override fun retry() {
            retries++
        }

        override fun dismiss() = Unit
    }

    private companion object {
        val today =
            TodayUiState.Ready(
                date = LocalDate(2026, 10, 10),
                timeZone = TimeZone.UTC,
                summary = TodaySummary.Empty,
                feeling = null,
                campfire = null,
                phoneCard = PhoneCard.None,
            )
    }
}
