package app.logdate.client.appfunctions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AssistantAccessTest {
    @Test
    fun `reading needs system search visibility first`() {
        assertEquals(AssistantReadRefusal.SystemSearchOff, assistantReadRefusal(systemSearchVisible = false, unlocked = true))
        assertEquals(AssistantReadRefusal.SystemSearchOff, assistantReadRefusal(systemSearchVisible = false, unlocked = false))
    }

    @Test
    fun `reading is refused while LogDate is locked`() {
        assertEquals(AssistantReadRefusal.Locked, assistantReadRefusal(systemSearchVisible = true, unlocked = false))
    }

    @Test
    fun `reading is allowed with consent and an unlocked app`() {
        assertNull(assistantReadRefusal(systemSearchVisible = true, unlocked = true))
    }

    @Test
    fun `excerpts are one line and shortened when long`() {
        assertEquals("Walked home. Quiet light.", assistantExcerpt("Walked home.\n  Quiet light.  "))
        val excerpt = assistantExcerpt("a".repeat(400))
        assertEquals(ASSISTANT_EXCERPT_LENGTH, excerpt.length)
        assertEquals('…', excerpt.last())
    }
}
