package app.logdate.space

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpaceRefTest {
    @Test
    fun `a personal space has the documented URI`() {
        val space = SpaceRef.personal("did:plc:abc123")
        assertEquals("at://did:plc:abc123/space/app.logdate.space.personal/self", space.uri)
        assertEquals(space, SpaceRef.parse(space.uri))
    }

    @Test
    fun `malformed space URIs are refused`() {
        assertNull(SpaceRef.parse("at://did:plc:abc123/app.logdate.space.personal/self"))
        assertNull(SpaceRef.parse("https://example.com/space/app.logdate.space.personal/self"))
        assertNull(SpaceRef.parse("at://alice/space/app.logdate.space.personal/self"))
        assertNull(SpaceRef.parse("at://did:plc:abc123/space/notansid/self"))
    }
}
