package app.logdate.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SettingsDescriptionLayoutTest {
    @Test
    fun toggleExplanationRemainsCompleteAtLargeText() = verify(Kind.Toggle)

    @Test
    fun linkedToggleExplanationRemainsCompleteAtLargeText() = verify(Kind.LinkedToggle)

    @Test
    fun navigationExplanationRemainsCompleteAtLargeText() = verify(Kind.Navigation)

    private fun verify(kind: Kind) =
        runDesktopComposeUiTest(width = 360, height = 891) {
            val description = "Allow the Android launcher and other system search surfaces to show your LogDate entries and journals."
            setContent {
                LogDateTheme {
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                        Box(Modifier.width(320.dp)) {
                            when (kind) {
                                Kind.Toggle -> ToggleSettingsItem("System search", description, false, {})
                                Kind.LinkedToggle -> LinkedToggleSettingsItem("System search", description, false, {}, {})
                                Kind.Navigation -> SettingsNavigationItem("System search", description, {}, {})
                            }
                        }
                    }
                }
            }
            val layouts = mutableListOf<TextLayoutResult>()
            onNodeWithText(description, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                it(layouts)
            }
            val layout = layouts.single()
            assertTrue(layout.lineCount > 2)
            for (line in 0 until layout.lineCount) assertFalse(layout.isLineEllipsized(line))
            assertFalse(layout.hasVisualOverflow)
        }

    private enum class Kind { Toggle, LinkedToggle, Navigation }
}
