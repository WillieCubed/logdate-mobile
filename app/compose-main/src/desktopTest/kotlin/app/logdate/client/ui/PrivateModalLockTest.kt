@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTestApi::class)

package app.logdate.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.ui.common.AlertDialog
import app.logdate.ui.common.DatePickerDialog
import app.logdate.ui.common.Dialog
import app.logdate.ui.common.DropdownMenu
import app.logdate.ui.common.ModalBottomSheet
import app.logdate.ui.common.TooltipBox
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals

class PrivateModalLockTest {
    @Test
    fun privateAlertReappearsAfterUnlockWithoutDismissingCallerState() =
        verifyModal { onDismiss ->
            AlertDialog(onDismissRequest = onDismiss, confirmButton = {}, text = { Text(PRIVATE_TEXT) })
        }

    @Test
    fun privateDialogReappearsAfterUnlockWithoutDismissingCallerState() =
        verifyModal { onDismiss ->
            Dialog(onDismissRequest = onDismiss) { Text(PRIVATE_TEXT) }
        }

    @Test
    fun privateMenuReappearsAfterUnlockWithoutDismissingCallerState() =
        verifyModal { onDismiss ->
            DropdownMenu(expanded = true, onDismissRequest = onDismiss) { Text(PRIVATE_TEXT) }
        }

    @Test
    fun privateSheetReappearsAfterUnlockWithoutDismissingCallerState() =
        verifyModal { onDismiss ->
            ModalBottomSheet(onDismissRequest = onDismiss) { Text(PRIVATE_TEXT) }
        }

    @Test
    fun privateDatePickerReappearsAfterUnlockWithoutDismissingCallerState() =
        verifyModal { onDismiss ->
            DatePickerDialog(onDismissRequest = onDismiss, confirmButton = {}) { Text(PRIVATE_TEXT) }
        }

    @Test
    fun lockingConcealsPrivateTooltipWithoutRecreatingItsTrigger() =
        runDesktopComposeUiTest {
            var locked by mutableStateOf(false)
            var triggerDisposals = 0
            setContent {
                LogDateTheme {
                    LockableContent(locked, "Alex", { locked = false }) {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                            tooltip = { PlainTooltip { Text(PRIVATE_TEXT) } },
                            state = rememberTooltipState(initialIsVisible = true, isPersistent = true),
                        ) {
                            DisposableEffect(Unit) { onDispose { triggerDisposals++ } }
                            Text("Private tooltip trigger")
                        }
                    }
                }
            }
            onNodeWithText(PRIVATE_TEXT).assertIsDisplayed()
            runOnIdle { locked = true }
            onNodeWithText(PRIVATE_TEXT).assertDoesNotExist()
            onNodeWithText("Use passcode").performClick()
            onNodeWithText("Private tooltip trigger").assertIsDisplayed()
            runOnIdle { assertEquals(0, triggerDisposals) }
        }

    private fun verifyModal(modal: @Composable (() -> Unit) -> Unit) =
        runDesktopComposeUiTest {
            var locked by mutableStateOf(false)
            var dismissed = 0
            setContent {
                LogDateTheme {
                    LockableContent(locked, "Alex", { locked = false }) {
                        var draft by remember { mutableStateOf("Unsaved private draft") }
                        Box {
                            TextButton(onClick = { draft = "Edited private draft" }) { Text(draft) }
                            modal { dismissed++ }
                        }
                    }
                }
            }
            onNodeWithText(PRIVATE_TEXT).assertIsDisplayed()
            runOnIdle { locked = true }
            onNodeWithText(PRIVATE_TEXT).assertDoesNotExist()
            onNodeWithText("Use passcode").assertIsDisplayed().performClick()
            onNodeWithText(PRIVATE_TEXT).assertIsDisplayed()
            onNodeWithText("Unsaved private draft").assertIsDisplayed()
            runOnIdle { assertEquals(0, dismissed, "Locking must not dismiss or confirm a private action") }
        }

    private companion object {
        const val PRIVATE_TEXT = "Private restored journal detail"
    }
}
