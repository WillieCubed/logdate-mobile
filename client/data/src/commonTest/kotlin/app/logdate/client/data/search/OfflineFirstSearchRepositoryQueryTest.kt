package app.logdate.client.data.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfflineFirstSearchRepositoryQueryTest {
    @Test
    fun prepareFtsQuery_usesPrefixMatchingForPlainTyping() {
        val prepared = prepareFtsQuery("Hiking trail")

        assertEquals("hiking* trail*", prepared?.query)
        assertEquals(listOf("hiking", "trail"), prepared?.tokens)
        assertFalse(prepared?.usesExplicitSyntax ?: true)
    }

    @Test
    fun prepareFtsQuery_preservesExplicitSyntax() {
        val prepared = prepareFtsQuery("\"golden hour\" OR sunset")

        assertEquals("\"golden hour\" OR sunset", prepared?.query)
        assertTrue(prepared?.usesExplicitSyntax ?: false)
    }

    @Test
    fun prepareFtsQuery_sanitizesBrokenExplicitSyntax() {
        val prepared = prepareFtsQuery("\"golden hour")

        assertEquals("golden hour", prepared?.query)
        assertTrue(prepared?.usesExplicitSyntax ?: false)
    }

    @Test
    fun prepareFtsQuery_returnsNullForBlankOrOperatorOnlyQueries() {
        assertNull(prepareFtsQuery("   "))
        assertNull(prepareFtsQuery("NOT"))
    }
}
