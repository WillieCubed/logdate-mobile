package app.logdate.client.intelligence.rewind.local

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StopwordListTest {
    @Test
    fun `contains common English stopwords`() {
        listOf("the", "and", "of", "to", "a", "in", "is", "it", "for", "with").forEach {
            assertTrue(StopwordList.contains(it), "expected '$it' to be a stopword")
        }
    }

    @Test
    fun `does not contain content words`() {
        listOf("dog", "travel", "happy", "family", "celebrate").forEach {
            assertFalse(StopwordList.contains(it), "expected '$it' to be a content word")
        }
    }

    @Test
    fun `is case-insensitive`() {
        assertTrue(StopwordList.contains("THE"))
        assertTrue(StopwordList.contains("The"))
        assertTrue(StopwordList.contains("tHe"))
    }

    @Test
    fun `trims whitespace before checking`() {
        assertTrue(StopwordList.contains("  the  "))
        assertTrue(StopwordList.contains("\tand\n"))
    }
}
