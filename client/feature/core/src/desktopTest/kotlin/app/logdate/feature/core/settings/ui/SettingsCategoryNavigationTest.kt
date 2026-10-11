package app.logdate.feature.core.settings.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.navigation3.runtime.NavKey
import app.logdate.feature.core.settings.navigation.PrivacySettingsRoute
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SettingsCategoryNavigationTest {
    @Test
    fun signedOutCategoriesKeepTheSelectedDirectLinkedAccountReachable() =
        runDesktopComposeUiTest(width = 360, height = 900) {
            setContent {
                LogDateTheme {
                    SettingsCategoryNavigation(
                        selectedSection = "account",
                        onNavigateTo = {},
                        onBack = {},
                        onNavigateToProfile = {},
                        isAuthenticated = false,
                    )
                }
            }
            onNodeWithText("Account").assertIsSelected()
            onAllNodesWithText("Devices").assertCountEquals(0)
        }

    @Test
    fun selectedSectionIsVisibleAndAnotherCategoryNavigates() =
        runDesktopComposeUiTest(width = 360, height = 900) {
            var navigated: NavKey? = null
            setContent {
                LogDateTheme {
                    SettingsCategoryNavigation(
                        selectedSection = "account",
                        onNavigateTo = { navigated = it },
                        onBack = {},
                        onNavigateToProfile = {},
                    )
                }
            }
            onNodeWithText("Account").performScrollTo().assertIsSelected()
            onNodeWithText("Privacy & Security").performScrollTo().assertIsNotSelected().performClick()
            assertEquals(PrivacySettingsRoute, navigated)
        }
}
