package app.logdate.feature.editor.ui.handwriting

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import app.logdate.client.datastore.EntryStartMode
import app.logdate.feature.editor.ui.editor.EditorState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.ui.creation.LocalEntryCreationProgress
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class EntryCreationRoutePhaseTest {
    @Test
    fun partialRouteRaisesTheDockButCannotAcceptModeInputOrAccessibilityFocus() =
        runDesktopComposeUiTest(width = 400, height = 800) {
            val phase = mutableFloatStateOf(1f)
            val block = TextBlockUiState()
            val editor = EditorState(blocks = listOf(block), expandedBlockId = block.id)
            val page = EntryPageOverviewState(false)
            var changes = 0
            setContent {
                CompositionLocalProvider(LocalEntryCreationProgress provides phase.floatValue) {
                    LogDateTheme {
                        EntryPageChrome(editor, page, EntryStartMode.TEXT, {}, {}, { changes++ }, header = {}) {}
                    }
                }
            }
            waitForIdle()
            val full = onNodeWithTag("entry_creation_actions").getUnclippedBoundsInRoot()
            runOnIdle { phase.floatValue = 0.775f }
            waitForIdle()
            val partial = onNodeWithTag("entry_creation_actions").getUnclippedBoundsInRoot()
            assertTrue(partial.bottom - partial.top > 0.dp && partial.bottom - partial.top < full.bottom - full.top)
            assertTrue(partial.top > full.top, "The same dock must rise into place as the route is revealed")
            onNodeWithTag(
                "entry_capture_tools",
                useUnmergedTree = true,
            ).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
            onNodeWithContentDescription("Handwrite", useUnmergedTree = true).performTouchInput {
                down(center)
                up()
            }
            assertEquals(0, changes)
            runOnIdle { phase.floatValue = 1f }
            waitForIdle()
            onNodeWithContentDescription("Handwrite").performTouchInput {
                down(center)
                up()
            }
            assertEquals(1, changes)
        }
}
