package app.logdate.wear.e2e

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import app.logdate.wear.v2.recorder.gesture.RecordGestureDefaults
import app.logdate.wear.v2.recorder.gesture.RecordGestureListener
import app.logdate.wear.v2.recorder.gesture.recordGesture
import app.logdate.wear.v2.recorder.gesture.startLocked
import app.logdate.wear.v2.theme.LogDateWearTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Drives Record's gesture with real touch input on a Wear OS emulator. A quick drag yields to
 * scrolling; a hold owns the gesture after the long-press timeout.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class RecordGestureTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val signals = RecordedSignals()
    private val clicks = Collections.synchronizedList(mutableListOf<String>())

    @Test
    fun a_tap_starts_a_locked_recording_and_never_clicks_the_button() {
        show()

        record {
            down(center)
            advanceEventTime(100)
            up()
        }

        assertEquals(listOf("pressed", "tapped"), signals.list)
        assertTrue(clicks.isEmpty())
    }

    @Test
    fun holding_past_the_long_press_timeout_records_until_the_finger_lifts() {
        show()

        record {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            up()
        }

        assertEquals(listOf("pressed", "committed", "released"), signals.list)
    }

    @Test
    fun a_lift_just_before_the_long_press_timeout_is_still_a_tap() {
        show()

        record {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis - 100)
            up()
        }

        assertEquals(listOf("pressed", "tapped"), signals.list)
    }

    @Test
    fun sliding_up_after_the_timeout_locks_the_recording() {
        show()

        record {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            val step = RecordGestureDefaults.LockDistance.toPx() / 4
            repeat(5) { moveBy(Offset(0f, -step)) }
            up()
        }

        assertEquals(listOf("pressed", "committed"), signals.list.take(2))
        assertTrue("progress 0.5" in signals.list, "signals were ${signals.list}")
        assertEquals("locked", signals.list.last())
        assertFalse("released" in signals.list)
    }

    @Test
    fun a_quick_drag_yields_to_the_list_and_records_nothing() {
        show()

        record {
            down(center)
            repeat(6) { moveBy(Offset(0f, 40f), delayMillis = 10) }
            up()
        }

        assertEquals(listOf("pressed", "yielded"), signals.list)
    }

    @Test
    fun after_the_timeout_the_recording_owns_the_drag() {
        show()

        record {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(3) { moveBy(Offset(0f, -RecordGestureDefaults.LockDistance.toPx() / 4)) }
            up()
        }

        assertTrue(isShown(TODAY_TEXT))
        assertEquals("released", signals.list.last())
    }

    @Test
    fun after_the_timeout_a_long_fast_slide_locks_recording() {
        show()

        record {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(4) { moveBy(Offset(0f, -RecordGestureDefaults.LockDistance.toPx())) }
            up()
        }

        assertTrue(isShown(TODAY_TEXT))
        assertEquals("locked", signals.list.last())
    }

    @Test
    fun sliding_sideways_off_the_button_does_not_end_the_recording() {
        show()
        record {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(Offset(width.toFloat(), 0f))
            move(delayMillis = 500)
        }
        assertFalse("released" in signals.list)

        record { up() }

        assertEquals("released", signals.list.last())
    }

    @Test
    fun a_touch_the_system_cancels_before_the_timeout_records_nothing() {
        show()

        record {
            down(center)
            advanceEventTime(100)
            cancel()
        }

        assertEquals(listOf("pressed", "yielded"), signals.list)
    }

    @Test
    fun a_touch_the_system_cancels_while_holding_is_saved() {
        show()

        record {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            cancel()
        }

        assertEquals(listOf("pressed", "committed", "released"), signals.list)
    }

    @Test
    fun talkback_starts_a_locked_recording_by_click_and_by_its_action() {
        show()

        composeRule.onNodeWithTag(RECORD).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag(RECORD).performCustomAccessibilityActionWithLabel(START)
        composeRule.waitForIdle()

        assertEquals(listOf("pressed", "tapped", "pressed", "tapped"), signals.list)
    }

    @Test
    fun record_is_one_control_for_accessibility() {
        show()

        composeRule.onAllNodesWithContentDescription(DESCRIPTION).assertCountEquals(1)
        composeRule.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(RECORD))).assertCountEquals(0)
    }

    @Test
    fun a_button_that_is_not_armed_clicks_and_records_nothing() {
        show(armed = false)

        composeRule.onNodeWithTag(RECORD).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("click"), clicks.toList())
        assertTrue(signals.list.isEmpty())
    }

    private fun record(gesture: TouchInjectionScope.() -> Unit) {
        composeRule.onNodeWithTag(RECORD).performTouchInput(gesture)
        composeRule.waitForIdle()
    }

    private fun show(armed: Boolean = true) {
        composeRule.setContent {
            LogDateWearTheme(dynamicColor = false) {
                AppScaffold {
                    // Record shows in full at the end of its list, where Today's list starts.
                    RecordPage(rememberTransformingLazyColumnState(initialAnchorItemIndex = MOMENT_COUNT), armed, signals) {
                        clicks += "click"
                    }
                }
            }
        }
    }

    private fun isShown(text: String): Boolean =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() && composeRule.onNodeWithText(text).isDisplayed()

    private class RecordedSignals : RecordGestureListener {
        private val recorded = Collections.synchronizedList(mutableListOf<String>())

        val list: List<String> get() = recorded.toList()

        override fun onPressed() {
            recorded += "pressed"
        }

        override fun onYielded() {
            recorded += "yielded"
        }

        override fun onTapped() {
            recorded += "tapped"
        }

        override fun onCommitted() {
            recorded += "committed"
        }

        override fun onLockProgress(progress: Float) {
            recorded += "progress $progress"
        }

        override fun onLocked() {
            recorded += "locked"
        }

        override fun onReleased() {
            recorded += "released"
        }
    }

    private companion object {
        const val RECORD = "gesture_record"
        const val DESCRIPTION = "Record"
        const val START = "Start recording"
        const val TODAY_TEXT = "Today"
        const val MOMENT_COUNT = 20

        @Composable
        fun RecordPage(
            listState: TransformingLazyColumnState,
            armed: Boolean,
            listener: RecordGestureListener,
            onDisarmedClick: () -> Unit,
        ) {
            val click: () -> Unit = if (armed) listener::startLocked else onDisarmedClick
            ScreenScaffold(
                scrollState = listState,
                edgeButton = {
                    EdgeButton(
                        onClick = click,
                        modifier =
                            Modifier
                                .testTag(RECORD)
                                .recordGesture(
                                    armed = armed,
                                    listener = listener,
                                    description = DESCRIPTION,
                                    startLabel = START,
                                    onClick = click,
                                ),
                        buttonSize = EdgeButtonSize.Medium,
                    ) {
                        Text("●")
                    }
                },
            ) { contentPadding ->
                // Earlier moments above, as on Today, so a quick drag down has a list to scroll back.
                TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
                    items(MOMENT_COUNT) { index -> Text("Moment $index") }
                    item { Text(TODAY_TEXT) }
                }
            }
        }
    }
}
