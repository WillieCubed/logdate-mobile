package app.logdate.client.sync

import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.media.MediaFileSource
import app.logdate.client.networking.DataUsageMode
import app.logdate.client.networking.DataUsagePolicy
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.cloud.CloudMediaDataSource
import app.logdate.client.sync.cloud.MediaFile
import app.logdate.client.sync.cloud.MediaUploadResult
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.InMemoryFirstSyncEnqueueStore
import app.logdate.client.sync.metadata.KeyValueMediaSyncRefStore
import app.logdate.client.sync.metadata.MediaSyncRef
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadInboxTest
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.client.sync.recovery.WireDownload
import app.logdate.client.sync.test.fakeDataUsagePolicy
import app.logdate.client.sync.test.fakeJournalNotesRepository
import app.logdate.client.sync.test.fakeSessionStorage
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class FullSyncMediaRecoveryProgressTest {
    @Test
    fun `metered originals wait without failing sync or acknowledging recovery completion`() =
        runTest {
            val fixture = Fixture(backgroundScope, DataUsageMode.Conservative)
            fixture.populate(101)

            val paused = runSyncBatches { fixture.manager.fullSync() }

            assertTrue(paused.success)
            assertFalse(paused.hasMorePending)
            assertNull(paused.recoveredMediaScope)
            assertTrue(fixture.downloads.isEmpty())
            assertEquals(101, fixture.database.rows.count { it.state != "APPLIED" })
            assertTrue(
                fixture.database.rows
                    .filter { it.reason == "NETWORK_POLICY" }
                    .all { it.attempts == 0 },
            )

            fixture.mode.value = DataUsageMode.Unrestricted
            val held = fixture.manager.fullSync()
            assertTrue(held.success)
            assertFalse(held.hasMorePending, "Held policy rows must not spin before their readiness wake releases backoff")
            assertNull(held.recoveredMediaScope)
            fixture.manager.releaseUploadBackoff()
            val resumed = runSyncBatches { fixture.manager.fullSync() }

            assertTrue(resumed.success)
            assertFalse(resumed.hasMorePending)
            assertEquals(fixture.scope, resumed.recoveredMediaScope)
            assertEquals(101, fixture.downloads.distinct().size)
            assertEquals(0, fixture.inbox.count())
        }

    @Test
    fun `large native recovery continues successful pages without failure backoff`() =
        runTest {
            val fixture = Fixture(backgroundScope)
            fixture.populate(201)

            val first = fixture.manager.fullSync()

            assertTrue(first.success)
            assertTrue(first.hasMorePending)
            assertNull(first.recoveredMediaScope)
            assertEquals(100, fixture.downloads.size, "The fixture must exercise the real inbox page limit")

            val finished = runSyncBatches { fixture.manager.fullSync() }

            assertTrue(finished.success)
            assertFalse(finished.hasMorePending)
            assertEquals(fixture.scope, finished.recoveredMediaScope)
            assertEquals(201, fixture.downloads.size)
            assertEquals(201, fixture.downloads.distinct().size)
            assertEquals(0, fixture.inbox.count())
            assertTrue(fixture.database.rows.all { it.state == "APPLIED" })
        }

    @Test
    fun `recovery beyond one worker batch retains remaining originals for its next run`() =
        runTest {
            val fixture = Fixture(backgroundScope)
            fixture.populate(901)

            val unfinished = runSyncBatches { fixture.manager.fullSync() }

            assertTrue(unfinished.success)
            assertTrue(unfinished.hasMorePending)
            assertNull(unfinished.recoveredMediaScope)
            assertEquals(800, fixture.downloads.size)
            assertEquals(101, fixture.inbox.count())

            val completed = runSyncBatches { fixture.manager.fullSync() }

            assertTrue(completed.success)
            assertFalse(completed.hasMorePending)
            assertEquals(fixture.scope, completed.recoveredMediaScope)
            assertEquals(901, fixture.downloads.size)
            assertEquals(901, fixture.downloads.distinct().size)
            assertEquals(0, fixture.inbox.count())
        }

    @Test
    fun `released offline failures continue healthy pages without retaining stale failure state`() =
        runTest {
            val fixture = Fixture(backgroundScope)
            fixture.populate(201, previouslyFailed = true)
            fixture.manager.releaseUploadBackoff()

            val first = fixture.manager.fullSync()

            assertTrue(first.success)
            assertTrue(first.hasMorePending)
            assertEquals(100, fixture.downloads.size)
            assertNull(first.recoveredMediaScope)
            val finished = runSyncBatches { fixture.manager.fullSync() }
            assertTrue(finished.success)
            assertEquals(fixture.scope, finished.recoveredMediaScope)
            assertEquals(201, fixture.downloads.distinct().size)
            assertTrue(fixture.database.rows.all { it.state == "APPLIED" })
        }

    @Test
    fun `an actual original download failure still requests retry`() =
        runTest {
            val fixture = Fixture(backgroundScope)
            fixture.populate(1)
            fixture.failDownload = true

            val result = fixture.manager.fullSync()

            assertFalse(result.success)
            assertTrue(result.hasMorePending)
            assertNull(result.recoveredMediaScope)
            assertEquals(1, fixture.database.rows.count { it.state == "FAILED" })
        }

    @Test
    fun `unsupported retained media cannot prove that recovery is complete`() =
        runTest {
            val fixture = Fixture(backgroundScope)
            val id = Uuid.random().toString()
            fixture.inbox.stage("MEDIA_NATIVE_NOTE", 1, listOf(WireDownload(id, 1, false, "retained")))
            fixture.inbox.failed("MEDIA_NATIVE_NOTE", id, "UNSUPPORTED_FORMAT")

            val result = fixture.manager.fullSync()

            assertTrue(result.success)
            assertFalse(result.hasMorePending)
            assertNull(result.recoveredMediaScope)
            assertEquals(1, fixture.database.rows.count { it.state != "APPLIED" })
        }

    @Test
    fun `batch runner drains successful pages and stops at a real failure`() =
        runTest {
            val expected = SyncResult(success = true)
            var calls = 0
            assertEquals(expected, runSyncBatches { if (++calls < 3) SyncResult(true, hasMorePending = true) else expected })
            assertEquals(3, calls)

            calls = 0
            val failed = SyncResult(success = false, hasMorePending = true)
            assertEquals(failed, runSyncBatches { if (++calls == 1) SyncResult(true, hasMorePending = true) else failed })
            assertEquals(2, calls)
            calls = 0
            val pending = SyncResult(success = true, hasMorePending = true)
            assertEquals(
                pending,
                runSyncBatches {
                    assertTrue(++calls <= 8, "Unfinished work must return to durable retry")
                    pending
                },
            )
            assertFailsWith<CancellationException> { runSyncBatches { throw CancellationException("stopped") } }
        }

    private class Fixture(
        background: CoroutineScope,
        initialMode: DataUsageMode = DataUsageMode.Unrestricted,
    ) {
        private val sessions = fakeSessionStorage(authenticated = true)
        private val bound = requireNotNull(sessions.getOriginBoundSession())
        val scope = UploadScope(bound.session.accountId, bound.origin)
        val database = DownloadInboxTest.Database()
        val inbox = DownloadInbox(database, database, { DownloadScope(scope.ownerId, scope.serverOrigin) }, { 1L })
        private val notes = fakeJournalNotesRepository()
        private val media = InMemoryMediaManager()
        private val refs = KeyValueMediaSyncRefStore(InMemoryKeyValueStorage(), { scope.serverOrigin }, { scope.ownerId })
        private val firstSync = InMemoryFirstSyncEnqueueStore()
        val mode = MutableStateFlow(initialMode)
        val downloads = mutableListOf<String>()
        var failDownload = false
        private val cloud =
            object : CloudMediaDataSource {
                override suspend fun uploadMedia(
                    accessToken: String,
                    contentId: Uuid,
                    media: MediaFileSource,
                ): Result<MediaUploadResult> = error("Retained originals are already uploaded")

                override suspend fun downloadMedia(
                    accessToken: String,
                    mediaId: String,
                ): Result<MediaFile> {
                    downloads += mediaId
                    return if (failDownload) {
                        Result.failure(IllegalStateException("offline fixture"))
                    } else {
                        Result.success(MediaFile(Uuid.parse(mediaId), "$mediaId.jpg", "image/jpeg", 1, byteArrayOf(1)))
                    }
                }
            }
        val manager =
            testDefaultSyncManager(
                sessionStorage = sessions,
                syncMetadataService = fakeSyncMetadataService(sessions),
                journalNotesRepository = notes,
                mediaManager = media,
                mediaSyncRefStore = refs,
                cloudMediaDataSource = cloud,
                downloadInbox = inbox,
                transactionManager = database,
                firstSyncEnqueueStore = firstSync,
                dataUsagePolicy =
                    object : DataUsagePolicy by fakeDataUsagePolicy() {
                        override val policy = mode

                        override suspend fun currentMode(): DataUsageMode = mode.value
                    },
                syncScope = background,
            )

        suspend fun populate(
            count: Int,
            previouslyFailed: Boolean = false,
        ) {
            EntityType.entries.forEach { firstSync.markEnqueuedLocalScope(scope.ownerId, scope.serverOrigin, it) }
            firstSync.markEnqueuedAssociationScope(scope.ownerId, scope.serverOrigin)
            firstSync.markAuditedLegacyScope(scope.ownerId, scope.serverOrigin)
            val date = Instant.fromEpochMilliseconds(123)
            repeat(count) {
                val id = Uuid.random()
                val local = "file:///previous-install/$id.jpg"
                notes.create(JournalNote.Image(id, date, date, local))
                refs.upsert(MediaSyncRef(id.toString(), local, "${scope.serverOrigin}/media/$id", id.toString(), 1))
                if (previouslyFailed) {
                    inbox.enqueueNativeMedia(
                        "MEDIA_NATIVE_NOTE",
                        id.toString(),
                        123,
                        local,
                        DownloadScope(scope.ownerId, scope.serverOrigin),
                    )
                    inbox.failed("MEDIA_NATIVE_NOTE", id.toString(), "OFFLINE")
                }
            }
        }
    }
}
