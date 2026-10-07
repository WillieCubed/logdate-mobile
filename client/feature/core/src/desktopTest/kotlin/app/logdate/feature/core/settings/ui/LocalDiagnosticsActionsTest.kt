package app.logdate.feature.core.settings.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class LocalDiagnosticsActionsTest {
    @Test
    fun reportCanBeSharedWithoutSavedEvents() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            var exports = 0
            setContent {
                LogDateTheme(darkTheme = false) {
                    LocalDiagnosticsSettingsSection(
                        state = LocalDiagnosticsState(),
                        onPreview = {},
                        onExport = { exports++ },
                        onClear = {},
                        onSetVerboseEnabled = {},
                    )
                }
            }

            onNodeWithText("Share diagnostic ZIP").assertIsEnabled().performClick()
            assertEquals(1, exports)
            onNodeWithText("Clear local history").assertIsNotEnabled()
        }

    @Test
    fun emptyReportPreviewPreservesAvailableReportDetails() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            val preview = "No saved sync events. Dropped events: 2."
            setContent {
                LogDateTheme(darkTheme = false) {
                    LocalDiagnosticsSettingsSection(
                        state = LocalDiagnosticsState(preview = preview),
                        onPreview = {},
                        onExport = {},
                        onClear = {},
                        onSetVerboseEnabled = {},
                    )
                }
            }

            onNodeWithText("Preview report").performClick()
            onNodeWithText(preview).assertExists()
        }
}
