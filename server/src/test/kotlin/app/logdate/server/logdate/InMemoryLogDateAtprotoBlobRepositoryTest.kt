package app.logdate.server.logdate

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InMemoryLogDateAtprotoBlobRepositoryTest {
    @Test
    fun `atproto blob repository stores and fetches user scoped blobs`() {
        val repository = InMemoryLogDateAtprotoBlobRepository()
        val userId = UUID.randomUUID()
        val stored =
            repository.upsertBlob(
                userId = userId,
                blob =
                    LogDateAtprotoBlob(
                        cid = "bafk-blob",
                        userId = userId,
                        mimeType = "image/jpeg",
                        sizeBytes = 3L,
                        storagePath = "users/$userId/atproto/blobs/bafk-blob",
                        createdAt = 10L,
                    ),
            )

        val fetched = repository.getBlob(userId, stored.cid)
        val otherUserLookup = repository.getBlob(UUID.randomUUID(), stored.cid)

        assertNotNull(fetched)
        assertEquals("image/jpeg", fetched.mimeType)
        assertEquals(3L, fetched.sizeBytes)
        assertNull(otherUserLookup)
    }
}
