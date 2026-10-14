package app.logdate.wear.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performRotaryScrollInput
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.presentation.MainActivity
import app.logdate.wear.v2.archive.ArchiveTags
import app.logdate.wear.v2.flow.FlowTags
import app.logdate.wear.v2.today.TodayTags
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Opens the Archive from the moment list in the real activity and graph: a note from 9 days ago is
 * not in the list, Earlier opens the Archive, and the Archive shows it.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ArchiveE2ETest {
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
    fun earlier_opens_the_archive_with_a_note_from_9_days_ago() {
        val earlier = Clock.System.now() - EARLIER_DAYS.days
        val note = JournalNote.Text(creationTimestamp = earlier, lastUpdated = earlier, content = CONTENT)
        val notes = koin.get<JournalNotesRepository>()
        runBlocking { notes.create(note) }
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            waitFor { composeRule.onAllNodesWithTag(TodayTags.PAGE) }

            composeRule.onNodeWithTag(TodayTags.PAGE).performScrollToNode(hasTestTag(TodayTags.MOMENTS))
            composeRule.onNodeWithTag(TodayTags.MOMENTS).performClick()
            waitFor { composeRule.onAllNodesWithTag(FlowTags.LIST) }
            assertEquals(0, composeRule.onAllNodesWithText(CONTENT).fetchSemanticsNodes().size)
            composeRule.onNodeWithTag(FlowTags.LIST).performRotaryScrollInput { rotateToScrollVertically(LONG_SCROLL) }
            waitFor { composeRule.onAllNodesWithTag(FlowTags.EARLIER) }
            composeRule.onNodeWithTag(FlowTags.EARLIER).performClick()

            waitFor { composeRule.onAllNodesWithTag(ArchiveTags.LIST) }
            waitFor { composeRule.onAllNodesWithText(CONTENT) }
            composeRule.onAllNodesWithText(CONTENT)[0].assertIsDisplayed()
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
        const val CONTENT = "A note from the second week back"
        const val EARLIER_DAYS = 9
        const val LONG_SCROLL = 800f
        const val TIMEOUT_MS = 10_000L
    }
}
