package app.logdate.ui.handwriting

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import app.logdate.shared.model.HandwritingDocument
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class HandwritingPreviewTest {
    @Test
    fun unreadableInkHasAnExplicitPreservedRecordState() =
        runComposeUiTest {
            setContent { MaterialTheme { HandwritingPreview(HandwritingDocument(), unreadable = true) } }
            onNodeWithText("Handwriting unavailable. Original ink is preserved.").assertExists()
        }
}
