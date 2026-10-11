package app.logdate.wear.e2e

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performRotaryScrollInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeSource
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.journals.NotePlace
import app.logdate.wear.v2.flow.FlowActions
import app.logdate.wear.v2.flow.FlowPage
import app.logdate.wear.v2.flow.FlowUiState
import app.logdate.wear.v2.moments.MomentActions
import app.logdate.wear.v2.moments.MomentRowTags
import app.logdate.wear.v2.moments.MomentScreen
import app.logdate.wear.v2.moments.MomentTags
import app.logdate.wear.v2.moments.MomentUiState
import app.logdate.wear.v2.playback.PlaybackSpeed
import app.logdate.wear.v2.playback.PlaybackState
import app.logdate.wear.v2.theme.LogDateWearTheme
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Drives a moment's screen with touch and the crown on a Wear OS emulator. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class MomentDetailTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val at = Instant.fromEpochMilliseconds(1_791_000_000_000)
    private val voice =
        JournalNote.Audio(
            mediaRef = "walk.m4a",
            durationMs = 42_000L,
            creationTimestamp = at,
            lastUpdated = at,
            transcription = "Walked to the lake",
            location = NoteLocation(place = NotePlace(Uuid.random(), "Golden Gate Park", 37.77, -122.45)),
        )
    private val text = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Coffee before anyone else was up")

    @Test
    fun a_voice_moment_opens_on_its_player() {
        var plays = 0
        show(ready(voice), MomentActions(onPlayPause = { plays++ }))

        composeRule.onNodeWithTag(MomentTags.POSITION).assertTextEquals("0:00 / 0:42")
        composeRule.onNodeWithTag(MomentTags.PLAY_PAUSE).performClick()

        assertEquals(1, plays)
    }

    @Test
    fun turning_the_crown_on_the_player_scrubs() {
        val turns = mutableListOf<Float>()
        show(
            ready(voice, PlaybackState.Active(voice.uid, positionMs = 12_000L, durationMs = 42_000L, isPaused = false)),
            MomentActions(onCrown = { turns += it }),
        )

        composeRule.onNodeWithTag(MomentTags.PLAYER).performRotaryScrollInput { rotateToScrollVertically(CROWN_STEP) }
        composeRule.waitForIdle()

        assertTrue(turns.sum() > 0f, "turns $turns")
    }

    @Test
    fun speed_and_skip_silence_report_taps() {
        var speedTaps = 0
        val skips = mutableListOf<Boolean>()
        show(ready(voice), MomentActions(onCycleSpeed = { speedTaps++ }, onSkipSilenceChange = { skips += it }))

        composeRule.onNodeWithTag(MomentTags.SPEED).performClick()
        composeRule.onNodeWithTag(MomentTags.SKIP_SILENCE).performClick()

        assertEquals(1, speedTaps)
        assertEquals(listOf(true), skips)
    }

    @Test
    fun swiping_up_shows_the_details_and_delete_asks_first() {
        var deletes = 0
        show(ready(voice), MomentActions(onDelete = { deletes++ }))

        composeRule.onNodeWithTag(MomentTags.SCREEN).performTouchInput { swipeUp() }
        waitForTag(MomentTags.DETAILS)
        composeRule.onNodeWithText("Walked to the lake").assertIsDisplayed()
        composeRule.onNodeWithTag(MomentTags.DETAILS).performRotaryScrollInput { rotateToScrollVertically(LONG_SCROLL) }
        waitForTag(MomentTags.DELETE)
        composeRule.onNodeWithTag(MomentTags.DELETE).performClick()
        assertEquals(0, deletes)
        waitForTag(MomentTags.CONFIRM_DELETE)
        composeRule.onNodeWithTag(MomentTags.CONFIRM_DELETE).performClick()
        composeRule.waitForIdle()

        assertEquals(1, deletes)
    }

    @Test
    fun a_text_moment_opens_on_its_details() {
        show(ready(text), MomentActions())

        composeRule.onNodeWithText("Coffee before anyone else was up").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag(MomentTags.PLAYER).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag(MomentTags.DELETE).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun a_row_in_the_list_opens_its_moment() {
        var opened: JournalNote? = null
        showList(FlowActions(onOpenMoment = { opened = it }))

        composeRule.onNodeWithTag(MomentRowTags.ROW).performClick()

        assertEquals(text, opened)
    }

    @Test
    fun a_row_does_not_open_without_a_screen_to_open() {
        showList(FlowActions())

        composeRule.onNodeWithTag(MomentRowTags.ROW).assertHasNoClickAction()
    }

    @Test
    fun a_missing_moment_says_so() {
        show(MomentUiState.Missing, MomentActions())

        composeRule.onNodeWithTag(MomentTags.MISSING).assertIsDisplayed()
    }

    private fun ready(
        note: JournalNote,
        playback: PlaybackState = PlaybackState.Idle,
    ) = MomentUiState.Ready(note, TimeZone.currentSystemDefault(), playback, PlaybackSpeed.NORMAL, skipSilence = false)

    private fun show(
        state: MomentUiState,
        actions: MomentActions,
    ) {
        composeRule.setContent { Scene { MomentScreen(state = state, actions = actions, timeSource = FixedTime) } }
    }

    private fun showList(actions: FlowActions) {
        val state = FlowUiState.Ready(TimeZone.currentSystemDefault(), listOf(text))
        composeRule.setContent { Scene { FlowPage(state = state, actions = actions, timeSource = FixedTime) } }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val CROWN_STEP = 120f
        const val LONG_SCROLL = 600f
        const val TIMEOUT_MS = 5_000L
    }
}

@Composable
private fun Scene(content: @Composable () -> Unit) {
    LogDateWearTheme(dynamicColor = false) {
        AppScaffold(timeText = {}) { content() }
    }
}

private object FixedTime : TimeSource {
    @Composable
    override fun currentTime(): String = "10:09"
}
