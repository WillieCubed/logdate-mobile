package app.logdate.client.sync.space

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.logdate.client.datastore.DataStoreKeyValueStorage
import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.media.MediaFileSource
import app.logdate.client.media.MediaPayload
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.SyncMediaTransfer
import app.logdate.client.sync.crypto.AesGcmMediaPayloadCrypto
import app.logdate.client.sync.metadata.KeyValueMediaSyncRefStore
import app.logdate.client.sync.metadata.MediaUploadIntentStore
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.space.BlobUploadReceipt
import app.logdate.space.BlobUploadReceiptLookup
import app.logdate.space.BlobUploadReceipts
import app.logdate.space.DataHome
import app.logdate.space.SpaceRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class SpaceBlobUploadReplayTest {
    @Test
    fun `acknowledged unchanged space media sends zero new bytes after durable store reopening`() =
        runTest {
            fixture { f ->
                assertTrue(f.transfer().uploadIfNeeded("test-access-token", f.note).isSuccess)
                val firstBytes = f.home.uploadedBytes
                f.reopen()
                repeat(3) { assertTrue(f.transfer().uploadIfNeeded("test-access-token", f.note).isSuccess) }
                assertEquals(1, f.home.uploadCalls)
                assertEquals(firstBytes, f.home.uploadedBytes)
            }
        }

    @Test
    fun `space blob committed before a lost response is recovered without another payload after restart`() =
        runTest {
            fixture { f ->
                f.home.loseNextResponse = true
                assertTrue(f.transfer().uploadIfNeeded("test-access-token", f.note).isFailure)
                val committedBytes = f.home.uploadedBytes
                assertEquals(1, f.home.server.blobs.size)
                f.reopen()
                assertTrue(f.transfer().uploadIfNeeded("test-access-token", f.note).isSuccess)
                assertEquals(committedBytes, f.home.uploadedBytes, "An already committed ciphertext must not cross the network again")
                assertEquals(1, f.home.uploadCalls)
                assertEquals(1, f.home.server.blobs.size, "Retry must not create a second randomly encrypted object")
            }
        }

    @Test
    fun `changed plaintext and encryption keys have separate receipts`() =
        runTest {
            fixture { f ->
                var data = ByteArray(128) { 1 }
                val source = MediaFileSource("same.jpg", "image/jpeg", 128) { Buffer().apply { write(data) } }
                val id = Uuid.random()
                f.home.loseNextResponse = true
                assertTrue(f.dataSource().uploadMedia("token", id, source).isFailure)
                f.reopen()
                data = ByteArray(128) { 2 }
                f.dataSource().uploadMedia("token", id, source).getOrThrow()
                f.dataSource(ByteArray(32) { 9 }).uploadMedia("token", id, source).getOrThrow()
                assertEquals(3, f.home.uploadCalls)
            }
        }

    @Test
    fun `changed content during receipt lookup cannot adopt an older receipt`() =
        runTest {
            fixture { f ->
                var data = ByteArray(128) { 1 }
                val source = MediaFileSource("same.jpg", "image/jpeg", 128) { Buffer().apply { write(data) } }
                val id = Uuid.random()
                f.home.loseNextResponse = true
                assertTrue(f.dataSource().uploadMedia("token", id, source).isFailure)
                f.reopen()
                f.home.beforeLookup = {
                    data = ByteArray(128) { 2 }
                    f.home.beforeLookup = null
                }
                assertTrue(f.dataSource().uploadMedia("token", id, source).isFailure)
                assertEquals(1, f.home.uploadCalls)
                f.dataSource().uploadMedia("token", id, source).getOrThrow()
                assertEquals(2, f.home.uploadCalls)
            }
        }

    @Test
    fun `independent concurrent callers send one ciphertext payload`() =
        runTest {
            fixture { f ->
                val data = ByteArray(128) { 1 }
                val source = MediaFileSource("same.jpg", "image/jpeg", 128) { Buffer().apply { write(data) } }
                val id = Uuid.random()
                (1..4).map { async { f.dataSource().uploadMedia("token", id, source).getOrThrow() } }.awaitAll()
                assertEquals(1, f.home.uploadCalls)
                assertEquals(176L, f.home.uploadedBytes)
            }
        }

    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val root = Files.createTempDirectory("space-upload-replay")
        val f = Fixture(root.resolve("refs.preferences_pb").toFile())
        try {
            f.open()
            block(f)
        } finally {
            f.close()
            root.toFile().deleteRecursively()
        }
    }

    private class Fixture(
        private val file: java.io.File,
    ) {
        val home = CountingHome()
        private val media = InMemoryMediaManager()
        lateinit var note: JournalNote.Image
        private lateinit var storeJob: Job
        private lateinit var storage: DataStoreKeyValueStorage

        suspend fun open() {
            storeJob = SupervisorJob()
            storage =
                DataStoreKeyValueStorage(
                    PreferenceDataStoreFactory.create(
                        scope = CoroutineScope(Dispatchers.IO + storeJob),
                        produceFile = { file },
                    ),
                )
            if (!::note.isInitialized) {
                val bytes = ByteArray(4096) { (it % 239).toByte() }
                val uri = media.saveMedia(MediaPayload("synthetic-space.jpg", "image/jpeg", bytes.size.toLong(), bytes))
                val now = Instant.parse("2026-10-28T12:00:00Z")
                note = JournalNote.Image(creationTimestamp = now, lastUpdated = now, mediaRef = uri)
            }
        }

        fun transfer() =
            SyncMediaTransfer(
                media,
                KeyValueMediaSyncRefStore(storage, { "https://cloud.test" }, { TEST_DID }),
                dataSource(),
            )

        fun dataSource(key: ByteArray = TEST_MEDIA_KEY) =
            SpaceMediaDataSource(
                SpaceDataHomes { _, _ -> home.client },
                AesGcmMediaPayloadCrypto(key),
                { SpaceLocation("https://cloud.test", TEST_SPACE) },
                intents = MediaUploadIntentStore(storage) { UploadScope(TEST_DID, "https://cloud.test") },
            )

        suspend fun reopen() {
            close()
            open()
        }

        suspend fun close() {
            if (::storeJob.isInitialized) storeJob.cancelAndJoin()
        }
    }

    private class CountingHome {
        val server = FakeDataHome()
        var uploadCalls = 0
        var uploadedBytes = 0L
        var loseNextResponse = false
        var beforeLookup: (() -> Unit)? = null
        private val delegate = server.signedIn("test-access-token")
        private val receipts = mutableMapOf<String, BlobUploadReceipt>()
        val client: DataHome =
            object : DataHome by delegate, BlobUploadReceipts {
                override suspend fun lookupBlobUpload(
                    space: SpaceRef,
                    intent: String,
                ): BlobUploadReceiptLookup {
                    beforeLookup?.invoke()
                    return BlobUploadReceiptLookup(true, receipts["${space.uri}:$intent"])
                }

                override suspend fun uploadBlobWithIntent(
                    space: SpaceRef,
                    intent: String,
                    sizeBytes: Long,
                    open: () -> RawSource,
                ): String {
                    receipts["${space.uri}:$intent"]?.let { return it.cid }
                    val bytes = open().buffered().use { it.readByteArray() }
                    require(bytes.size.toLong() == sizeBytes)
                    uploadCalls++
                    uploadedBytes += bytes.size
                    val cid = delegate.uploadBlob(space, bytes)
                    receipts["${space.uri}:$intent"] = BlobUploadReceipt(cid, sizeBytes)
                    if (loseNextResponse) {
                        loseNextResponse = false
                        throw IOException("synthetic response lost after commit")
                    }
                    return cid
                }

                override suspend fun uploadBlob(
                    space: SpaceRef,
                    sizeBytes: Long,
                    open: () -> RawSource,
                ): String {
                    val bytes = open().buffered().use { it.readByteArray() }
                    require(bytes.size.toLong() == sizeBytes)
                    uploadCalls++
                    uploadedBytes += bytes.size
                    val committed = delegate.uploadBlob(space, bytes)
                    if (loseNextResponse) {
                        loseNextResponse = false
                        throw IOException("synthetic response lost after commit")
                    }
                    return committed
                }
            }
    }
}
