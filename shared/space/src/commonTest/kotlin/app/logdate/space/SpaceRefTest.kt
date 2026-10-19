package app.logdate.space

import app.logdate.lexicon.app.logdate.crypto.Sealed
import app.logdate.lexicon.app.logdate.space.Personal
import studio.hypertext.atproto.lexicon.LexiconRecordKey
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
    fun `a personal space follows the personal space declaration`() {
        val space = SpaceRef.personal("did:plc:abc123")
        assertEquals(Personal.TYPE, space.type)
        assertEquals(LexiconRecordKey.Literal(space.skey), Personal.KEY)
        assertEquals(listOf(Sealed.TYPE), Personal.COLLECTIONS)
    }

    @Test
    fun `malformed space URIs are refused`() {
        assertNull(SpaceRef.parse("at://did:plc:abc123/app.logdate.space.personal/self"))
        assertNull(SpaceRef.parse("https://example.com/space/app.logdate.space.personal/self"))
        assertNull(SpaceRef.parse("at://alice/space/app.logdate.space.personal/self"))
        assertNull(SpaceRef.parse("at://did:plc:abc123/space/notansid/self"))
    }
}
