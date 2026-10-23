package app.logdate.client.device.identity

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OwnerUuidCasingTest {
    @Test
    fun `bound owner recognizes the same UUID without rewriting retained spelling`() =
        runTest {
            val raw = "A8A3400A-9F3C-4FCA-9A7A-7C8CBE5CA24E"
            val storage = CanonicalOwnerTestStorage()
            storage.putString("identity.canonical_owner_id", raw)
            storage.putString("identity.canonical_owner_bound", "true")
            val owner = DefaultCanonicalOwnerProvider(readOnlyCanonicalOwnerStorage(storage))
            assertTrue(owner.adoptRemoteOwnerIfUninitialized(raw.lowercase()))
            assertEquals(raw, owner.readExistingOwner()?.rawId)
            assertTrue(owner.hasBoundOwner())
            assertFalse(owner.adoptRemoteOwnerIfUninitialized("b930bf49-d1e9-4a65-b84b-8e74af45012b"))
        }
}
