package app.logdate.client.e2e

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.ui.LockableContent
import app.logdate.feature.core.AppAuthState
import app.logdate.ui.common.AlertDialog
import app.logdate.ui.theme.LogDateTheme
import org.junit.Rule
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class RestoredJournalLockRuntimeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun lockedJournalAndControlsAreAbsentFromTheAccessibilityTree(): Unit =
        with(compose) {
            setContent {
                LogDateTheme {
                    LockableContent(true, "Alex", {}) {
                        Column {
                            Text("Private journal entry")
                            Button(onClick = {}) { Text("Delete private entry") }
                        }
                    }
                }
            }
            onNodeWithText("Private journal entry").assertDoesNotExist()
            onNodeWithText("Private journal entry", useUnmergedTree = true).assertIsNotDisplayed()
            onNodeWithText("Delete private entry").assertDoesNotExist()
            onNodeWithText("Use passcode").assertIsDisplayed()
        }

    @Test
    fun lockingBlocksUnderlyingTapsWithoutDiscardingTheEditorDraft(): Unit =
        with(compose) {
            var locked by mutableStateOf(false)
            var clicks = 0
            var disposals = 0
            setContent {
                LogDateTheme {
                    LockableContent(locked, "Alex", {}) {
                        var draft by remember { mutableStateOf("Original draft") }
                        DisposableEffect(Unit) { onDispose { disposals++ } }
                        Column {
                            OutlinedTextField(draft, { draft = it }, modifier = Modifier.testTag("private_draft"))
                            Button(onClick = { clicks++ }) { Text("Save private entry") }
                        }
                    }
                }
            }
            onNodeWithTag("private_draft").performTextReplacement("Unsubmitted private draft")
            val saveTarget = onNodeWithText("Save private entry").fetchSemanticsNode().boundsInRoot.center
            runOnIdle { locked = true }
            waitForIdle()
            onRoot().performTouchInput { click(saveTarget) }
            runOnIdle {
                assertEquals(0, clicks)
                assertEquals(0, disposals)
                locked = false
            }
            onNodeWithTag("private_draft").assertTextEquals("Unsubmitted private draft")
            runOnIdle { assertEquals(0, disposals) }
        }

    @Test
    fun lockingRevokesAndRejectsEditorFocusWhileUnlockStillWorks(): Unit =
        with(compose) {
            var locked by mutableStateOf(false)
            var editorFocused = false
            var unlocks = 0
            val requester = FocusRequester()
            setContent {
                LogDateTheme {
                    LockableContent(locked, "Alex", { unlocks++ }) {
                        OutlinedTextField(
                            "Private draft",
                            {},
                            modifier = Modifier.focusRequester(requester).onFocusChanged { editorFocused = it.isFocused },
                        )
                    }
                }
            }
            runOnIdle { requester.requestFocus() }
            runOnIdle {
                assertTrue(editorFocused)
                locked = true
            }
            waitForIdle()
            runOnIdle {
                assertFalse(editorFocused)
                requester.requestFocus()
            }
            runOnIdle { assertFalse(editorFocused) }
            onRoot().performKeyInput { pressKey(Key.Tab) }
            runOnIdle { assertFalse(editorFocused) }
            onNodeWithText("Use passcode").performClick()
            runOnIdle { assertEquals(1, unlocks) }
        }

    @Test
    fun lockingConcealsAnAlreadyOpenPrivateDialog(): Unit =
        with(compose) {
            var locked by mutableStateOf(false)
            var confirmations = 0
            setContent {
                LogDateTheme {
                    LockableContent(locked, "Alex", {}) {
                        Text("Private journal")
                        AlertDialog(
                            onDismissRequest = {},
                            title = { Text("Delete My private journal?") },
                            confirmButton = {
                                TextButton(onClick = { confirmations++ }) { Text("Confirm deletion") }
                            },
                        )
                    }
                }
            }
            onNodeWithText("Delete My private journal?").assertIsDisplayed()
            runOnIdle { locked = true }
            onNodeWithText("Delete My private journal?").assertDoesNotExist()
            onNodeWithText("Confirm deletion").assertDoesNotExist()
            onNodeWithText("Use passcode").assertIsDisplayed().performClick()
            runOnIdle { assertEquals(0, confirmations) }
        }

    @Test
    fun enrollmentActionIsReachableAtTwoHundredPercentInCompactLandscape(): Unit =
        verifyLandscape(
            AppAuthState.REQUEST_ENROLLMENT,
            "Set up device security",
        )

    @Test
    fun unlockActionIsReachableAtTwoHundredPercentInCompactLandscape(): Unit =
        verifyLandscape(
            AppAuthState.REQUIRE_PROMPT,
            "Use passcode",
        )

    private fun verifyLandscape(
        authState: AppAuthState,
        action: String,
    ) = with(compose) {
        var unlocks = 0
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                LogDateTheme {
                    Box(Modifier.fillMaxWidth().height(280.dp)) {
                        LockableContent(
                            true,
                            "Alexandria Elizabeth with a long restored display name",
                            { unlocks++ },
                            authState = authState,
                        ) { Text("Private restored journal", modifier = Modifier.fillMaxSize()) }
                    }
                }
            }
        }
        onNodeWithText(action).performScrollTo().assertIsDisplayed().performClick()
        runOnIdle { assertEquals(1, unlocks) }
        onNodeWithText("Private restored journal").assertDoesNotExist()
    }
}
