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
    fun `with the app lock on, LogDate counts as locked until the person unlocks it`() {
        // A process started only for an assistant has never shown the unlock prompt.
        assertEquals(false, assistantUnlocked(appLockOn = true, authenticated = false))
        assertEquals(true, assistantUnlocked(appLockOn = true, authenticated = true))
    }

    @Test
    fun `without the app lock, LogDate is always unlocked`() {
        assertEquals(true, assistantUnlocked(appLockOn = false, authenticated = false))
    }

    @Test
    fun `excerpts are one line and shortened when long`() {
        assertEquals("Walked home. Quiet light.", assistantExcerpt("Walked home.\n  Quiet light.  "))
        val excerpt = assistantExcerpt("a".repeat(400))
        assertEquals(ASSISTANT_EXCERPT_LENGTH, excerpt.length)
        assertEquals('…', excerpt.last())
    }
}
