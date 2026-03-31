package app.logdate.client.data.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfflineFirstSearchRepositoryQueryTest {
    @Test
    fun prepareFtsQuery_usesPrefixMatchingForPlainTyping() {
        val prepared = assertNotNull(prepareFtsQuery("Hiking trail"))

        assertEquals("hiking* trail*", prepared.query)
        assertEquals(listOf("hiking", "trail"), prepared.tokens)
        assertFalse(prepared.usesExplicitSyntax)
        assertFalse(prepared.isSingleCharacterPlainQuery)
    }

    @Test
    fun prepareFtsQuery_marksSingleCharacterPlainQueries() {
        val prepared = assertNotNull(prepareFtsQuery("a"))

        assertEquals("a*", prepared.query)
        assertEquals(listOf("a"), prepared.tokens)
        assertTrue(prepared.isSingleCharacterPlainQuery)
    }

    @Test
    fun prepareFtsQuery_preservesExplicitSyntax() {
        val prepared = assertNotNull(prepareFtsQuery("\"golden hour\" OR sunset"))

        assertEquals("\"golden hour\" OR sunset", prepared.query)
        assertTrue(prepared.usesExplicitSyntax)
    }

    @Test
    fun prepareFtsQuery_sanitizesBrokenExplicitSyntax() {
        val prepared = assertNotNull(prepareFtsQuery("\"golden hour"))

        assertEquals("golden hour", prepared.query)
        assertTrue(prepared.usesExplicitSyntax)
    }

    @Test
    fun prepareFtsQuery_returnsNullForBlankOrOperatorOnlyQueries() {
        assertNull(prepareFtsQuery("   "))
        assertNull(prepareFtsQuery("NOT"))
    }
}
