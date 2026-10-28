package app.logdate.client.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.logdate.client.datastore.DataStoreKeyValueStorage
import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.media.MediaPayload
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.cloud.MediaUpload
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.InMemoryIdentityRecoveryNeededStore
import app.logdate.client.sync.metadata.KeyValueMediaSyncRefStore
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.test.FakeCloudApiClient
import app.logdate.client.sync.test.fakeJournalNotesRepository
import app.logdate.client.sync.test.fakeSessionStorage
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.sync.MediaUploadResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class MediaUploadAcknowledgementDurabilityTest {
    @Test
    fun `acknowledged file sends zero payload bytes on repeated sync and durable store reopening`() =
        runTest {
            fixture { f ->
                assertTrue(f.engine.fullSync().success)
                assertEquals(1, f.api.uploadMediaCalls.size)
                assertEquals(2048L, f.bytes())
                repeat(3) { assertTrue(f.engine.fullSync().success) }
                assertEquals(2048L, f.bytes(), "Repeated schedules must send zero additional media bytes")
                f.reopen()
                repeat(3) { assertTrue(f.engine.fullSync().success) }
                assertEquals(1, f.api.uploadMediaCalls.size)
                assertEquals(2048L, f.bytes(), "Reopening real on-disk preferences must preserve the upload receipt")
                assertEquals(0, f.metadata.getPendingCount())
            }
        }

    @Test
    fun `metadata retry after an acknowledged media upload reuses its durable receipt`() =
        runTest {
            fixture { f ->
                val successfulRecord = f.api.uploadContentResponse
                f.api.uploadContentResponse = Result.failure(IOException("Record acknowledgement lost"))
                assertTrue(!f.engine.uploadPendingChanges().success)
                assertEquals(2048L, f.bytes())
                f.reopen()
                f.api.uploadContentResponse = successfulRecord
                f.engine.releaseUploadBackoff()
                assertTrue(f.engine.uploadPendingChanges().success)
                assertEquals(1, f.api.uploadMediaCalls.size)
                assertEquals(2048L, f.bytes(), "Retrying the record must not retransmit acknowledged media")
            }
        }

    @Test
    fun `concurrent scheduled triggers share one actual media transfer`() =
        runTest {
            fixture { f ->
                f.api.pauseUpload = true
                val first = async { f.engine.uploadPendingChanges() }
                val second = async { f.engine.uploadPendingChanges() }
                assertTrue(first.await().success)
                assertTrue(second.await().success)
                assertEquals(1, f.api.uploadMediaCalls.size)
                assertEquals(2048L, f.bytes())
            }
        }

    @Test
    fun `a server accepted file with a lost response is reconciled without retransmitting payload`() =
        runTest {
            fixture { f ->
                f.api.loseNextMediaAcknowledgement = true
                assertTrue(!f.engine.uploadPendingChanges().success)
                assertEquals(1, f.api.acceptedMedia)
                assertEquals(2048L, f.bytes())
                f.reopen()
                f.engine.releaseUploadBackoff()
                assertTrue(f.engine.uploadPendingChanges().success)
                assertEquals(2048L, f.bytes(), "Already accepted media must be reconciled before sending its full body again")
            }
        }

    @Test
    fun `unhandled legacy media operations cannot be presented as files being uploaded`() =
        runTest {
            fixture { f ->
                assertTrue(f.engine.fullSync().success)
                val before = f.bytes()
                repeat(827) { f.metadata.enqueuePending("legacy-media-$it", EntityType.MEDIA, PendingOperation.CREATE) }
                assertTrue(f.engine.uploadPendingChanges().success)
                assertEquals(before, f.bytes(), "No media payload was transmitted")
                assertEquals(827, f.metadata.getPendingCount(), "Never clear legacy work to fix the display")
                assertEquals(
                    null,
                    f.engine.syncStatusFlow.value
                        .runProgress(),
                    "Held operations are not an active file transfer",
                )
            }
        }

    @Test
    fun `encryption recovery never presents queued work as an active transfer`() =
        runTest {
            fixture { f ->
                f.recovery.setNeeded(true)
                assertTrue(!f.engine.uploadPendingChanges().success)
                assertEquals(0L, f.bytes())
                assertEquals(1, f.metadata.getPendingCount())
                assertEquals(
                    null,
                    f.engine.syncStatusFlow.value
                        .runProgress(),
                )
            }
        }

    @Test
    fun `backed off work remains queued without advertising another transfer`() =
        runTest {
            fixture { f ->
                f.api.uploadContentResponse = Result.failure(IOException("Temporarily unavailable"))
                assertTrue(!f.engine.uploadPendingChanges().success)
                val before = f.bytes()
                f.engine.uploadPendingChanges()
                assertEquals(before, f.bytes())
                assertEquals(1, f.metadata.getPendingCount())
                assertEquals(
                    null,
                    f.engine.syncStatusFlow.value
                        .runProgress(),
                )
            }
        }

    @Test
    fun `intent survives reopening but changes for account server content or encryption version`() =
        runTest {
            fixture { f ->
                val first = f.intents.getOrCreate("content", "content-a:key-a")
                f.reopen()
                assertEquals(first, f.intents.getOrCreate("content", "content-a:key-a"))
                val second =
                    app.logdate.client.sync.metadata.MediaUploadIntentStore(f.storage) {
                        val bound = f.sessions.getOriginBoundSession()!!
                        app.logdate.client.sync.metadata
                            .UploadScope(bound.session.accountId, bound.origin)
                    }
                val concurrent =
                    (1..8)
                        .map { n ->
                            async {
                                (
                                    if (n % 2 ==
                                        0
                                    ) {
                                        f.intents
                                    } else {
                                        second
                                    }
                                ).getOrCreate("content", "new-content:key-a")
                            }
                        }.map { it.await() }

                assertTrue(concurrent.distinct().size == 1)
                kotlin.test.assertNotEquals(first, f.intents.getOrCreate("content", "content-b:key-a"))
                kotlin.test.assertNotEquals(first, f.intents.getOrCreate("content", "content-a:key-b"))
                assertEquals(first, f.intents.getOrCreate("content", "content-a:key-a"))
                val original = f.sessions.getSession()!!
                f.sessions.saveSession(original.copy(accountId = "other-account"))
                kotlin.test.assertNotEquals(first, f.intents.getOrCreate("content", "content-a:key-a"))
                f.sessions.saveSession(original)
                f.sessions.currentOrigin = "https://another.example.test"
                kotlin.test.assertNotEquals(first, f.intents.getOrCreate("content", "content-a:key-a"))
            }
        }

    @Test
    fun `same sized content mutation after fingerprinting cannot commit under the old intent`() =
        runTest {
            fixture { f ->
                var data = ByteArray(2048) { 1 }
                val source =
                    app.logdate.client.media.MediaFileSource("same.jpg", "image/jpeg", 2048) {
                        kotlinx.io.Buffer().apply { write(data) }
                    }
                f.api.beforeUpload = { data = ByteArray(2048) { 2 } }
                val result =
                    DefaultCloudMediaDataSource(f.api, uploadIntents = f.intents)
                        .uploadMedia("token", kotlin.uuid.Uuid.random(), source)
                assertTrue(result.isFailure, "Do not attach a receipt to bytes different from the local fingerprint")
                assertEquals(0, f.api.acceptedMedia)
            }
        }

    @Test
    fun `changed bytes or encryption key cannot reuse a lost upload receipt`() =
        runTest {
            fixture { f ->
                var data = ByteArray(128) { 1 }
                val source =
                    app.logdate.client.media.MediaFileSource("same.jpg", "image/jpeg", 128) {
                        kotlinx.io.Buffer().apply { write(data) }
                    }
                val id = kotlin.uuid.Uuid.random()
                val first = DefaultCloudMediaDataSource(f.api, uploadIntents = f.intents)
                f.api.loseNextMediaAcknowledgement = true
                assertTrue(first.uploadMedia("token", id, source).isFailure)
                f.reopen()
                data = ByteArray(128) { 2 }
                DefaultCloudMediaDataSource(f.api, uploadIntents = f.intents).uploadMedia("token", id, source).getOrThrow()
                assertEquals(2, f.api.acceptedMedia, "Changed bytes require their own upload")
                val cryptoA =
                    app.logdate.client.sync.crypto
                        .AesGcmMediaPayloadCrypto(ByteArray(32) { 3 })
                val cryptoB =
                    app.logdate.client.sync.crypto
                        .AesGcmMediaPayloadCrypto(ByteArray(32) { 4 })
                kotlin.test.assertNotEquals(cryptoA.streamEncryptor().receiptContext, cryptoB.streamEncryptor().receiptContext)
                DefaultCloudMediaDataSource(f.api, cryptoA, f.intents).uploadMedia("token", id, source).getOrThrow()
                DefaultCloudMediaDataSource(f.api, cryptoB, f.intents).uploadMedia("token", id, source).getOrThrow()
                assertEquals(4, f.api.acceptedMedia, "Changed encryption keys require distinct encrypted receipts")
            }
        }

    @Test
    fun `content changed while receipt lookup waits cannot adopt the older version`() =
        runTest {
            fixture { f ->
                var data = ByteArray(128) { 1 }
                val source =
                    app.logdate.client.media.MediaFileSource("same.jpg", "image/jpeg", 128) {
                        kotlinx.io.Buffer().apply { write(data) }
                    }
                val id = kotlin.uuid.Uuid.random()
                f.api.loseNextMediaAcknowledgement = true
                assertTrue(DefaultCloudMediaDataSource(f.api, uploadIntents = f.intents).uploadMedia("token", id, source).isFailure)
                f.reopen()
                f.api.beforeReceiptLookup = {
                    data = ByteArray(128) { 2 }
                    f.api.beforeReceiptLookup = null
                }
                val retry = DefaultCloudMediaDataSource(f.api, uploadIntents = f.intents).uploadMedia("token", id, source)
                assertTrue(retry.isFailure, "Receipt belongs to the earlier bytes, not the changed file")
                assertEquals(1, f.api.acceptedMedia)
                DefaultCloudMediaDataSource(f.api, uploadIntents = f.intents).uploadMedia("token", id, source).getOrThrow()
                assertEquals(2, f.api.acceptedMedia)
            }
        }

    @Test
    fun `independent concurrent callers of one intent send only one payload`() =
        runTest {
            fixture { f ->
                val source =
                    app.logdate.client.media.MediaFileSource("same.jpg", "image/jpeg", 128) {
                        kotlinx.io.Buffer().apply { write(ByteArray(128) { 1 }) }
                    }
                val id = kotlin.uuid.Uuid.random()
                val fingerprint =
                    app.logdate.client.sync.cloud
                        .mediaReceiptFingerprint(source, "passthrough-v1")
                        .value
                f.intents.getOrCreate(id.toString(), fingerprint)
                f.api.pauseUpload = true
                val first = async { DefaultCloudMediaDataSource(f.api, uploadIntents = f.intents).uploadMedia("token", id, source) }
                val second = async { DefaultCloudMediaDataSource(f.api, uploadIntents = f.intents).uploadMedia("token", id, source) }
                first.await().getOrThrow()
                second.await().getOrThrow()
                assertEquals(1, f.api.acceptedMedia)
                assertEquals(128L, f.bytes())
            }
        }

    @Test
    fun `receipt service rollback never authorizes blind replay of a negotiated upload`() =
        runTest {
            fixture { f ->
                f.api.loseNextMediaAcknowledgement = true
                assertTrue(!f.engine.uploadPendingChanges().success)
                assertEquals(2048L, f.bytes())
                f.reopen()
                f.api.receiptsUnsupported = true
                f.engine.releaseUploadBackoff()
                assertTrue(!f.engine.uploadPendingChanges().success)
                assertEquals(2048L, f.bytes(), "A missing previously supported receipt API is an error, not permission to retransmit")
                f.api.receiptsUnsupported = false
                f.engine.releaseUploadBackoff()
                assertTrue(f.engine.uploadPendingChanges().success)
                assertEquals(2048L, f.bytes())
            }
        }

    private suspend fun TestScope.fixture(block: suspend (Fixture) -> Unit) {
        val root = Files.createTempDirectory("upload-receipt-audit")
        val f = Fixture(this, root.resolve("receipts.preferences_pb").toFile())
        try {
            f.start()
            block(f)
        } finally {
            f.close()
            root.toFile().deleteRecursively()
        }
    }

    private class Fixture(
        private val tests: TestScope,
        private val file: java.io.File,
    ) {
        val api = CountingApi()
        val sessions = fakeSessionStorage()
        val recovery = InMemoryIdentityRecoveryNeededStore()
        val metadata = fakeSyncMetadataService(sessions)
        private val notes = fakeJournalNotesRepository()
        private val media = InMemoryMediaManager()
        private lateinit var storeJob: Job
        private lateinit var engineJob: Job
        lateinit var storage: DataStoreKeyValueStorage
        lateinit var intents: app.logdate.client.sync.metadata.MediaUploadIntentStore
        lateinit var engine: DefaultSyncManager

        suspend fun start() {
            val uri = media.saveMedia(MediaPayload("fixture.jpg", "image/jpeg", 2048, ByteArray(2048) { 19 }))
            val now = Clock.System.now()
            val note = JournalNote.Image(creationTimestamp = now, lastUpdated = now, mediaRef = uri)
            notes.create(note)
            metadata.enqueuePending(note.uid.toString(), EntityType.NOTE, PendingOperation.CREATE)
            open()
        }

        private fun open() {
            storeJob = SupervisorJob()
            val disk = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + storeJob), produceFile = { file })
            storage = DataStoreKeyValueStorage(disk)
            intents =
                app.logdate.client.sync.metadata.MediaUploadIntentStore(storage) {
                    val bound = sessions.getOriginBoundSession()!!
                    app.logdate.client.sync.metadata
                        .UploadScope(bound.session.accountId, bound.origin)
                }
            val refs =
                KeyValueMediaSyncRefStore(storage, {
                    sessions.getOriginBoundSession()!!.origin
                }, { sessions.getSession()!!.accountId })
            engineJob = SupervisorJob(tests.backgroundScope.coroutineContext[Job])
            engine =
                testDefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(api),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(api, uploadIntents = intents),
                    sessionStorage = sessions,
                    identityRecoveryNeededStore = recovery,
                    mediaManager = media,
                    mediaSyncRefStore = refs,
                    journalNotesRepository = notes,
                    syncMetadataService = metadata,
                    syncScope = CoroutineScope(tests.backgroundScope.coroutineContext + engineJob),
                )
        }

        suspend fun reopen() {
            close()
            open()
        }

        suspend fun close() {
            engineJob.cancelAndJoin()
            storeJob.cancelAndJoin()
        }

        fun bytes() =
            api.uploadMediaCalls.sumOf {
                it.second.data.size
                    .toLong()
            }
    }

    private class CountingApi : FakeCloudApiClient() {
        var loseNextMediaAcknowledgement = false
        var acceptedMedia = 0
        var beforeUpload: (() -> Unit)? = null
        var beforeReceiptLookup: (() -> Unit)? = null
        var receiptsUnsupported = false
        var pauseUpload = false
        private val receipts = mutableMapOf<String, MediaUploadResponse>()

        override suspend fun getMediaUploadReceipt(
            accessToken: String,
            uploadIntentId: String,
        ): Result<MediaUploadResponse?> {
            if (receiptsUnsupported) return Result.failure(UnsupportedOperationException("Synthetic older server"))
            beforeReceiptLookup?.invoke()
            return Result.success(receipts[uploadIntentId])
        }

        override suspend fun uploadMedia(
            accessToken: String,
            media: MediaUpload,
        ): Result<MediaUploadResponse> {
            if (pauseUpload) delay(20)
            beforeUpload?.invoke()
            val stored = super.uploadMedia(accessToken, media)
            if (stored.isSuccess) {
                acceptedMedia++
                media.uploadIntentId?.let { receipts[it] = stored.getOrThrow().copy(contentId = media.contentId, uploadIntentId = it) }
            }
            if (loseNextMediaAcknowledgement) {
                loseNextMediaAcknowledgement = false
                return Result.failure(IOException("Response lost after server acceptance"))
            }
            return stored.map { it.copy(contentId = media.contentId, uploadIntentId = media.uploadIntentId) }
        }
    }
}
