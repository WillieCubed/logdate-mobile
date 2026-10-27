package app.logdate.feature.editor.ui.content

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.client.datastore.EntryStartMode
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class EntryStartModeChoicesTest {
    @Test
    fun `default choices expose the saved selection and select camera`() =
        runDesktopComposeUiTest(width = 360, height = 800) {
            var selected = EntryStartMode.TEXT
            setContent {
                LogDateTheme(dynamicColor = false) {
                    EntryStartModeChoices(selected, handwritingEnabled = true) { selected = it }
                }
            }
            onNodeWithText("Text").assertIsSelected()
            onNodeWithText("Camera").performClick()
            assertEquals(EntryStartMode.CAMERA, selected)
        }
}
