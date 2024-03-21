package app.logdate.feature.library

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.logdate.feature.library.ui.LibraryRoute
import app.logdate.testing.HiltActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class ListFeatureTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltActivity>()

    @Test
    fun testItemIsDisplayed() {
        // TODO: Test that library items are actually displayed
        composeTestRule.setContent {
            LibraryRoute(onGoToItem = { /* no-op */ })
        }
        composeTestRule.onNodeWithTag("item_1").assertExists()
    }
}