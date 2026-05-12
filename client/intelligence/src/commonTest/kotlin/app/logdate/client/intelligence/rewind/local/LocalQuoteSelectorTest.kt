package app.logdate.client.intelligence.rewind.local

import app.logdate.client.repository.journals.JournalNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class LocalQuoteSelectorTest {
    private val selector = LocalQuoteSelector()

    private fun textEntry(content: String): JournalNote.Text =
        JournalNote.Text(
            creationTimestamp = Instant.fromEpochMilliseconds(0),
            lastUpdated = Instant.fromEpochMilliseconds(0),
            content = content,
        )

    @Test
    fun `returns empty list for empty entries`() {
        assertEquals(emptyList(), selector.select(emptyList()))
    }

    @Test
    fun `picks sentences with first-person + emotion words`() {
        val entries =
            listOf(
                textEntry(
                    "Today was rough. " +
                        "I felt completely overwhelmed by the deadline pressing in on me. " +
                        "Tomorrow will be better.",
                ),
            )
        val quotes = selector.select(entries)
        assertTrue(quotes.isNotEmpty(), "expected at least one quote")
        assertTrue(
            quotes.any { it.text.contains("overwhelmed") },
            "expected the overwhelmed sentence to be picked: ${quotes.map { it.text }}",
        )
    }

    @Test
    fun `skips sentences outside the length window`() {
        // The short and the very long sentence should both be skipped — only the
        // mid-length one is eligible.
        val entries =
            listOf(
                textEntry(
                    "Bad. " +
                        "I felt completely overwhelmed by the looming deadline that just would not give. " +
                        ("very ".repeat(80) + "long sentence."),
                ),
            )
        val quotes = selector.select(entries)
        assertEquals(1, quotes.size)
        assertTrue(quotes.first().text.contains("overwhelmed"))
    }

    @Test
    fun `caps at three quotes`() {
        val entries =
            (1..10).map { i ->
                textEntry("I felt wonderful about everything that happened that day number $i ok yes.")
            }
        val quotes = selector.select(entries)
        assertTrue(quotes.size <= 3, "expected at most 3 quotes, got ${quotes.size}")
    }

    @Test
    fun `tags every quote with its source entry id`() {
        val entry =
            textEntry("I felt completely overwhelmed by the deadline pressing in on me this week.")
        val quotes = selector.select(listOf(entry))
        assertTrue(quotes.isNotEmpty())
        quotes.forEach { assertEquals(entry.uid.toString(), it.sourceEntryId) }
    }
}
