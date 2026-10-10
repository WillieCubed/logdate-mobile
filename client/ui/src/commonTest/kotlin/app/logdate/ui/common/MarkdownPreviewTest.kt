package app.logdate.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownPreviewTest {
    private val styles =
        MarkdownPreviewStyles(
            h1 = TextStyle(color = Color.Red, lineHeight = 36.sp),
            h2 = TextStyle(color = Color.Green, lineHeight = 32.sp),
            h3 = TextStyle(color = Color.Blue, lineHeight = 28.sp),
            h4 = TextStyle(color = Color.Cyan, lineHeight = 24.sp),
            h5 = TextStyle(color = Color.Magenta, lineHeight = 20.sp),
            h6 = TextStyle(color = Color.Yellow, lineHeight = 20.sp),
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
            listOf("Heading bold", "\nParagraph with em and link.\n\n• first\n• second"),
            preview.paragraphTexts(),
        )
        assertFalse("https://logdate.app" in preview.text)
        assertTrue(preview.hasStyle(styles.h1.toSpanStyle(), "Heading"))
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
            "A quiet thought\n\nold and new\n\nval answer = 42",
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

        assertEquals("first line\nsecond line", preview.text)
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

    @Test
    fun `full reading keeps link actions while compact previews retain card interaction`() {
        val document = parseMarkdownDocument("Read [map](https://logdate.app/maps).")

        val reading = buildMarkdownPreview(document, styles, clickableLinks = true)
        val preview = buildMarkdownPreview(document, styles)

        assertEquals("Read map.", reading.text)
        val link = reading.getLinkAnnotations(0, reading.length).single()
        assertEquals("https://logdate.app/maps", (link.item as LinkAnnotation.Url).url)
        assertEquals("map", reading.text.substring(link.start, link.end))
        assertTrue(preview.getLinkAnnotations(0, preview.length).isEmpty())
    }

    @Test
    fun `unsupported link schemes stay readable without click actions`() {
        val document = parseMarkdownDocument("[label](javascript:alert)")

        val reading = buildMarkdownPreview(document, styles, clickableLinks = true)

        assertEquals("label", reading.text)
        assertTrue(reading.getLinkAnnotations(0, reading.length).isEmpty())
    }

    @Test
    fun `headings lay out as their own paragraphs at their heading line height`() {
        val preview =
            buildMarkdownPreview(
                "Intro\n\n# Title\n## Subtitle\n\nBody\n\n### Section\n\n#### Detail",
                styles,
            )

        assertEquals(
            listOf("Intro\n", "Title", "Subtitle", "\nBody\n", "Section", "", "Detail"),
            preview.paragraphTexts(),
        )
        assertEquals(
            listOf(
                ParagraphStyle(textDirection = TextDirection.Content),
                ParagraphStyle(textDirection = TextDirection.Content, lineHeight = 36.sp),
                ParagraphStyle(textDirection = TextDirection.Content, lineHeight = 32.sp),
                ParagraphStyle(textDirection = TextDirection.Content),
                ParagraphStyle(textDirection = TextDirection.Content, lineHeight = 28.sp),
                ParagraphStyle(textDirection = TextDirection.Content),
                ParagraphStyle(textDirection = TextDirection.Content, lineHeight = 24.sp),
            ),
            preview.paragraphStyles.map { it.item },
        )
        assertTrue(preview.hasStyle(styles.h2.toSpanStyle(), "Subtitle"))
    }

    @Test
    fun `preview without headings stays one paragraph`() {
        val preview = buildMarkdownPreview("first\n\n- second\n- third", styles)

        assertEquals(listOf("first\n\n• second\n• third"), preview.paragraphTexts())
    }

    @Test
    fun `compact preview drops blank lines between blocks and keeps heading paragraphs`() {
        val preview =
            buildMarkdownPreview(
                parseMarkdownDocument("# Title\n\nBody **bold**.\n\n- first\n- second\n\n> quote"),
                styles,
                compact = true,
            )

        assertEquals("TitleBody bold.\n• first\n• second\nquote", preview.text)
        assertEquals(listOf("Title", "Body bold.\n• first\n• second\nquote"), preview.paragraphTexts())
        assertEquals(
            36.sp,
            preview.paragraphStyles
                .first()
                .item.lineHeight,
        )
        assertTrue(preview.hasStyle(styles.strong, "bold"))
        assertTrue(preview.hasStyle(styles.quote, "quote"))
    }

    @Test
    fun `compact preview without blank lines matches the regular preview`() {
        val document = parseMarkdownDocument("first line\nsecond line")

        assertEquals(buildMarkdownPreview(document, styles), buildMarkdownPreview(document, styles, compact = true))
    }

    private fun AnnotatedString.paragraphTexts(): List<String> = paragraphStyles.map { text.substring(it.start, it.end) }

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
