package app.logdate.feature.editor.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MissingMarkContextTest {
    @Test
    fun `unavailable marked moment offers explicit new entry recovery`() =
        runSkikoComposeUiTest(size = Size(390f, 780f)) {
            var continued = 0
            setContent {
                LogDateTheme(dynamicColor = false) {
                    MissingMarkContextContent { continued += 1 }
                }
            }
            waitForIdle()
            assertEquals(0, continued)
            onNodeWithText("Continue as a new entry").performClick()
            assertEquals(1, continued)
        }
}
