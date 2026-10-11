package app.logdate.wear.e2e

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.input.RemoteInputIntentHelper
import app.logdate.wear.capture.Feeling
import app.logdate.wear.v2.capture.CaptureSheet
import app.logdate.wear.v2.capture.CaptureSheetTags
import app.logdate.wear.v2.capture.CaptureSheetUiState
import app.logdate.wear.v2.capture.WRITTEN_TEXT_KEY
import app.logdate.wear.v2.capture.remoteTextInputIntent
import app.logdate.wear.v2.capture.writtenText
import app.logdate.wear.v2.theme.LogDateWearTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The + sheet on a Wear OS emulator, and the system text input it hands writing to. */
@RunWith(AndroidJUnit4::class)
class CaptureSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun the_phone_camera_shows_only_while_the_phone_is_in_reach() {
        var state by mutableStateOf(CaptureSheetUiState(phoneInReach = false))
        show(state = { state })
        composeRule.onAllNodesWithTag(CaptureSheetTags.PHONE_CAMERA).assertCountEquals(0)

        state = CaptureSheetUiState(phoneInReach = true)
        scrollTo(CaptureSheetTags.PHONE_CAMERA)

        composeRule.onNodeWithTag(CaptureSheetTags.PHONE_CAMERA).assertIsDisplayed()
    }

    @Test
    fun mood_opens_the_five_feelings_and_a_tap_chooses_one() {
        val chosen = mutableListOf<Feeling>()
        var state by mutableStateOf(CaptureSheetUiState())
        show(
            state = { state },
            onToggleFeelings = { state = state.copy(choosingFeeling = !state.choosingFeeling) },
            onFeeling = { feeling -> chosen += feeling },
        )

        composeRule.onNodeWithTag(CaptureSheetTags.MOOD).performClick()
        scrollTo(CaptureSheetTags.feeling(Feeling.GOOD))
        composeRule.onNodeWithTag(CaptureSheetTags.feeling(Feeling.GOOD)).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(Feeling.GOOD), chosen)
    }

    @Test
    fun opening_mood_brings_the_first_feelings_fully_into_view_without_scrolling() {
        var state by mutableStateOf(CaptureSheetUiState())
        show(state = { state }, onToggleFeelings = { state = state.copy(choosingFeeling = !state.choosingFeeling) })

        composeRule.onNodeWithTag(CaptureSheetTags.MOOD).performClick()
        composeRule.waitForIdle()

        assertTrue(isOnScreen(CaptureSheetTags.feeling(Feeling.GREAT)))
        assertTrue(isOnScreen(CaptureSheetTags.feeling(Feeling.GOOD)))
    }

    @Test
    fun nothing_in_the_sheet_works_while_a_save_runs() {
        show(state = { CaptureSheetUiState(saving = true) })

        composeRule.onNodeWithTag(CaptureSheetTags.WRITE).assertIsNotEnabled()
    }

    @Test
    fun write_asks_the_system_for_text_by_voice_or_keyboard() {
        val intent = remoteTextInputIntent("Write a note")

        assertTrue(RemoteInputIntentHelper.isActionRemoteInput(intent))
        assertEquals(WRITTEN_TEXT_KEY, inputOf(intent).resultKey)
    }

    @Test
    fun the_written_text_comes_back_from_the_system_input() {
        assertEquals("Coffee with Sam", writtenText(resultWith("Coffee with Sam")))
    }

    @Test
    fun nothing_written_is_no_text() {
        assertNull(writtenText(null))
        assertNull(writtenText(Intent()))
        assertNull(writtenText(resultWith("   ")))
    }

    private fun inputOf(intent: Intent): RemoteInput = RemoteInputIntentHelper.getRemoteInputsExtra(intent).orEmpty().single()

    private fun resultWith(text: String): Intent {
        val input = inputOf(remoteTextInputIntent("Write a note"))
        val result = Intent()
        RemoteInput.addResultsToIntent(arrayOf(input), result, Bundle().apply { putCharSequence(input.resultKey, text) })
        return result
    }

    private fun show(
        state: () -> CaptureSheetUiState,
        onToggleFeelings: () -> Unit = {},
        onFeeling: (Feeling) -> Unit = {},
    ) {
        composeRule.setContent {
            LogDateWearTheme(dynamicColor = false) {
                AppScaffold {
                    CaptureSheet(
                        state = state(),
                        onWrite = {},
                        onToggleFeelings = onToggleFeelings,
                        onFeeling = onFeeling,
                        onPhoneCamera = {},
                    )
                }
            }
        }
    }

    /** Scrolls the sheet a quarter screen at a time until [tag] is placed inside the screen, where a tap lands on it. */
    private fun scrollTo(tag: String) {
        repeat(MAX_SCROLLS) {
            composeRule.waitForIdle()
            if (isOnScreen(tag)) return
            composeRule.onNodeWithTag(CaptureSheetTags.LIST).performTouchInput {
                swipeUp(startY = centerY, endY = centerY - height / 4f, durationMillis = 400)
            }
        }
    }

    private fun isOnScreen(tag: String): Boolean {
        val node = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull() ?: return false
        val screen = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val bounds = node.boundsInRoot
        return node.layoutInfo.isPlaced && bounds.top >= screen.top && bounds.bottom <= screen.bottom
    }

    private companion object {
        const val MAX_SCROLLS = 8
    }
}
