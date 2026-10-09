package app.logdate.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownDocumentTest {
    @Test
    fun `nested formatting preserves source and Unicode content ranges`() {
        val source = "# 旅行 👩🏽‍🚀\n\nA **bold *thought*** and ~~rain~~."

        val document = parseMarkdownDocument(source)

        assertEquals(source, document.source)
        assertEquals("旅行 👩🏽‍🚀\n\nA bold thought and rain.", document.plainText)
        assertEquals(listOf(MarkdownBlockKind.HEADING, MarkdownBlockKind.PARAGRAPH), document.blocks.map { it.kind })
        assertSpan(document, MarkdownStyleKind.STRONG, "bold *thought*", "bold thought")
        assertSpan(document, MarkdownStyleKind.EMPHASIS, "thought", "thought")
        assertSpan(document, MarkdownStyleKind.STRIKETHROUGH, "rain", "rain")
    }

    @Test
    fun `paragraph and blank newlines remain intentional`() {
        val document = parseMarkdownDocument("first line\nsecond line\n\n\nlast line")

        assertEquals("first line\nsecond line\n\n\nlast line", document.plainText)
    }

    @Test
    fun `task nested list quote table and divider have readable structure`() {
        val source = "> A *quiet* thought\n\n- [x] done\n- [ ] todo\n  - nested\n\n| Name | Value |\n| --- | --- |\n| A | **B** |\n\n---"

        val document = parseMarkdownDocument(source)

        assertEquals("A quiet thought\n\n☑ done\n☐ todo\n  • nested\n\nName\tValue\nA\tB\n\n—", document.plainText)
        assertEquals(
            listOf(MarkdownBlockKind.BLOCK_QUOTE, MarkdownBlockKind.UNORDERED_LIST, MarkdownBlockKind.TABLE, MarkdownBlockKind.DIVIDER),
            document.blocks.map { it.kind },
        )
        assertSpan(document, MarkdownStyleKind.STRONG, "B", "B")
    }

    @Test
    fun `code protects formatting and retains blank code lines`() {
        val source = "`**literal**`\n\n~~~kotlin\n# code\n\n**literal**\n~~~"

        val document = parseMarkdownDocument(source)

        assertEquals("**literal**\n\n# code\n\n**literal**", document.plainText)
        assertFalse(document.spans.any { it.kind == MarkdownStyleKind.STRONG || it.kind.name.startsWith("HEADING") })
        assertSpan(document, MarkdownStyleKind.CODE_BLOCK, "# code\n\n**literal**", "# code\n\n**literal**")
    }

    @Test
    fun `fenced code preserves deliberate leading and trailing blank lines`() {
        val document = parseMarkdownDocument("~~~\n\nline\n\n~~~")

        assertEquals("\nline\n", document.plainText)
        assertSpan(document, MarkdownStyleKind.CODE_BLOCK, "\nline\n", "\nline\n")
    }

    @Test
    fun `indented code removes only block indentation and preserves blank lines`() {
        val document = parseMarkdownDocument("    a\n\n      b")

        assertEquals("a\n\n  b", document.plainText)
    }

    @Test
    fun `escaped unfinished and unsupported HTML text remain readable`() {
        val source = "\\*literal\\* and *unfinished\n\n<div>raw **HTML**</div>\n\n![Café 🌏](https://example.com/photo.png)"

        val document = parseMarkdownDocument(source)

        assertEquals("*literal* and *unfinished\n\n<div>raw **HTML**</div>\n\nCafé 🌏", document.plainText)
        assertTrue(document.spans.isEmpty())
        assertEquals(source, document.source)
    }

    @Test
    fun `reference and autolinks share label spans and omit destinations`() {
        val document = parseMarkdownDocument("[map][place] and <https://example.com>\n\n[place]: https://logdate.app")

        assertEquals("map and https://example.com", document.plainText)
        assertSpan(document, MarkdownStyleKind.LINK, "map", "map")
        assertSpan(document, MarkdownStyleKind.LINK, "https://example.com", "https://example.com")
    }

    @Test
    fun `unresolved references retain their literal source`() {
        val document = parseMarkdownDocument("Read [missing][nowhere] and [short].")

        assertEquals("Read [missing][nowhere] and [short].", document.plainText)
        assertTrue(document.spans.isEmpty())
    }

    @Test
    fun `ordered reading follows the starting number while source markers remain intact`() {
        val document = parseMarkdownDocument("5. fifth\n9. sixth")

        assertEquals("5. fifth\n6. sixth", document.plainText)
        assertSpan(document, MarkdownStyleKind.LIST_MARKER, "9.", "6. ")
    }

    private fun assertSpan(
        document: MarkdownDocument,
        kind: MarkdownStyleKind,
        sourceContent: String,
        readableContent: String,
    ) {
        assertTrue(
            document.spans.any {
                it.kind == kind &&
                    document.source.substring(it.sourceStart, it.sourceEnd) == sourceContent &&
                    document.plainText.substring(it.textStart, it.textEnd) == readableContent
            },
            "Expected $kind for '$sourceContent' -> '$readableContent', got ${document.spans}",
        )
    }
}
