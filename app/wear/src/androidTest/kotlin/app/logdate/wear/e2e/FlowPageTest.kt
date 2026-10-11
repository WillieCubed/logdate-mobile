package app.logdate.wear.e2e

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performRotaryScrollInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeSource
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.v2.archive.ArchiveDay
import app.logdate.wear.v2.archive.ArchiveScreen
import app.logdate.wear.v2.archive.ArchiveTags
import app.logdate.wear.v2.archive.ArchiveUiState
import app.logdate.wear.v2.flow.FlowActions
import app.logdate.wear.v2.flow.FlowPage
import app.logdate.wear.v2.flow.FlowTags
import app.logdate.wear.v2.flow.FlowUiState
import app.logdate.wear.v2.moments.MomentRowTags
import app.logdate.wear.v2.playback.PlaybackFailure
import app.logdate.wear.v2.playback.PlaybackState
import app.logdate.wear.v2.theme.LogDateWearTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Drives the moment list and the Archive with touch and the crown on a Wear OS emulator. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FlowPageTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val zone = TimeZone.currentSystemDefault()
    private val at = Instant.fromEpochMilliseconds(1_791_000_000_000)
    private val voice = JournalNote.Audio(mediaRef = "v.m4a", durationMs = 42_000L, creationTimestamp = at, lastUpdated = at)
    private val text = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Coffee before anyone else was up")

    @Test
    fun a_voice_row_plays_inline_and_a_row_opens_its_moment() {
        val played = mutableListOf<JournalNote.Audio>()
        val opened = mutableListOf<JournalNote>()
        showFlow(earlierCount = 0, FlowActions(onOpenMoment = { opened += it }, onPlayPause = { played += it }))

        composeRule.onAllNodesWithTag(MomentRowTags.PLAY_PAUSE).onFirst().performClick()
        composeRule.onNodeWithText("Coffee before anyone else was up").performClick()

        assertEquals(listOf(voice), played)
        assertEquals(listOf<JournalNote>(text), opened)
    }

    @Test
    fun a_voice_row_that_could_not_play_says_why() {
        showFlow(
            earlierCount = 0,
            FlowActions(onPlayPause = {}),
            PlaybackState.Failed(voice.uid, PlaybackFailure.NOT_ON_WATCH),
        )

        composeRule.onNodeWithTag(MomentRowTags.FAILURE).assertIsDisplayed()
    }

    @Test
    fun earlier_opens_the_archive_when_there_is_more() {
        var archiveOpens = 0
        showFlow(earlierCount = 3, FlowActions(onOpenArchive = { archiveOpens++ }))

        composeRule.onNodeWithTag(FlowTags.LIST).performRotaryScrollInput { rotateToScrollVertically(LONG_SCROLL) }
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodesWithTag(FlowTags.EARLIER).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag(FlowTags.EARLIER).performClick()

        assertEquals(1, archiveOpens)
    }

    @Test
    fun earlier_is_not_offered_without_older_moments() {
        showFlow(earlierCount = 0, FlowActions(onOpenArchive = {}))

        composeRule.onNodeWithTag(FlowTags.LIST).performRotaryScrollInput { rotateToScrollVertically(LONG_SCROLL) }
        composeRule.waitForIdle()

        assertTrue(composeRule.onAllNodesWithTag(FlowTags.EARLIER).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun the_archive_lists_days_and_opens_their_moments() {
        val opened = mutableListOf<JournalNote>()
        composeRule.setContent {
            Scene {
                ArchiveScreen(
                    state = ArchiveUiState.Ready(zone, listOf(ArchiveDay(LocalDate(2026, 9, 21), listOf(text)))),
                    timeSource = FlowFixedTime,
                    onOpenMoment = { opened += it },
                )
            }
        }

        composeRule.onNodeWithTag(ArchiveTags.LIST).assertIsDisplayed()
        composeRule.onNodeWithText("Coffee before anyone else was up").performClick()

        assertEquals(listOf<JournalNote>(text), opened)
    }

    private fun showFlow(
        earlierCount: Int,
        actions: FlowActions,
        playback: PlaybackState = PlaybackState.Idle,
    ) {
        composeRule.setContent {
            Scene {
                FlowPage(
                    state = FlowUiState.Ready(zone, listOf(voice, text), earlierCount),
                    timeSource = FlowFixedTime,
                    actions = actions,
                    playback = playback,
                )
            }
        }
    }

    private companion object {
        const val LONG_SCROLL = 800f
        const val TIMEOUT_MS = 5_000L
    }
}

@Composable
private fun Scene(content: @Composable () -> Unit) {
    LogDateWearTheme(dynamicColor = false) {
        AppScaffold(timeText = {}) { content() }
    }
}

private object FlowFixedTime : TimeSource {
    @Composable
    override fun currentTime(): String = "10:09"
}
