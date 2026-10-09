package app.logdate.ui.common

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.LayoutDirection
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MarkdownDirectionTest {
    @Test
    fun `full reader keeps inline formatting on one line and preserves intentional soft breaks`() =
        runDesktopComposeUiTest(width = 640, height = 800) {
            val visible = "The long way home — rain.\nSoft line\ncontinues."
            setContent {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    LogDateTheme(dynamicColor = false) {
                        MarkdownText("The **long *way*** home — ~~rain~~.\nSoft line\ncontinues.")
                    }
                }
            }
            val layout = onNodeWithText(visible).textLayout()
            assertEquals(3, layout.lineCount, "Inline styling must not introduce paragraph breaks")
            assertEquals(0, layout.getLineForOffset(visible.indexOf('.')))
            assertEquals(1, layout.getLineForOffset(visible.indexOf("Soft")))
            assertEquals(2, layout.getLineForOffset(visible.indexOf("continues")))
        }

    @Test
    fun `full reader follows English and Arabic content direction in an RTL shell`() =
        runDesktopComposeUiTest(width = 480, height = 800) {
            setContent {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    LogDateTheme(dynamicColor = false) {
                        MarkdownText("# Hello world.\n\nمرحبا بالعالم.\n\n~~~text\n  keep indentation\n~~~\n\n<div>literal HTML</div>")
                    }
                }
            }
            val english = onNodeWithText("Hello world.").textLayout()
            assertEquals(ResolvedTextDirection.Ltr, english.getParagraphDirection(0))
            assertTrue(english.getBoundingBox(0).left < english.getBoundingBox(11).left, "English punctuation follows its words")
            val arabic = onNodeWithText("مرحبا بالعالم.").textLayout()
            assertEquals(ResolvedTextDirection.Rtl, arabic.getParagraphDirection(0))
            assertTrue(
                arabic.getBoundingBox(0).left > arabic.getBoundingBox(arabic.layoutInput.text.lastIndex).left,
                "Arabic punctuation follows its words",
            )
            assertEquals(ResolvedTextDirection.Ltr, onNodeWithText("  keep indentation").textLayout().getParagraphDirection(0))
            val html = onNodeWithText("<div>literal HTML</div>").textLayout()
            assertTrue(
                html.getBoundingBox(0).left < html.getBoundingBox(html.layoutInput.text.lastIndex).left,
                "Literal markup keeps source order",
            )
        }

    @Test
    fun `preview model preserves newlines and Arabic runs within English and mixed content`() =
        runDesktopComposeUiTest(width = 480, height = 800) {
            val span = SpanStyle()
            val styles = MarkdownPreviewStyles(span, span, span, span, span, span, span, span, span, span, span, span)
            val preview = buildMarkdownPreview("Hello **world**.\nمرحبا بالعالم.\nHello مرحبا.", styles)
            setContent {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    LogDateTheme(dynamicColor = false) { Text(preview) }
                }
            }
            val layout = onNodeWithText(preview.text).textLayout()
            assertEquals(ResolvedTextDirection.Ltr, layout.getParagraphDirection(0))
            assertEquals(ResolvedTextDirection.Rtl, layout.getBidiRunDirection(preview.text.indexOf('م')))
            assertEquals(ResolvedTextDirection.Ltr, layout.getParagraphDirection(preview.text.lastIndexOf("Hello")))
            assertEquals(ResolvedTextDirection.Rtl, layout.getBidiRunDirection(preview.text.lastIndexOf('م')))
            assertEquals(3, layout.lineCount, "Direction styling must not add blank lines")
            assertTrue(layout.getBoundingBox(0).left < layout.getBoundingBox(11).left)
        }

    @Test
    fun `Arabic preview keeps its paragraph and punctuation right to left`() =
        runDesktopComposeUiTest(width = 480, height = 800) {
            val span = SpanStyle()
            val styles = MarkdownPreviewStyles(span, span, span, span, span, span, span, span, span, span, span, span)
            val preview = buildMarkdownPreview("مرحبا **بالعالم**.", styles)
            setContent {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    LogDateTheme(dynamicColor = false) { Text(preview) }
                }
            }
            val layout = onNodeWithText(preview.text).textLayout()
            assertEquals(ResolvedTextDirection.Rtl, layout.getParagraphDirection(0))
            assertTrue(layout.getBoundingBox(0).left > layout.getBoundingBox(preview.lastIndex).left)
        }
}

private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
    val layouts = mutableListOf<TextLayoutResult>()
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    return layouts.single()
}
