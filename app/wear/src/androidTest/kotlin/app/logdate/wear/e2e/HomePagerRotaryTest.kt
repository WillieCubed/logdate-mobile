package app.logdate.wear.e2e

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performRotaryScrollInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import app.logdate.wear.v2.home.HomeScreen
import app.logdate.wear.v2.home.HomeTags
import app.logdate.wear.v2.theme.LogDateWearTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Drives Home's pager with touch and the crown on a real Wear OS emulator. The pages hold plain
 * lists, so only paging is under test.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class HomePagerRotaryTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun swiping_up_opens_the_moment_list() {
        showHome()

        swipeToFlow()

        assertTrue(isShown(FLOW_FIRST))
    }

    @Test
    fun turning_the_crown_up_at_the_top_of_the_list_returns_to_today() {
        showHome()
        swipeToFlow()

        composeRule.onNodeWithTag(FLOW_LIST).performRotaryScrollInput { rotateToScrollVertically(-CROWN_STEP) }

        waitUntilShown(TODAY_FIRST)
    }

    @Test
    fun turning_the_crown_up_inside_the_list_scrolls_the_list_first() {
        showHome()
        swipeToFlow()
        composeRule.onNodeWithTag(FLOW_LIST).performRotaryScrollInput { rotateToScrollVertically(LONG_SCROLL) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(FLOW_LIST).performRotaryScrollInput { rotateToScrollVertically(-CROWN_STEP) }
        composeRule.waitForIdle()

        assertTrue(composeRule.onNodeWithTag(FLOW_LIST).isDisplayed())
        assertFalse(isShown(TODAY_FIRST))
    }

    @Test
    fun turning_the_crown_down_at_the_end_of_today_opens_the_list() {
        showHome()

        composeRule.onNodeWithTag(TODAY_LIST).performRotaryScrollInput { rotateToScrollVertically(CROWN_STEP) }

        waitUntilShown(FLOW_FIRST)
    }

    @Test
    fun home_stays_on_today_while_recording() {
        showHome(pagingLocked = true)

        composeRule.onNodeWithTag(HomeTags.PAGER).performTouchInput { swipeUp() }
        composeRule.onNodeWithTag(TODAY_LIST).performRotaryScrollInput { rotateToScrollVertically(CROWN_STEP) }
        composeRule.waitForIdle()

        assertTrue(isShown(TODAY_FIRST))
        assertFalse(isShown(FLOW_FIRST))
    }

    private fun showHome(pagingLocked: Boolean = false) {
        composeRule.setContent {
            LogDateWearTheme(dynamicColor = false) {
                AppScaffold {
                    HomeScreen(
                        pagingLocked = pagingLocked,
                        today = { listState -> SampleList(listState, tag = TODAY_LIST, label = "Today", count = 3) },
                        flow = { listState -> SampleList(listState, tag = FLOW_LIST, label = "Earlier", count = 30) },
                    )
                }
            }
        }
    }

    private fun swipeToFlow() {
        composeRule.onNodeWithTag(HomeTags.PAGER).performTouchInput { swipeUp() }
        waitUntilShown(FLOW_FIRST)
    }

    private fun isShown(text: String): Boolean =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() && composeRule.onNodeWithText(text).isDisplayed()

    private fun waitUntilShown(text: String) {
        composeRule.waitUntil(TIMEOUT_MS) { isShown(text) }
    }

    private companion object {
        const val TODAY_LIST = "sample_today_list"
        const val FLOW_LIST = "sample_flow_list"
        const val TODAY_FIRST = "Today 0"
        const val FLOW_FIRST = "Earlier 0"
        const val CROWN_STEP = 120f
        const val LONG_SCROLL = 600f
        const val TIMEOUT_MS = 5_000L
    }
}

@Composable
private fun SampleList(
    listState: TransformingLazyColumnState,
    tag: String,
    label: String,
    count: Int,
) {
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.testTag(tag)) {
            items(count) { index -> Text("$label $index") }
        }
    }
}
