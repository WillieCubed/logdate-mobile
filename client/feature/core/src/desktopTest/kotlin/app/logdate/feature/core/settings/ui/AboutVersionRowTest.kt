@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.feature.core.settings.updates.AppUpdateUiState
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test

/**
 * About LogDate is where someone checks which build they have when they report a problem or
 * confirm that an update installed, so the version row names the build number as well as the
 * version when the platform reports one.
 */
@OptIn(ExperimentalTestApi::class)
class AboutVersionRowTest {
    @Test
    fun `the version row shows the build number next to the version`() =
        runDesktopComposeUiTest {
            setContent { About(AppUpdateUiState(currentVersionName = "1.4.0", currentVersionCode = 1402)) }

            onNodeWithText("App version: 1.4.0 (1402)").assertExists()
        }

    @Test
    fun `the version row shows only the version when the platform has no build number`() =
        runDesktopComposeUiTest {
            setContent { About(AppUpdateUiState(currentVersionName = "1.4.0")) }

            onNodeWithText("App version: 1.4.0").assertExists()
        }

    @Test
    fun `the version row says the version is unknown when the platform cannot report it`() =
        runDesktopComposeUiTest {
            setContent { About(AppUpdateUiState()) }

            onNodeWithText("App version: unknown").assertExists()
        }
}

@Composable
private fun About(appUpdateUiState: AppUpdateUiState) {
    LogDateTheme(dynamicColor = false, darkTheme = false) {
        AdvancedSettingsContent(
            onBack = {},
            appUpdateUiState = appUpdateUiState,
            onCheckForAppUpdates = {},
            onCompleteAppUpdate = {},
        )
    }
}
