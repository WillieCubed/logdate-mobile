package app.logdate.client.data.restore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidMediaOwnerCensusTest {
    @Test
    fun `soft-deleted rows remain media owners until their rows are purged`() {
        val queries = mutableListOf<String>()
        val references =
            collectAndroidMediaOwnerReferences { sql ->
                queries += sql
                listOf("tombstoned:${sql.substringAfter("FROM ")}")
            }

        assertEquals(9, queries.size)
        assertTrue(queries.any { it == "SELECT contentUri FROM image_notes" })
        assertTrue(queries.any { it == "SELECT coverImageUri FROM journals" })
        assertTrue(queries.any { it == "SELECT cover_image_uri FROM events" })
        assertTrue(queries.any { it == "SELECT photo_uri FROM people" })
        assertTrue(queries.none { "WHERE" in it })
        assertEquals(9, references.size)
        assertFalse(references.isEmpty())
    }
}
