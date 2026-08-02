package app.logdate.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownPreviewTest {
    private val styles =
        MarkdownPreviewStyles(
            h1 = SpanStyle(color = Color.Red),
            h2 = SpanStyle(color = Color.Green),
            h3 = SpanStyle(color = Color.Blue),
            h4 = SpanStyle(color = Color.Cyan),
            h5 = SpanStyle(color = Color.Magenta),
            h6 = SpanStyle(color = Color.Yellow),
            strong = SpanStyle(fontWeight = FontWeight.Bold),
            emphasis = SpanStyle(fontStyle = FontStyle.Italic),
            strikethrough = SpanStyle(textDecoration = TextDecoration.LineThrough),
            code = SpanStyle(background = Color.LightGray),
            link = SpanStyle(textDecoration = TextDecoration.Underline),
            quote = SpanStyle(fontStyle = FontStyle.Italic),
        )

    @Test
    fun `preview renders readable markdown without source delimiters or link destinations`() {
        val preview =
            buildMarkdownPreview(
                "# Heading **bold**\n\nParagraph with *em* and [link](https://logdate.app).\n\n- first\n- second",
                styles,
            )

        assertEquals(
            "Heading bold\nParagraph with em and link.\n• first\n• second",
            preview.text,
        )
        assertFalse("https://logdate.app" in preview.text)
        assertTrue(preview.hasStyle(styles.h1, "Heading"))
        assertTrue(preview.hasStyle(styles.strong, "bold"))
        assertTrue(preview.hasStyle(styles.emphasis, "em"))
        assertTrue(preview.hasStyle(styles.link, "link"))
    }

    @Test
    fun `preview preserves code quote and strike content without rendering their markers`() {
        val preview =
            buildMarkdownPreview(
                "> A *quiet* thought\n\n~~old~~ and `new`\n\n```kotlin\nval answer = 42\n```",
                styles,
            )

        assertEquals(
            "A quiet thought\nold and new\nval answer = 42",
            preview.text,
        )
        assertTrue(preview.hasStyle(styles.quote, "A quiet thought"))
        assertTrue(preview.hasStyle(styles.strikethrough, "old"))
        assertTrue(preview.hasStyle(styles.code, "new"))
        assertTrue(preview.hasStyle(styles.code, "val answer = 42"))
    }

    @Test
    fun `soft line break keeps adjacent paragraph lines separated`() {
        val preview = buildMarkdownPreview("first line\nsecond line", styles)

        assertEquals("first line second line", preview.text)
        assertFalse("linesecond" in preview.text)
    }

    @Test
    fun `overflow semantics expose only visible preview text plus ellipsis`() {
        val preview = AnnotatedString("Visible words followed by hidden words")

        val semantics =
            previewSemanticsText(
                content = preview,
                visibleEnd = "Visible words".length,
                hasVisualOverflow = true,
            )

        assertEquals("Visible words…", semantics.text)
        assertFalse("hidden" in semantics.text)
    }

    @Test
    fun `semantics preserve the full preview when text does not overflow`() {
        val preview = AnnotatedString("All visible")

        assertEquals(
            preview,
            previewSemanticsText(
                content = preview,
                visibleEnd = preview.length,
                hasVisualOverflow = false,
            ),
        )
    }

    private fun AnnotatedString.hasStyle(
        expected: SpanStyle,
        substring: String,
    ): Boolean {
        val start = text.indexOf(substring)
        val end = start + substring.length
        return spanStyles.any { range ->
            range.item == expected && range.start <= start && range.end >= end
        }
    }
}
