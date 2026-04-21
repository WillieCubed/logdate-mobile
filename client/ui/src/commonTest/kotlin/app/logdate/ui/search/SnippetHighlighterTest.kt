package app.logdate.ui.search

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Validates the [parseSnippetMarkers] logic for converting plain-text search results
 * into formatted UI snippets.
 *
 * These tests ensure that bracketed markers (e.g., `[match]`) are correctly identified,
 * removed from the visible text, and converted into bold span styles within a Compose
 * `AnnotatedString`.
 */
class SnippetHighlighterTest {
    @Test
    fun `plain text without markers returns unformatted string`() {
        val result = parseSnippetMarkers("No matches here")
        assertEquals("No matches here", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `empty string returns empty annotated string`() {
        val result = parseSnippetMarkers("")
        assertEquals("", result.text)
    }

    @Test
    fun `single marker pair produces bold span`() {
        val result = parseSnippetMarkers("Found [match] in text")
        assertEquals("Found match in text", result.text)
        assertEquals(1, result.spanStyles.size)

        val span = result.spanStyles[0]
        assertEquals(6, span.start) // "Found " = 6 chars
        assertEquals(11, span.end) // "match" = 5 chars
        assertEquals(FontWeight.Bold, span.item.fontWeight)
    }

    @Test
    fun `multiple markers produce multiple bold spans`() {
        val result = parseSnippetMarkers("[first] and [second] match")
        assertEquals("first and second match", result.text)
        assertEquals(2, result.spanStyles.size)

        assertEquals(0, result.spanStyles[0].start)
        assertEquals(5, result.spanStyles[0].end)
        assertEquals(10, result.spanStyles[1].start)
        assertEquals(16, result.spanStyles[1].end)
    }

    @Test
    fun `adjacent markers handled correctly`() {
        val result = parseSnippetMarkers("[one][two]")
        assertEquals("onetwo", result.text)
        assertEquals(2, result.spanStyles.size)
    }

    @Test
    fun `orphaned open bracket treated as literal`() {
        val result = parseSnippetMarkers("text with [ orphan")
        assertEquals("text with [ orphan", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `marker at start of string`() {
        val result = parseSnippetMarkers("[match] at start")
        assertEquals("match at start", result.text)
        assertEquals(1, result.spanStyles.size)
        assertEquals(0, result.spanStyles[0].start)
    }

    @Test
    fun `marker at end of string`() {
        val result = parseSnippetMarkers("at end [match]")
        assertEquals("at end match", result.text)
        assertEquals(1, result.spanStyles.size)
        assertEquals(7, result.spanStyles[0].start)
    }

    @Test
    fun `empty marker brackets produce empty bold span`() {
        val result = parseSnippetMarkers("text [] here")
        assertEquals("text  here", result.text)
        assertEquals(1, result.spanStyles.size)
        // Empty span — start equals end
        assertEquals(result.spanStyles[0].start, result.spanStyles[0].end)
    }

    @Test
    fun `custom matchStyle is applied to matched terms`() {
        val highlight =
            SpanStyle(
                background = Color(0xFFE8DEF8),
                color = Color(0xFF1D1B20),
                fontWeight = FontWeight.SemiBold,
            )
        val result = parseSnippetMarkers("Found [match] in text", matchStyle = highlight)

        assertEquals("Found match in text", result.text)
        assertEquals(1, result.spanStyles.size)
        assertEquals(highlight, result.spanStyles[0].item)
    }
}
