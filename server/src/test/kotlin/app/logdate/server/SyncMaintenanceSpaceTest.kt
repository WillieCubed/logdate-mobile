package app.logdate.server

import app.logdate.server.logdate.InMemoryLogDateBlobStorage
import app.logdate.server.logdate.LogDateBlobNamespace
import app.logdate.server.logdate.LogDateBlobStorage
import app.logdate.server.logdate.LogDateBlobWriteRequest
import app.logdate.server.logging.SpaceBlobNotices
import app.logdate.server.logging.safeServerMessage
import app.logdate.server.space.InMemorySpaceStore
import app.logdate.server.space.SpaceBlob
import app.logdate.server.space.SpaceRecordWrite
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class SyncMaintenanceSpaceTest {
    private val owner = UUID.randomUUID()
    private val space = "at://did:plc:owner/space/app.logdate.space.personal/self"
    private val author = "did:plc:owner"
    private val logged = mutableListOf<Pair<LogLevel, String?>>()
    private val antilog =
        object : Antilog() {
            override fun performLog(
                priority: LogLevel,
                tag: String?,
                throwable: Throwable?,
                message: String?,
            ) {
                logged += priority to message
            }
        }

    private fun storeBlob(
        storage: LogDateBlobStorage,
        cid: String,
    ): SpaceBlob {
        val path =
            storage.putBlob(
                LogDateBlobWriteRequest(owner, LogDateBlobNamespace.SPACE, cid, null, "application/octet-stream", byteArrayOf(1)),
            )
        return SpaceBlob(cid, "application/octet-stream", 1, path)
    }

    private fun sealed(): JsonObject =
        buildJsonObject {
            put("\$type", "app.logdate.crypto.sealed")
            put("version", 1)
            put("keyId", "abcdefghijklm")
            putJsonObject("nonce") { put("\$bytes", Base64.encode(ByteArray(12))) }
            putJsonObject("ciphertext") { put("\$bytes", Base64.encode(ByteArray(528))) }
        }

    @Test
    fun `old tombstones and blobs nothing refers to are purged from the store and storage`() =
        runTest {
            val store = InMemorySpaceStore()
            val storage = InMemoryLogDateBlobStorage()
            val orphan = store.putBlob(owner, space, storeBlob(storage, "bafyorphan"))

            purgeSpaces(store, storage, now = Clock.System.now() + 2.days)

            assertNull(storage.getBlob(orphan.storagePath))
            assertNull(store.getBlob(space, "bafyorphan"))
        }

    @Test
    fun `a blob a live record refers to and a recent orphan both survive`() =
        runTest {
            val store = InMemorySpaceStore()
            val storage = InMemoryLogDateBlobStorage()
            val used = store.putBlob(owner, space, storeBlob(storage, "bafyused"))
            val recent = store.putBlob(owner, space, storeBlob(storage, "bafyrecent"))
            store.put(SpaceRecordWrite(owner, space, author, "a", sealed(), listOf("bafyused"), null, false))

            purgeSpaces(store, storage, now = Clock.System.now() + 2.hours)

            assertNotNull(storage.getBlob(used.storagePath))
            assertNotNull(store.getBlob(space, "bafyused"))
            assertNotNull(storage.getBlob(recent.storagePath))
            assertNotNull(store.getBlob(space, "bafyrecent"))
        }

    @Test
    fun `a storage delete that throws is logged and the other blobs are still deleted`() =
        runTest {
            val store = InMemorySpaceStore()
            val delegate = InMemoryLogDateBlobStorage()
            val first = store.putBlob(owner, space, storeBlob(delegate, "bafyfirst"))
            val second = store.putBlob(owner, space, storeBlob(delegate, "bafysecond"))
            val failingPath = listOf(first.storagePath, second.storagePath).minOf { it }
            val storage =
                object : LogDateBlobStorage by delegate {
                    override fun deleteBlobIfExists(storagePath: String) {
                        check(storagePath != failingPath) { "provider failure" }
                        delegate.deleteBlobIfExists(storagePath)
                    }
                }
            Napier.base(antilog)
            try {
                purgeSpaces(store, storage, now = Clock.System.now() + 2.days)
            } finally {
                Napier.takeLogarithm(antilog)
            }

            val survivor = listOf(first, second).single { it.storagePath == failingPath }
            val deleted = listOf(first, second).single { it.storagePath != failingPath }
            assertNotNull(delegate.getBlob(survivor.storagePath))
            assertNull(delegate.getBlob(deleted.storagePath))
            assertEquals(1, logged.count { it == LogLevel.ERROR to SpaceBlobNotices.BLOB_PURGE_FAILED })
        }

    @Test
    fun `space purge outcomes keep their own codes in production logs`() {
        mapOf(
            SpaceBlobNotices.BLOBS_PURGED to "SERVER_SPACE_BLOBS_PURGED",
            SpaceBlobNotices.BLOB_PURGE_FAILED to "SERVER_SPACE_BLOB_PURGE_FAILED",
        ).forEach { (message, code) ->
            assertEquals(code, safeServerMessage(LogLevel.ERROR, tag = null, message = message))
        }
    }
}
