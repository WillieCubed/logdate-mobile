package app.logdate.client.ambient

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class QuickReplyTest {
    private val now = Instant.parse("2026-10-09T21:15:00Z")

    @Test
    fun `a blank reply saves nothing`() {
        assertNull(quickReplyNote(null, now, "America/Chicago"))
        assertNull(quickReplyNote("   \n ", now, "America/Chicago"))
    }

    @Test
    fun `a reply becomes a text entry stamped now in the local zone`() {
        val note = quickReplyNote("  Long walk by the river.\nQuiet light.  ", now, "America/Chicago")!!

        assertEquals("Long walk by the river.\nQuiet light.", note.content)
        assertEquals(now, note.creationTimestamp)
        assertEquals(now, note.lastUpdated)
        assertEquals("America/Chicago", note.timeZoneId)
    }

    @Test
    fun `the confirmation shows the reply on one line and shortens it when long`() {
        assertEquals("Long walk. Quiet light.", quickReplyPreview("Long walk.\n Quiet light."))

        val long = "a".repeat(200)
        val preview = quickReplyPreview(long)
        assertEquals(120, preview.length)
        assertEquals('…', preview.last())
    }
}
