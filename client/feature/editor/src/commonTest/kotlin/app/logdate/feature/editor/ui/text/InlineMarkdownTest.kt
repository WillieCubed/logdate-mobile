package app.logdate.feature.editor.ui.text

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InlineMarkdownTest {
    @Test
    fun headingsAreRecognizedOnlyAtTheStartOfLines() {
        val source = "# First\n## Second\n### Third\n#### Fourth\n##### Fifth\n###### Sixth\nNot # a heading"

        val spans = parseInlineMarkdown(source)

        assertSpan(spans, InlineMarkdownStyle.HEADING_1, source, "First")
        assertSpan(spans, InlineMarkdownStyle.HEADING_2, source, "Second")
        assertSpan(spans, InlineMarkdownStyle.HEADING_3, source, "Third")
        assertSpan(spans, InlineMarkdownStyle.HEADING_4, source, "Fourth")
        assertSpan(spans, InlineMarkdownStyle.HEADING_5, source, "Fifth")
        assertSpan(spans, InlineMarkdownStyle.HEADING_6, source, "Sixth")
        assertFalse(spans.any { source.substring(it.start, it.end) == "a heading" })
    }

    @Test
    fun inlineFormattingKeepsRangesAlignedWithTheRawMarkdown() {
        val source = "A **bright** day with *friends*, ~~rain~~, and `coffee`."

        val spans = parseInlineMarkdown(source)

        assertSpan(spans, InlineMarkdownStyle.STRONG, source, "bright")
        assertSpan(spans, InlineMarkdownStyle.EMPHASIS, source, "friends")
        assertSpan(spans, InlineMarkdownStyle.STRIKETHROUGH, source, "rain")
        assertSpan(spans, InlineMarkdownStyle.INLINE_CODE, source, "coffee")
        spans.forEach { span ->
            assertTrue(span.start >= 0)
            assertTrue(span.end <= source.length)
            assertTrue(span.start < span.end)
        }
    }

    @Test
    fun linksQuotesAndListMarkersAreRecognized() {
        val source = "> Remember this\n- Gallery\n1. Call Sam\nRead the [map](https://logdate.app)."

        val spans = parseInlineMarkdown(source)

        assertSpan(spans, InlineMarkdownStyle.BLOCK_QUOTE, source, "Remember this")
        assertSpan(spans, InlineMarkdownStyle.LIST_MARKER, source, "-")
        assertSpan(spans, InlineMarkdownStyle.LIST_MARKER, source, "1.")
        assertSpan(spans, InlineMarkdownStyle.LINK, source, "map")
    }

    @Test
    fun fencedAndInlineCodeProtectMarkdownCharactersInsideCode() {
        val source = "`**literal**`\n```kotlin\n# also literal\n```"

        val spans = parseInlineMarkdown(source)

        assertSpan(spans, InlineMarkdownStyle.INLINE_CODE, source, "**literal**")
        assertSpan(spans, InlineMarkdownStyle.CODE_BLOCK, source, "# also literal")
        assertFalse(spans.any { it.style == InlineMarkdownStyle.STRONG })
        assertFalse(spans.any { it.style.name.startsWith("HEADING") })
    }

    @Test
    fun inlineCodeDoesNotSuppressBlockFormattingOnTheSameLine() {
        val source = "# Heading with `code`\n> Quote with `code`\n- Pack the `rain shell`"

        val spans = parseInlineMarkdown(source)

        assertSpan(spans, InlineMarkdownStyle.HEADING_1, source, "Heading with `code`")
        assertSpan(spans, InlineMarkdownStyle.BLOCK_QUOTE, source, "Quote with `code`")
        assertSpan(spans, InlineMarkdownStyle.LIST_MARKER, source, "-")
        assertEquals(3, spans.count { it.style == InlineMarkdownStyle.INLINE_CODE })
    }

    @Test
    fun unmatchedAndEscapedMarkersStayPlain() {
        val source = "An *unfinished thought and \\*escaped\\* markers"

        val spans = parseInlineMarkdown(source)

        assertFalse(spans.any { it.style == InlineMarkdownStyle.EMPHASIS })
    }

    @Test
    fun unfinishedDelimiterDoesNotSuppressValidFormattingOnLaterLines() {
        val source = "*unfinished\nlater *valid* and `code`"

        val spans = parseInlineMarkdown(source)

        assertSpan(spans, InlineMarkdownStyle.EMPHASIS, source, "valid")
        assertSpan(spans, InlineMarkdownStyle.INLINE_CODE, source, "code")
    }

    @Test
    fun unfinishedInlineCodeDoesNotSuppressValidCodeOnLaterLines() {
        val source = "`unfinished\nlater `valid`"

        val spans = parseInlineMarkdown(source)

        assertSpan(spans, InlineMarkdownStyle.INLINE_CODE, source, "valid")
    }

    @Test
    fun unclosedCodeFenceProtectsTheRemainingDocument() {
        val source = "Before\n```kotlin\n# literal\n**also literal**"

        val spans = parseInlineMarkdown(source)

        assertSpan(
            spans,
            InlineMarkdownStyle.CODE_BLOCK,
            source,
            "# literal\n**also literal**",
        )
        assertFalse(spans.any { it.style == InlineMarkdownStyle.STRONG })
        assertFalse(spans.any { it.style.name.startsWith("HEADING") })
    }

    @Test
    fun underscoresInsideWordsAndIdentifiersStayPlain() {
        val source = "trip_to_paris, user_profile_name, and user__profile__name"

        val spans = parseInlineMarkdown(source)

        assertFalse(spans.any { it.style == InlineMarkdownStyle.EMPHASIS })
        assertFalse(spans.any { it.style == InlineMarkdownStyle.STRONG })
    }

    @Test
    fun tripleAsterisksApplyStrongAndEmphasisToTheSameContent() {
        val source = "A ***really bright*** morning"

        val spans = parseInlineMarkdown(source)

        assertSpan(spans, InlineMarkdownStyle.STRONG, source, "really bright")
        assertSpan(spans, InlineMarkdownStyle.EMPHASIS, source, "really bright")
    }

    @Test
    fun cjkRtlAndEmojiContentKeepsExactSourceRanges() {
        val source = "**旅行 🌏** و *ذكريات*"

        val spans = parseInlineMarkdown(source)

        assertSpan(spans, InlineMarkdownStyle.STRONG, source, "旅行 🌏")
        assertSpan(spans, InlineMarkdownStyle.EMPHASIS, source, "ذكريات")
    }

    @Test
    fun unicodeContentUsesIdentityOffsets() {
        val source = "# Café 👩🏽‍🚀"

        val heading = parseInlineMarkdown(source).single()

        assertEquals(InlineMarkdownStyle.HEADING_1, heading.style)
        assertEquals("Café 👩🏽‍🚀", source.substring(heading.start, heading.end))
    }

    @Test
    fun visualFormattingPreservesRawTextAndIdentityOffsets() {
        val source = "# Trip notes\nA **bright** day with `coffee`."
        val transformation =
            InlineMarkdownVisualTransformation {
                SpanStyle(fontWeight = FontWeight.Bold)
            }

        val transformed = transformation.filter(AnnotatedString(source))

        assertEquals(source, transformed.text.text)
        val expectedRanges = parseInlineMarkdown(source).map { it.start to it.end }
        val actualRanges = transformed.text.spanStyles.map { it.start to it.end }
        assertEquals(expectedRanges, actualRanges)
        (0..source.length).forEach { offset ->
            assertEquals(offset, transformed.offsetMapping.originalToTransformed(offset))
            assertEquals(offset, transformed.offsetMapping.transformedToOriginal(offset))
        }
    }

    private fun assertSpan(
        spans: List<InlineMarkdownSpan>,
        style: InlineMarkdownStyle,
        source: String,
        expectedContent: String,
    ) {
        val expectedStart = source.indexOf(expectedContent)
        assertTrue(expectedStart >= 0)
        assertTrue(
            spans.any { span ->
                span.style == style &&
                    span.start == expectedStart &&
                    span.end == expectedStart + expectedContent.length
            },
            "Expected $style span for '$expectedContent', but got $spans",
        )
    }
}
