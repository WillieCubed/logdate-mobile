package app.logdate.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class MarkdownPreviewLayoutTest {
    @Test
    fun `preview keeps one blank line around headings including consecutive ones`() =
        runDesktopComposeUiTest(width = 320, height = 1200) {
            var layout: TextLayoutResult? = null
            setContent {
                LogDateTheme(dynamicColor = false) {
                    Text(
                        text =
                            buildMarkdownPreview(
                                "Intro\n\n# Title\n## Subtitle\n\nBody\n\n### Section\n\n#### Detail",
                                markdownPreviewStyles(),
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        onTextLayout = { layout = it },
                    )
                }
            }
            waitForIdle()

            val result = assertNotNull(layout)
            val lines =
                (0 until result.lineCount).map { line ->
                    result.layoutInput.text.text
                        .substring(result.getLineStart(line), result.getLineEnd(line, visibleEnd = true))
                        .trim('\n')
                }
            assertEquals(listOf("Intro", "", "Title", "Subtitle", "", "Body", "", "Section", "", "Detail"), lines)
        }
}
