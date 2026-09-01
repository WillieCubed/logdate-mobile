package app.logdate.client.data.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the search query preparation logic in [OfflineFirstSearchRepository].
 *
 * These tests verify how raw search queries are transformed into FTS-compatible
 * queries, ensuring correct handling of prefix matching, explicit syntax,
 * and single-character queries.
 */
class OfflineFirstSearchRepositoryQueryTest {
    @Test
    fun `prepare fts query uses prefix matching for plain typing`() {
        val prepared = assertNotNull(prepareFtsQuery("Hiking trail"))

        assertEquals("hiking* trail*", prepared.query)
        assertEquals(listOf("hiking", "trail"), prepared.tokens)
        assertFalse(prepared.usesExplicitSyntax)
        assertFalse(prepared.isSingleCharacterPlainQuery)
    }

    @Test
    fun `prepare fts query marks single character plain queries`() {
        val prepared = assertNotNull(prepareFtsQuery("a"))

        assertEquals("a*", prepared.query)
        assertEquals(listOf("a"), prepared.tokens)
        assertTrue(prepared.isSingleCharacterPlainQuery)
    }

    @Test
    fun `prepare fts query preserves explicit syntax`() {
        val prepared = assertNotNull(prepareFtsQuery("\"golden hour\" OR sunset"))

        assertEquals("\"golden hour\" OR sunset", prepared.query)
        assertTrue(prepared.usesExplicitSyntax)
    }

    @Test
    fun `prepare fts query sanitizes broken explicit syntax`() {
        val prepared = assertNotNull(prepareFtsQuery("\"golden hour"))

        assertEquals("golden hour", prepared.query)
        assertTrue(prepared.usesExplicitSyntax)
    }

    @Test
    fun `prepare fts query returns null for blank or operator only queries`() {
        assertNull(prepareFtsQuery("   "))
        assertNull(prepareFtsQuery("NOT"))
    }
}
