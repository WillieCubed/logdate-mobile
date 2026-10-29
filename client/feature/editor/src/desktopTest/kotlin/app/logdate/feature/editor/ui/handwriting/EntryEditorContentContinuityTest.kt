package app.logdate.feature.editor.ui.handwriting

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextRange
import app.logdate.feature.editor.ui.LocalEditorOwnerAttachmentObserver
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.text.LOGDATE_EDITOR_TEXT_INPUT_TAG
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class EntryEditorContentContinuityTest {
    @Test
    fun focusedInputDoesNotExposeTheInactiveOverviewPullAsAProgressBar() =
        runDesktopComposeUiTest(width = 360, height = 800) {
            val block = TextBlockUiState(content = "One accessible input")
            setContent { EntryPageStudy(initialBlocks = listOf(block)) }
            val pull = onNodeWithTag("study_edge_pull", useUnmergedTree = true)
            pull.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            val input = onNodeWithTag(LOGDATE_EDITOR_TEXT_INPUT_TAG, useUnmergedTree = true).fetchSemanticsNode().id
            onNodeWithTag("memory_block_${block.id}").performSemanticsAction(SemanticsActions.OnClick) { it() }
            pull.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ProgressBarRangeInfo))
            assertEquals(input, onNodeWithTag(LOGDATE_EDITOR_TEXT_INPUT_TAG, useUnmergedTree = true).fetchSemanticsNode().id)
            onNodeWithContentDescription("Return to entry").performSemanticsAction(SemanticsActions.OnClick) { it() }
            pull.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        }

    @Test
    fun focusAndInterruptedReturnKeepOneTextInputOwner() =
        runDesktopComposeUiTest(width = 360, height = 800) {
            val block = TextBlockUiState(content = "One retained memory")
            val attachments = mutableListOf<Boolean>()
            val observe: (kotlin.uuid.Uuid, Boolean) -> Unit = { id, attached -> if (id == block.id) attachments += attached }
            setContent {
                CompositionLocalProvider(LocalEditorOwnerAttachmentObserver provides observe) {
                    EntryPageStudy(initialBlocks = listOf(block))
                }
            }
            waitForIdle()
            val original = onNodeWithTag(LOGDATE_EDITOR_TEXT_INPUT_TAG, useUnmergedTree = true).fetchSemanticsNode().id
            val originalSurface = onNodeWithTag("memory_block_${block.id}", useUnmergedTree = true).fetchSemanticsNode().id
            assertEquals(listOf(true), attachments)
            mainClock.autoAdvance = false

            fun assertFrames(count: Int) {
                repeat(count) {
                    mainClock.advanceTimeBy(16)
                    val fields = onAllNodesWithTag(LOGDATE_EDITOR_TEXT_INPUT_TAG, useUnmergedTree = true).fetchSemanticsNodes()
                    assertEquals(1, fields.size, "Outgoing and incoming surfaces must share one editable owner")
                    assertEquals(original, fields.single().id, "A focus change must retain the text input node")
                    assertTrue(attachments.none { !it }, "A live input must remain attached throughout focus and interrupted return")
                    assertEquals(
                        originalSurface,
                        onNodeWithTag("memory_block_${block.id}", useUnmergedTree = true).fetchSemanticsNode().id,
                        "The actual memory surface must move with its input owner",
                    )
                }
            }

            onNodeWithTag("memory_block_${block.id}").performSemanticsAction(SemanticsActions.OnClick) { it() }
            assertFrames(8)
            onNodeWithTag("memory_block_${block.id}", useUnmergedTree = true).assertIsEnabled()
            onNodeWithTag(LOGDATE_EDITOR_TEXT_INPUT_TAG, useUnmergedTree = true).performTextInputSelection(TextRange(4, 12))
            assertFrames(1)
            onNodeWithTag(LOGDATE_EDITOR_TEXT_INPUT_TAG, useUnmergedTree = true).assert(
                SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(4, 12)),
            )
            onNodeWithContentDescription("Return to entry").performSemanticsAction(SemanticsActions.OnClick) { it() }
            assertFrames(5)
            onNodeWithTag("memory_block_${block.id}").performSemanticsAction(SemanticsActions.OnClick) { it() }
            assertFrames(60)
            onNodeWithTag(LOGDATE_EDITOR_TEXT_INPUT_TAG, useUnmergedTree = true).assert(
                SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(4, 12)),
            )
            mainClock.autoAdvance = true
        }
}
