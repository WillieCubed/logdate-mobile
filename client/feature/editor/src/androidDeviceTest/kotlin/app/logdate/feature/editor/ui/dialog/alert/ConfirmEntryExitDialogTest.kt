package app.logdate.feature.editor.ui.dialog.alert

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

class ConfirmEntryExitDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun persistenceInProgressDisablesEveryExitAction() {
        composeTestRule.setContent {
            ConfirmEntryExitDialog(
                onCancel = {},
                onConfirm = {},
                onSaveAsDraft = {},
                actionsEnabled = false,
            )
        }

        composeTestRule.onNodeWithTag("exit_dialog_cancel").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("exit_dialog_discard").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("exit_dialog_save_draft").assertIsNotEnabled()
    }
}
