package app.logdate.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SettingsMasterLayoutTest {
    @Test
    fun largeTextGivesLongWordsTheFullLabelWidthAndKeepsOneAction() = assertLongWordStaysWhole(width = 360)

    @Test
    fun wordWiderThanTheWholeToggleStaysWholeAndKeepsOneAction() = assertLongWordStaysWhole(width = 240)

    private fun assertLongWordStaysWhole(width: Int) =
        runDesktopComposeUiTest(width = width, height = 891) {
            val label = "Use recommendations"
            var changes = 0
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                    LogDateTheme {
                        MasterFeatureToggle(label, false, { changes++ }, modifier = Modifier.padding(horizontal = Spacing.lg))
                    }
                }
            }
            val layouts = mutableListOf<TextLayoutResult>()
            onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                it(layouts)
            }
            val layout = layouts.single()
            val lines =
                (0 until layout.lineCount).map {
                    label.substring(layout.getLineStart(it), layout.getLineEnd(it)).trim()
                }
            assertFalse(lines.any { it == "s" }, "The switch must not force a word into a lone final letter: $lines")
            assertTrue(lines.any { "recommendations" in it }, "The long word must stay on one line: $lines")
            assertFalse(layout.hasVisualOverflow)
            onAllNodes(isToggleable()).assertCountEquals(1)
            onNodeWithText(label).performClick()
            runOnIdle { assertEquals(1, changes) }
        }
}
