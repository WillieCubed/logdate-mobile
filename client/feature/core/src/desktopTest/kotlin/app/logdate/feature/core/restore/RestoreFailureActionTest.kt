package app.logdate.feature.core.restore

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RestoreFailureActionTest {
    @Test
    fun inaccessibleArchiveExplainsPermissionLossAndReopensPickerAtLargeText() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            var pickerOpens = 0
            setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    LogDateTheme(dynamicColor = false) {
                        RestoreBottomSheet(
                            restoreState = RestoreState.Failed(RestoreError.FILE_NOT_ACCESSIBLE),
                            onSelectFile = {},
                            onUpdateOptions = {},
                            onConfirmImport = {},
                            onCancel = {},
                            onRetry = { pickerOpens++ },
                            onDismiss = {},
                        )
                    }
                }
            }
            onNodeWithText(
                "LogDate couldn't open this archive. If access was removed, choose it again; otherwise choose another copy.",
            ).assertIsDisplayed()
            onNodeWithText("Choose archive").assertIsDisplayed().performClick()
            runOnIdle { assertEquals(1, pickerOpens) }
        }

    @Test
    fun transientFailureStillOffersRetry() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            setContent {
                LogDateTheme(dynamicColor = false) {
                    RestoreBottomSheet(
                        restoreState = RestoreState.Failed(RestoreError.RESTORE_FAILED),
                        onSelectFile = {},
                        onUpdateOptions = {},
                        onConfirmImport = {},
                        onCancel = {},
                        onRetry = {},
                        onDismiss = {},
                    )
                }
            }
            onNodeWithText("Retry").assertIsDisplayed()
        }
}
