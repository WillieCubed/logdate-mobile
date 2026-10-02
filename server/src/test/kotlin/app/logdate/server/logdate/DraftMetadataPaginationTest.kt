package app.logdate.server.logdate

import kotlinx.coroutines.test.runTest
import studio.hypertext.atproto.identity.AtprotoDid
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DraftMetadataPaginationTest {
    @Test
    fun `mixed updates and deletion-only pages advance by server version without skipping`() =
        runTest {
            val store = InMemoryLogDateCollectionsMetadataStore()
            val user = UUID.randomUUID()
            val did = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
            val kind = LogDateCollectionKind.DRAFT
            store.upsert(user, did, kind, "first")
            store.upsert(user, did, kind, "second")
            store.upsert(user, did, kind, "third")
            store.delete(user, did, kind, "second", deletedAt = 100L)

            var cursor = 0L
            val seen = mutableListOf<String>()
            repeat(3) {
                val page = store.changes(user, kind, cursor, limit = 1)
                seen += page.changes.map { it.recordKey } + page.deletions.map { "deleted:${it.recordKey}" }
                assertTrue(page.lastTimestamp > cursor)
                cursor = page.lastTimestamp
                if (it < 2) assertTrue(page.hasMore) else assertFalse(page.hasMore)
            }
            assertEquals(listOf("first", "third", "deleted:second"), seen)
            assertEquals(emptyList(), store.changes(user, kind, cursor, limit = 1).changes)
        }
}
