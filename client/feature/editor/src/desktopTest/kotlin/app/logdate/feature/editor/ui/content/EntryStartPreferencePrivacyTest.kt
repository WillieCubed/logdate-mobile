package app.logdate.feature.editor.ui.content

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.client.datastore.EntryStartMode
import app.logdate.ui.common.LocalPrivateModalVisible
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class EntryStartPreferencePrivacyTest {
    @Test
    fun lockingHidesAnAlreadyOpenPreferenceDialogAndUnlockRestoresIt() =
        runDesktopComposeUiTest(width = 360, height = 800) {
            val visible = mutableStateOf(true)
            setContent {
                LogDateTheme(dynamicColor = false) {
                    CompositionLocalProvider(LocalPrivateModalVisible provides visible.value) {
                        EntryStartModeDialog(EntryStartMode.TEXT, true, {}, onDismiss = {})
                    }
                }
            }
            onNodeWithText("Done").assertIsDisplayed()
            runOnIdle { visible.value = false }
            onNodeWithText("Done").assertDoesNotExist()
            runOnIdle { visible.value = true }
            onNodeWithText("Done").assertIsDisplayed()
        }
}
