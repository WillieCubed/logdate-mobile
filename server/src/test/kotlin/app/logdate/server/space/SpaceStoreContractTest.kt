package app.logdate.server.space

import app.logdate.server.database.AccountsTable
import app.logdate.server.database.PostgreSQLSpaceStore
import app.logdate.server.database.SpaceBlobsTable
import app.logdate.server.database.SpaceRecordBlobsTable
import app.logdate.server.database.SpaceRecordsTable
import app.logdate.server.database.SpaceStatesTable
import app.logdate.server.database.support.withH2Database
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

abstract class SpaceStoreContractTest {
    abstract fun withStore(block: suspend (SpaceStore, UUID) -> Unit)

    private val space = "at://did:plc:owner/space/app.logdate.space.personal/self"
    private val author = "did:plc:owner"

    private fun sealed(fill: Int) =
        buildJsonObject {
            put("\$type", "app.logdate.crypto.sealed")
            put("version", 1)
            put("keyId", "abcdefghijklm")
            putJsonObject("nonce") { put("\$bytes", Base64.encode(ByteArray(12))) }
            putJsonObject("ciphertext") { put("\$bytes", Base64.encode(ByteArray(528) { fill.toByte() })) }
        }

    private fun write(
        account: UUID,
        rkey: String,
        fill: Int = 1,
        swapCid: String? = null,
        createOnly: Boolean = false,
        blobs: List<String> = emptyList(),
    ) = SpaceRecordWrite(account, space, author, rkey, sealed(fill), blobs, swapCid, createOnly)

    @Test
    fun `writes get increasing sequence numbers and read back`() =
        withStore { store, account ->
            val first = store.put(write(account, "a"))
            val second = store.put(write(account, "b"))
            assertTrue(second.seq > first.seq)
            assertEquals(first, store.get(space, author, "a"))
        }

    @Test
    fun `createOnly refuses an existing record and swapCid refuses a changed one`() =
        withStore { store, account ->
            val first = store.put(write(account, "a"))
            assertFailsWith<SpaceSwapException> { store.put(write(account, "a", createOnly = true)) }
            store.put(write(account, "a", fill = 2, swapCid = first.cid))
            assertFailsWith<SpaceSwapException> { store.put(write(account, "a", fill = 3, swapCid = first.cid)) }
        }

    @Test
    fun `deletions are listed in order as tombstones`() =
        withStore { store, account ->
            store.put(write(account, "a"))
            val put = store.put(write(account, "b"))
            val deleted = store.delete(account, space, author, "a", swapCid = null)
            assertNull(store.get(space, author, "a"))
            val page = store.changes(space, author, since = put.seq, limit = 10)
            assertEquals(listOf(SpaceChange("a", deleted, true, null, null)), page.changes)
            assertEquals(deleted, page.cursor)
            assertFalse(page.hasMore)
        }

    @Test
    fun `deleting a missing record or a tombstone changes nothing`() =
        withStore { store, account ->
            assertEquals(0L, store.delete(account, space, author, "never", swapCid = null))
            val put = store.put(write(account, "a"))
            assertEquals(put.seq, store.delete(account, space, author, "never", swapCid = null))
            val deleted = store.delete(account, space, author, "a", swapCid = null)
            assertEquals(deleted, store.delete(account, space, author, "a", swapCid = "bafyanything"))
            assertEquals(deleted, store.changes(space, author, 0, 10).cursor)
        }

    @Test
    fun `delete refuses a changed record and a tombstone can be written again`() =
        withStore { store, account ->
            val first = store.put(write(account, "a"))
            assertFailsWith<SpaceSwapException> { store.delete(account, space, author, "a", swapCid = "bafyother") }
            store.delete(account, space, author, "a", swapCid = first.cid)
            val again = store.put(write(account, "a", createOnly = true))
            assertEquals(again, store.get(space, author, "a"))
        }

    @Test
    fun `changes page by limit`() =
        withStore { store, account ->
            repeat(3) { store.put(write(account, "r$it")) }
            val first = store.changes(space, author, 0, 2)
            assertEquals(2, first.changes.size)
            assertTrue(first.hasMore)
            val rest = store.changes(space, author, first.cursor, 2)
            assertEquals(listOf("r2"), rest.changes.map { it.rkey })
            assertFalse(rest.hasMore)
            val empty = store.changes(space, author, rest.cursor, 2)
            assertEquals(SpaceChangePage(emptyList(), rest.cursor, false), empty)
        }

    @Test
    fun `purge deletes old tombstones and unreferenced old blobs only`() =
        withStore { store, account ->
            val now = Clock.System.now()
            store.putBlob(account, space, SpaceBlob("bafyused", "application/octet-stream", 10, "path/used"))
            store.putBlob(account, space, SpaceBlob("bafyorphan", "application/octet-stream", 10, "path/orphan"))
            store.put(write(account, "a", blobs = listOf("bafyused")))
            store.put(write(account, "b"))
            store.delete(account, space, author, "b", null)
            val purged = store.purge(tombstonesBefore = now + 1.hours, unreferencedBlobsBefore = now + 1.hours)
            assertEquals(listOf("path/orphan"), purged)
            assertNotNull(store.getBlob(space, "bafyused"))
            assertNull(store.getBlob(space, "bafyorphan"))
            assertEquals(listOf("a"), store.changes(space, author, 0, 10).changes.map { it.rkey })
        }

    @Test
    fun `put referencing an unknown blob is refused`() =
        withStore { store, account ->
            store.putBlob(account, space, SpaceBlob("bafyknown", "application/octet-stream", 10, "path/known"))
            store.putBlob(account, "at://did:plc:other/space/app.logdate.space.personal/self", SpaceBlob("bafyelsewhere", "x", 1, "p"))
            val missing =
                assertFailsWith<SpaceMissingBlobException> {
                    store.put(write(account, "a", blobs = listOf("bafyknown", "bafyelsewhere")))
                }
            assertEquals("bafyelsewhere", missing.cid)
            assertNull(store.get(space, author, "a"))
            assertEquals(emptyList(), store.changes(space, author, 0, 10).changes)
            assertEquals(1L, store.put(write(account, "a", blobs = listOf("bafyknown"))).seq)
        }

    @Test
    fun `put refuses a record that isn't a sealed envelope`() =
        withStore { store, account ->
            val plain = SpaceRecordWrite(account, space, author, "a", buildJsonObject { put("text", "hello") }, emptyList(), null, false)
            val refused = assertFailsWith<SpaceInvalidRecordException> { store.put(plain) }
            assertEquals("Not an app.logdate.crypto.sealed record", refused.reason)
            assertNull(store.get(space, author, "a"))
        }

    @Test
    fun `changes requires a limit from 1 to the maximum`() =
        withStore { store, account ->
            store.put(write(account, "a"))
            assertFailsWith<IllegalArgumentException> { store.changes(space, author, 0, 0) }
            assertFailsWith<IllegalArgumentException> { store.changes(space, author, 0, MAX_SPACE_CHANGES_LIMIT + 1) }
            assertEquals(1, store.changes(space, author, 0, MAX_SPACE_CHANGES_LIMIT).changes.size)
        }

    @Test
    fun `putting an existing blob keeps its storage path and restarts its grace period`() =
        withStore { store, account ->
            val original = SpaceBlob("bafyblob", "application/octet-stream", 10, "path/original")
            assertEquals(original, store.putBlob(account, space, original))
            delay(5)
            val between = Clock.System.now()
            delay(5)
            val stored = store.putBlob(account, space, SpaceBlob("bafyblob", "image/jpeg", 12, "path/second"))
            assertEquals(SpaceBlob("bafyblob", "image/jpeg", 12, "path/original"), stored)
            assertEquals(stored, store.getBlob(space, "bafyblob"))
            assertEquals(emptyList(), store.purge(tombstonesBefore = between, unreferencedBlobsBefore = between))
            assertNotNull(store.getBlob(space, "bafyblob"))
        }

    @Test
    fun `a purged blob stored again gets a new row at its new path`() =
        withStore { store, account ->
            store.putBlob(account, space, SpaceBlob("bafyblob", "application/octet-stream", 10, "path/first"))
            val later = Clock.System.now() + 1.hours
            assertEquals(listOf("path/first"), store.purge(tombstonesBefore = later, unreferencedBlobsBefore = later))
            val again = SpaceBlob("bafyblob", "application/octet-stream", 10, "path/again")
            assertEquals(again, store.putBlob(account, space, again))
            assertEquals(again, store.getBlob(space, "bafyblob"))
        }

    @Test
    fun `purge keeps recent tombstones and blobs`() =
        withStore { store, account ->
            val before = Clock.System.now() - 1.hours
            store.putBlob(account, space, SpaceBlob("bafyorphan", "application/octet-stream", 10, "path/orphan"))
            store.put(write(account, "b"))
            store.delete(account, space, author, "b", null)
            assertEquals(emptyList(), store.purge(tombstonesBefore = before, unreferencedBlobsBefore = before))
            assertEquals(SpaceBlob("bafyorphan", "application/octet-stream", 10, "path/orphan"), store.getBlob(space, "bafyorphan"))
            assertEquals(listOf("b"), store.changes(space, author, 0, 10).changes.map { it.rkey })
        }
}

class InMemorySpaceStoreTest : SpaceStoreContractTest() {
    override fun withStore(block: suspend (SpaceStore, UUID) -> Unit) = runBlocking { block(InMemorySpaceStore(), UUID.randomUUID()) }
}

class PostgreSQLSpaceStoreH2Test : SpaceStoreContractTest() {
    override fun withStore(block: suspend (SpaceStore, UUID) -> Unit) =
        withH2Database(AccountsTable, SpaceStatesTable, SpaceRecordsTable, SpaceRecordBlobsTable, SpaceBlobsTable) {
            val account = UUID.randomUUID()
            transaction {
                AccountsTable.insert {
                    it[id] = account
                    it[username] = "owner"
                    it[displayName] = "Owner"
                    it[createdAt] = Clock.System.now()
                }
            }
            runBlocking { block(PostgreSQLSpaceStore(), account) }
        }
}
