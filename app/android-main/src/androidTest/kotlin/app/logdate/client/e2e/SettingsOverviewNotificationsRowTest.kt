package app.logdate.client.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.feature.core.settings.ui.SettingsOverviewContent
import app.logdate.feature.core.settings.ui.UserProfile
import kotlin.test.Test
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsOverviewNotificationsRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun notificationsRowIsHiddenWhenCallbackIsMissing() {
        composeRule.setContent {
            SettingsOverviewContent(
                onBack = {},
                onNavigateToProfile = {},
                onNavigateToAccount = {},
                onNavigateToDevices = {},
                onNavigateToReset = {},
                onNavigateToLocation = {},
                onNavigateToPrivacy = {},
                onNavigateToMemories = {},
                onNavigateToTimeline = {},
                onNavigateToSync = {},
                onNavigateToExport = {},
                userProfile = UserProfile(name = "Test", username = "test", isAuthenticated = true),
            )
        }

        composeRule.onNodeWithText("Notifications").assertDoesNotExist()
    }

    @Test
    fun notificationsRowIsShownWhenCallbackIsProvided() {
        composeRule.setContent {
            SettingsOverviewContent(
                onBack = {},
                onNavigateToProfile = {},
                onNavigateToAccount = {},
                onNavigateToDevices = {},
                onNavigateToReset = {},
                onNavigateToLocation = {},
                onNavigateToPrivacy = {},
                onNavigateToMemories = {},
                onNavigateToNotifications = {},
                onNavigateToTimeline = {},
                onNavigateToSync = {},
                onNavigateToExport = {},
                userProfile = UserProfile(name = "Test", username = "test", isAuthenticated = true),
            )
        }

        composeRule.onNodeWithText("Notifications").performScrollTo().assertIsDisplayed()
    }
}
