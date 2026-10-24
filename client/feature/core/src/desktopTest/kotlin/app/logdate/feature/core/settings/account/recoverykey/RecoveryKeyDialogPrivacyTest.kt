package app.logdate.feature.core.settings.account.recoverykey

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.ui.common.LocalPrivateModalVisible
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class RecoveryKeyDialogPrivacyTest {
    @Test
    fun `confirmation hides while locked and returns after unlocking`() =
        runDesktopComposeUiTest {
            val visible = mutableStateOf(true)
            setContent {
                LogDateTheme {
                    CompositionLocalProvider(LocalPrivateModalVisible provides visible.value) {
                        TurnOnRecoveryKeyDialog(
                            state =
                                RecoveryKeyUiState.Ready(
                                    handle = "alice.logdate.app",
                                    serverName = "LogDate Cloud",
                                    phrase = PhraseStatus.CONFIRMED,
                                    key = KeyStatus.OFF,
                                ),
                            onConfirm = {},
                            onDismiss = {},
                        )
                    }
                }
            }
            onNodeWithText("Turn on recovery key?").assertIsDisplayed()
            runOnIdle { visible.value = false }
            onNodeWithText("Turn on recovery key?").assertDoesNotExist()
            runOnIdle { visible.value = true }
            onNodeWithText("Turn on recovery key?").assertIsDisplayed()
        }
}
