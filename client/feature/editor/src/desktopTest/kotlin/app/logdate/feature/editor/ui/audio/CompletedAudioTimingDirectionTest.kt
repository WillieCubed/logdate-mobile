package app.logdate.feature.editor.ui.audio

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CompletedAudioTimingDirectionTest {
    @Test
    fun `left to right timing presents elapsed before total`() = assertTimingOrder(LayoutDirection.Ltr)

    @Test
    fun `right to left timing presents elapsed before total`() = assertTimingOrder(LayoutDirection.Rtl)

    private fun assertTimingOrder(direction: LayoutDirection) =
        runSkikoComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    LogDateTheme(dynamicColor = false) {
                        CompletedAudioTransport(12_000, false, .5f, emptyList(), {}, {}, Modifier, Modifier)
                    }
                }
            }
            val elapsed = onNodeWithText("00:06 /", useUnmergedTree = true)
            val total = onNodeWithTag("audio_block_duration", useUnmergedTree = true)
            assertTrue(
                elapsed.getUnclippedBoundsInRoot().right <= total.getUnclippedBoundsInRoot().left,
                "Numeric timing must display elapsed / total in both layout directions",
            )
            val layouts = mutableListOf<TextLayoutResult>()
            elapsed.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertTrue(layout.getBoundingBox(0).left < layout.getBoundingBox(6).left, "The slash follows elapsed digits")
            onNodeWithTag("completed_audio_waveform", useUnmergedTree = true).assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "00:06 / 00:12"),
            )
        }
}
