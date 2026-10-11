package app.logdate.wear.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performRotaryScrollInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.presentation.MainActivity
import app.logdate.wear.v2.flow.FlowTags
import app.logdate.wear.v2.home.HomeTags
import app.logdate.wear.v2.memories.MemoryCardTags
import app.logdate.wear.v2.moments.MomentTags
import app.logdate.wear.v2.today.TodayTags
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * Shows On this day in the real activity and graph: a note from a year ago today is recalled through
 * the shared use case, the card appears after the moments, and it opens that note. No Rewind card
 * appears, since nothing on the watch writes Rewinds.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class MemoriesE2ETest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val koin get() = KoinJavaComponent.getKoin()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun openTheNewWatchApp() {
        setNewWatchApp(enabled = true)
    }

    @After
    fun tearDown() {
        scenario?.onActivity { it.finish() }
        setNewWatchApp(enabled = false)
    }

    @Test
    fun a_note_from_a_year_ago_shows_on_this_day_and_opens() {
        val zone = TimeZone.currentSystemDefault()
        val lastYear =
            Clock.System
                .now()
                .toLocalDateTime(zone)
                .date
                .minus(1, DateTimeUnit.YEAR)
        val moment = lastYear.atStartOfDayIn(zone) + NOON_HOURS.hours
        val note = JournalNote.Text(creationTimestamp = moment, lastUpdated = moment, content = CONTENT)
        val notes = koin.get<JournalNotesRepository>()
        runBlocking { notes.create(note) }
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            waitFor { composeRule.onAllNodesWithTag(TodayTags.PAGE) }

            composeRule.onNodeWithTag(HomeTags.PAGER).performTouchInput { swipeUp() }
            waitFor { composeRule.onAllNodesWithTag(FlowTags.LIST) }
            composeRule.onNodeWithTag(FlowTags.LIST).performRotaryScrollInput { rotateToScrollVertically(LONG_SCROLL) }
            waitFor { composeRule.onAllNodesWithTag(MemoryCardTags.ON_THIS_DAY) }
            assertEquals(0, composeRule.onAllNodesWithTag(MemoryCardTags.REWIND).fetchSemanticsNodes().size)
            composeRule.onNodeWithTag(MemoryCardTags.ON_THIS_DAY).performClick()

            waitFor { composeRule.onAllNodesWithTag(MomentTags.DETAILS) }
            waitFor { composeRule.onAllNodesWithText(CONTENT) }
        } finally {
            runBlocking { notes.removeById(note.uid) }
        }
    }

    private fun setNewWatchApp(enabled: Boolean) =
        runBlocking {
            koin.get<FeatureFlagStore>().setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, enabled)
        }

    private fun waitFor(nodes: () -> SemanticsNodeInteractionCollection) {
        composeRule.waitUntil(TIMEOUT_MS) { nodes().fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val CONTENT = "A note from this day last year"
        const val NOON_HOURS = 12
        const val LONG_SCROLL = 800f
        const val TIMEOUT_MS = 10_000L
    }
}
