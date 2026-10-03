@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.feature.core.settings.updates.AppUpdateUiState
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A tester has to be able to open Labs on any build, including one whose version the platform
 * cannot report, so the version row in About LogDate counts taps in both states.
 */
@OptIn(ExperimentalTestApi::class)
class AboutLabsEntryTest {
    @Test
    fun `the seventh tap on the installed version shows Labs`() =
        runDesktopComposeUiTest {
            var labsOpened = 0
            setContent { About(AppUpdateUiState(currentVersionName = "1.4.0"), onOpenLabs = { labsOpened += 1 }) }

            repeat(6) { onNodeWithText("App version: 1.4.0").performClick() }
            onNodeWithText("Labs").assertDoesNotExist()

            onNodeWithText("App version: 1.4.0").performClick()
            onNodeWithText("Labs").assertExists().performClick()
            assertEquals(1, labsOpened)
        }

    @Test
    fun `the seventh tap shows Labs when the version is not available`() =
        runDesktopComposeUiTest {
            var labsOpened = 0
            setContent { About(AppUpdateUiState(), onOpenLabs = { labsOpened += 1 }) }

            repeat(6) { onNodeWithText("App version: unknown").performClick() }
            onNodeWithText("Labs").assertDoesNotExist()

            onNodeWithText("App version: unknown").performClick()
            onNodeWithText("Labs").assertExists().performClick()
            assertEquals(1, labsOpened)
        }
}

@Composable
private fun About(
    appUpdateUiState: AppUpdateUiState,
    onOpenLabs: () -> Unit,
) {
    LogDateTheme(dynamicColor = false, darkTheme = false) {
        AdvancedSettingsContent(
            onBack = {},
            appUpdateUiState = appUpdateUiState,
            onCheckForAppUpdates = {},
            onCompleteAppUpdate = {},
            onNavigateToLabs = onOpenLabs,
        )
    }
}
