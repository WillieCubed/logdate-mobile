package app.logdate.space

import kotlin.test.Test
import kotlin.test.assertEquals

class Base32Test {
    @Test
    fun `encodes the RFC 4648 vectors in lowercase without padding`() {
        assertEquals("", Base32.encode(ByteArray(0)))
        assertEquals("my", Base32.encode("f".encodeToByteArray()))
        assertEquals("mzxq", Base32.encode("fo".encodeToByteArray()))
        assertEquals("mzxw6", Base32.encode("foo".encodeToByteArray()))
        assertEquals("mzxw6yq", Base32.encode("foob".encodeToByteArray()))
        assertEquals("mzxw6ytb", Base32.encode("fooba".encodeToByteArray()))
        assertEquals("mzxw6ytboi", Base32.encode("foobar".encodeToByteArray()))
    }
}
