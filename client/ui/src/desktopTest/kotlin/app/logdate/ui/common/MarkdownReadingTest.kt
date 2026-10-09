package app.logdate.ui.common

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class MarkdownReadingTest {
    @Test
    fun `full reader preserves newlines nested formatting and literal fallback content`() =
        runDesktopComposeUiTest(width = 480, height = 1000) {
            setContent {
                LogDateTheme {
                    MarkdownText(
                        "# Heading **bold**\n\nfirst line\nsecond *line* with `code`\n\n<div>raw **HTML**</div>\n\n![Café 🌏](https://example.com/photo.png)",
                    )
                }
            }

            onNodeWithText("Heading bold").assertExists()
            onNodeWithText("first line\nsecond line with code").assertExists()
            onNodeWithText("<div>raw **HTML**</div>").assertExists()
            onNodeWithText("Café 🌏").assertExists()
        }

    @Test
    fun `full reader renders populated quotes task lists code and tables`() =
        runDesktopComposeUiTest(width = 640, height = 1400) {
            setContent {
                LogDateTheme {
                    MarkdownText(
                        "> A *quiet* thought\n\n- [x] done\n- [ ] todo\n  - nested\n\n| Name | Value |\n| --- | --- |\n| A | **B** |\n\n~~~kotlin\n# code\n\n**literal**\n~~~\n\n---",
                    )
                }
            }

            onNodeWithText("A quiet thought").assertExists()
            onNodeWithText("done").assertExists()
            onNodeWithText("todo").assertExists()
            onNodeWithText("nested").assertExists()
            onNodeWithText("Name").assertExists()
            onNodeWithText("Value").assertExists()
            onNodeWithText("B").assertExists()
            onNodeWithText("# code\n\n**literal**").assertExists()
        }

    @Test
    fun `full reader preserves indentation inside fenced code`() =
        runDesktopComposeUiTest(width = 480, height = 800) {
            setContent {
                LogDateTheme { MarkdownText("~~~kotlin\n    keep\n      deeper\n~~~") }
            }

            onNodeWithText("    keep\n      deeper").assertExists()
        }
}
