package app.logdate.client.sync.space.mappers

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.space.SpaceRecordMappingException
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class NoteEntryTextMapperTest : NoteEntryMapperFixture() {
    @Test
    fun `text marked as markdown reads as written`() {
        val note = fullNotes[0]
        val record = mapper.toRecord(note).withBlock { it + ("format" to JsonPrimitive("markdown")) }

        assertEquals(note, mapper.fromRecord(mapper.rkeyOf(note), record))
    }

    @Test
    fun `plain text reads as markdown that shows the same characters`() {
        val texts =
            mapOf(
                "*not bold*" to "\\*not bold\\*",
                "# not a heading" to "\\# not a heading",
                "1. not a list" to "1\\. not a list",
                "2) not a list" to "2\\) not a list",
                "- not a bullet" to "\\- not a bullet",
                "> not a quote" to "\\> not a quote",
                "---" to "\\---",
                "snake_case and `code` and [link](x) and <b>" to "snake\\_case and \\`code\\` and \\[link](x) and \\<b>",
                "Tom & Jerry &amp; co" to "Tom & Jerry \\&amp; co",
                "back\\slash" to "back\\\\slash",
                "a | b ~~c~~" to "a \\| b \\~\\~c\\~\\~",
                "Wow! 100% 3.5 - fine" to "Wow! 100% 3.5 - fine",
            )

        texts.forEach { (plain, markdown) ->
            val note = fullNotes[0] as JournalNote.Text
            val record = mapper.toRecord(note).withBlock { it + ("text" to JsonPrimitive(plain)) + ("format" to JsonPrimitive("plain")) }

            val read = mapper.fromRecord(mapper.rkeyOf(note), record) as JournalNote.Text

            assertEquals(markdown, read.content, plain)
            assertEquals(markdown, writtenText(read), "plain text round-trips as equivalent markdown")
        }
    }

    @Test
    fun `plain text keeps its line breaks and blank lines`() {
        assertEquals("one\\\ntwo\n\nthree\\\n\\# four", plainTextAsMarkdown("one\ntwo\n\nthree\r\n# four"))
        assertEquals("a\n\n\nb", plainTextAsMarkdown("a\n  \n\nb"))
        assertEquals("", plainTextAsMarkdown(""))
    }

    @Test
    fun `escaped plain text reads back as the characters it was written with`() {
        val samples =
            listOf(
                "*not bold* _nor this_ ~~nor~~ `nor` [nor](this) <nor> a|b \\ end\\",
                "# h\n## h\n> q\n- b\n+ b\n1. l\n10) l\n=== \n--\n&copy; &#35; &x",
                "plain 日本語 👨‍👩‍👧 text, with: punctuation! (and) {braces} #hash 2.5 a-b",
            )

        samples.forEach { plain -> assertEquals(plain, readMarkdownEscapes(plainTextAsMarkdown(plain)!!), plain) }
    }

    @Test
    fun `plain text with an indented line is refused because markdown drops the indentation`() {
        val note = fullNotes[0]
        val record =
            mapper.toRecord(note).withBlock {
                it + ("text" to JsonPrimitive("poem\n  indented")) +
                    ("format" to JsonPrimitive("plain"))
            }

        val failure = assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(mapper.rkeyOf(note), record) }

        assertFalse("indented" in failure.message.orEmpty())
        assertNull(plainTextAsMarkdown("\tcode"))
    }
}
