package app.logdate.atproto.identity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IdentityExceptionTest {
    @Test
    fun constructsExceptionMessagesAndCauses() {
        val cause = IllegalStateException("boom")
        val didException = DidResolutionException("did:plc:ewvi7nxzyoun6zhxrhs64oiz", "HTTP 500", cause)
        val handleException = HandleResolutionException("alice.test", "HTTP 404", cause)
        val mismatch = DidDocumentMismatchException("did:plc:one", "did:plc:two")

        assertEquals("Failed to resolve DID did:plc:ewvi7nxzyoun6zhxrhs64oiz: HTTP 500", didException.message)
        assertSame(cause, didException.cause)
        assertEquals("Failed to resolve handle alice.test: HTTP 404", handleException.message)
        assertSame(cause, handleException.cause)
        assertTrue(mismatch.message!!.contains("expected did:plc:one"))
    }
}
