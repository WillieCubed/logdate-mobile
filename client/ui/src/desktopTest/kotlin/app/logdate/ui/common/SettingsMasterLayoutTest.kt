package app.logdate.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onFirst
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

@OptIn(ExperimentalTestApi::class)
class SettingsMasterLayoutTest {
    @Test
    fun largeTextGivesLongWordsTheFullLabelWidthAndKeepsOneAction() =
        runDesktopComposeUiTest(width = 360, height = 891) {
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
            assertFalse(layouts.single().hasVisualOverflow)

            // Where a word wraps depends on the platform's fonts, so check the geometry instead: at
            // large text the switch moves below the label, and the label gets the toggle's whole
            // inner width rather than sharing a row with the switch.
            val labelWidth = onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width
            val toggleWidth =
                onAllNodes(isToggleable())
                    .onFirst()
                    .fetchSemanticsNode()
                    .boundsInRoot.width
            val innerWidth = toggleWidth - with(density) { (Spacing.lg * 2).toPx() }
            assertEquals(innerWidth, labelWidth, absoluteTolerance = 1f, "The label must span the toggle's full inner width")
            onAllNodes(isToggleable()).assertCountEquals(1)
            onNodeWithText(label).performClick()
            runOnIdle { assertEquals(1, changes) }
        }
}
