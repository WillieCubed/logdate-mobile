package app.logdate.reminders.e2e

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.domain.reminders.ReminderPreferencesState
import app.logdate.feature.core.settings.ui.ReminderSettingsContent
import app.logdate.feature.core.settings.ui.ReminderSettingsFix
import app.logdate.ui.theme.LogDateTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class ReminderSettingsRuntimeTest {
    @get:Rule val compose = createComposeRule()
    private val choices = mutableListOf<Boolean>()
    private val fixes = mutableListOf<ReminderSettingsFix>()

    private fun content(
        busy: Boolean = false,
        failed: Boolean = false,
        enabled: Boolean = false,
    ) {
        compose.setContent {
            LogDateTheme {
                ReminderSettingsContent(
                    ReminderPreferencesState(ready = true, rolloutEnabled = true, enabled = enabled),
                    notificationsAllowed = false,
                    locationAllowed = false,
                    streaksEnabled = false,
                    busy = busy,
                    saveFailed = failed,
                    onEnabled = { choices.add(it) },
                    onFix = { fixes.add(it) },
                    onBack = {},
                )
            }
        }
    }

    @Test fun oneExplicitSwitchControlsAllReminderFamilies() {
        content()
        compose.onAllNodes(isToggleable()).assertCountEquals(1)
        compose.onNode(isToggleable()).assertIsOff().performClick()
        assertEquals(listOf(true), choices)
        compose.onNodeWithText("At most one reminder a day, on your phone or watch.").assertIsDisplayed()
        compose.onNodeWithText("Drafts and memories").performScrollTo().assertIsDisplayed()
    }

    @Test fun blockedRequirementsExposeTheirRealFixActions() {
        content(enabled = true)
        compose.onNodeWithText("Notifications").performScrollTo().performClick()
        compose.onNodeWithText("New places").performScrollTo().performClick()
        compose.onNodeWithText("Campfire").performScrollTo().performClick()
        assertEquals(listOf(ReminderSettingsFix.NOTIFICATIONS, ReminderSettingsFix.PLACES, ReminderSettingsFix.STREAKS), fixes)
    }

    @Test fun savingDisablesTheSwitchAndFailureKeepsThePreviousChoiceVisible() {
        content(busy = true, failed = true)
        compose.onNode(isToggleable()).assertIsOff().assertIsNotEnabled()
        compose.onNodeWithText("Couldn’t save your reminder preference. Try again.").assertIsDisplayed()
    }

    @Test fun featureOffDisablesDependentRowsAndInformationDoesNotPretendToBeAnAction() {
        content()
        compose.onNodeWithText("Notifications").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Drafts and memories").performScrollTo().assertHasNoClickAction()
    }
}
