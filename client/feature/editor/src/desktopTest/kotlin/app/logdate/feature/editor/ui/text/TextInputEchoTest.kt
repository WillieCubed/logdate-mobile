package app.logdate.feature.editor.ui.text

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.feature.editor.ui.editor.LocalEditorInputTokens
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

@OptIn(ExperimentalTestApi::class)
class TextInputEchoTest {
    @Test
    fun restorationRejectsAFormerRuntimeWithTheSameMemoryIdAndLowerRevision() =
        runDesktopComposeUiTest {
            var mounted by mutableStateOf(true)
            var block by mutableStateOf(TextBlockUiState())
            var runtime by mutableStateOf(Uuid.random())
            setContent {
                val holder = rememberSaveableStateHolder()
                CompositionLocalProvider(
                    LocalEditorTextInputSession provides EditorTextInputSession(0, runtime) { _, _, _ -> 9L },
                ) {
                    if (mounted) {
                        holder.SaveableStateProvider("input") {
                            LogDateTheme(dynamicColor = false) {
                                TextBlockContent(block, onTextChanged = {}, onFocused = {})
                            }
                        }
                    }
                }
            }
            val input = onNodeWithTag(LOGDATE_EDITOR_TEXT_INPUT_TAG)
            input.performTextInput("Former owner")
            runOnIdle { mounted = false }
            waitForIdle()
            runOnIdle {
                runtime = Uuid.random()
                block = block.copy(content = "New owner")
                mounted = true
            }
            input.assertTextEquals("New owner")
            input.performTextInput(" content")
            input.assertTextEquals("New owner content")
        }

    @Test
    fun savedInputRetainsItsAcknowledgementWhileRenderedStateIsStillBehind() =
        runDesktopComposeUiTest {
            var mounted by mutableStateOf(true)
            val block = TextBlockUiState()
            setContent {
                val holder = rememberSaveableStateHolder()
                CompositionLocalProvider(LocalEditorTextInputSession provides EditorTextInputSession(0) { _, _, _ -> 1L }) {
                    if (mounted) {
                        holder.SaveableStateProvider("input") {
                            LogDateTheme(dynamicColor = false) {
                                TextBlockContent(block, onTextChanged = {}, onFocused = {})
                            }
                        }
                    }
                }
            }
            val input = onNodeWithTag(LOGDATE_EDITOR_TEXT_INPUT_TAG)
            input.performTextInput("Retained")
            input.assertTextEquals("Retained")
            runOnIdle { mounted = false }
            waitForIdle()
            runOnIdle { mounted = true }
            input.assertTextEquals("Retained")
        }

    @Test
    fun delayedAcceptedEchoCannotRewindTextOrMoveTheInsertionPoint() =
        runDesktopComposeUiTest(width = 360, height = 800) {
            var block by mutableStateOf(TextBlockUiState())
            var renderedRevision by mutableStateOf(0L)
            var acceptedRevision = 0L
            val accepted = mutableListOf<String>()
            setContent {
                CompositionLocalProvider(
                    LocalEditorTextInputSession provides
                        EditorTextInputSession(renderedRevision) { _, _, text ->
                            accepted += text
                            ++acceptedRevision
                        },
                ) {
                    LogDateTheme(dynamicColor = false) {
                        TextBlockContent(block, onTextChanged = {}, onFocused = {})
                    }
                }
            }
            val input = onNodeWithTag(LOGDATE_EDITOR_TEXT_INPUT_TAG)
            input.performTextInput("A qu")
            input.performTextInput("iet")
            runOnIdle {
                block = block.copy(content = "A qu")
                renderedRevision = 1
            }
            input.assertTextEquals("A quiet")
            input.performTextInput(" moment.")
            input.assertTextEquals("A quiet moment.")
            assertEquals(listOf("A qu", "A quiet", "A quiet moment."), accepted)
            runOnIdle {
                block = block.copy(content = "A quiet moment.")
                renderedRevision = 3
            }
            input.assertTextEquals("A quiet moment.")
            runOnIdle {
                block = block.copy(content = "Restored externally")
                renderedRevision = 4
            }
            input.assertTextEquals("Restored externally")
        }

    @Test
    fun rejectedCommitDoesNotLeaveUnacceptedTextVisible() =
        runDesktopComposeUiTest {
            val block = TextBlockUiState(content = "Retain")
            setContent {
                CompositionLocalProvider(LocalEditorTextInputSession provides EditorTextInputSession(7) { _, _, _ -> null }) {
                    LogDateTheme(dynamicColor = false) {
                        TextBlockContent(block, onTextChanged = {}, onFocused = {})
                    }
                }
            }
            val input = onNodeWithTag(LOGDATE_EDITOR_TEXT_INPUT_TAG)
            input.performTextInput(" rejected")
            input.assertTextEquals("Retain")
        }

    @Test
    fun acceptedReplacementWithSameMemoryIdStartsANewTextOwner() =
        runDesktopComposeUiTest {
            var block by mutableStateOf(TextBlockUiState(content = "First"))
            var token by mutableStateOf(Uuid.random())
            var revision by mutableStateOf(0L)
            setContent {
                CompositionLocalProvider(
                    LocalEditorInputTokens provides mapOf(block.id to token),
                    LocalEditorTextInputSession provides EditorTextInputSession(revision) { _, _, _ -> 9L },
                ) {
                    LogDateTheme(dynamicColor = false) {
                        TextBlockContent(block, onTextChanged = {}, onFocused = {})
                    }
                }
            }
            val input = onNodeWithTag(LOGDATE_EDITOR_TEXT_INPUT_TAG)
            input.performTextInput(" pending")
            runOnIdle {
                token = Uuid.random()
                block = block.copy(content = "Second")
                revision = 10
            }
            input.assertTextEquals("Second")
            input.performTextInput(" owner")
            input.assertTextEquals("Second owner")
        }
}
