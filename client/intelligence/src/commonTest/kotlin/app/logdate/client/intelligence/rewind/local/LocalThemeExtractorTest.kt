package app.logdate.client.intelligence.rewind.local

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalThemeExtractorTest {
    private val extractor = LocalThemeExtractor()

    @Test
    fun `returns empty list for empty input`() {
        assertEquals(emptyList(), extractor.extract(emptyList()))
    }

    @Test
    fun `returns empty list when entries are only stopwords`() {
        val themes = extractor.extract(listOf("the and of", "a to in", "is it"))
        assertEquals(emptyList(), themes)
    }

    @Test
    fun `extracts most frequent content words as themes`() {
        val entries =
            listOf(
                "Had a great dinner with friends tonight.",
                "Another dinner with friends, this time at home.",
                "Spent all day planning the next dinner gathering.",
            )
        val themes = extractor.extract(entries)
        assertTrue(themes.contains("dinner"), "expected 'dinner' in themes: $themes")
        assertTrue(themes.contains("friends"), "expected 'friends' in themes: $themes")
    }

    @Test
    fun `orders themes by frequency descending`() {
        val entries =
            listOf(
                "travel travel travel friends home",
                "travel friends home",
            )
        val themes = extractor.extract(entries)
        assertEquals("travel", themes.first())
    }

    @Test
    fun `is case-insensitive when counting frequency`() {
        val themes = extractor.extract(listOf("Travel TRAVEL travel"))
        assertEquals(listOf("travel"), themes)
    }

    @Test
    fun `ignores words shorter than three characters`() {
        val themes = extractor.extract(listOf("go go go is is is travel"))
        // 'go' and 'is' are too short / stopwords; only 'travel' should survive.
        assertEquals(listOf("travel"), themes)
    }

    @Test
    fun `strips punctuation when tokenizing`() {
        val themes = extractor.extract(listOf("travel, travel. travel!"))
        assertEquals(listOf("travel"), themes)
    }

    @Test
    fun `caps results at maxThemes`() {
        val extractorCappedAtTwo = LocalThemeExtractor(maxThemes = 2)
        val entries =
            listOf(
                "travel friends dinner family birthday celebration",
                "travel friends dinner family birthday celebration",
            )
        val themes = extractorCappedAtTwo.extract(entries)
        assertEquals(2, themes.size)
    }
}
