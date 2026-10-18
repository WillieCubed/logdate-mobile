package app.logdate.client.sync.moments

import app.logdate.client.repository.moments.MarkState
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.sync.MomentChangesResponse
import app.logdate.shared.model.sync.MomentDeletion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MomentDownloaderTest {
    @Test fun `full manager routes moment downloads and includes their count and applied timestamp`() =
        runTest {
            val f = MomentDownloadFixture()
            val mark = f.mark()
            val checkpoint = Instant.parse("2100-01-01T00:00:00Z")
            f.page = MomentChangesResponse(listOf(f.wire(mark)), emptyList(), checkpoint.toEpochMilliseconds())
            val manager =
                testDefaultSyncManager(
                    sessionStorage = f.sessions,
                    cloudAccountRepository = f.accounts,
                    syncMetadataService = f.metadata,
                    transactionManager = f.db,
                    downloadInbox = f.inbox,
                    momentSyncConfig = f.config(),
                    syncScope = backgroundScope,
                )
            val result = manager.downloadRemoteChanges()
            assertTrue(result.success)
            assertEquals(mark, f.repository.getMark(mark.id))
            assertEquals(1, result.downloadedItems)
            assertEquals(checkpoint, f.metadata.getLastSyncTime(EntityType.MOMENT))
            assertEquals(checkpoint, result.lastSyncTime)
        }

    @Test fun `paged download uses durable cursors and merges both pages without echoing remote state`() =
        runTest {
            val f = MomentDownloadFixture()
            val first = f.mark()
            val second = f.mark(8)
            val one = f.wire(first)
            val two = f.wire(second)
            f.onFetch =
                { _, since ->
                    Result.success(
                        if (since ==
                            0L
                        ) {
                            MomentChangesResponse(listOf(one), emptyList(), 7, true)
                        } else {
                            MomentChangesResponse(listOf(two), emptyList(), 8)
                        },
                    )
                }
            val result = f.downloader().download()
            assertTrue(result.success)
            assertEquals(2, result.downloadedItems)
            assertEquals(listOf(0L, 7L), f.calls.map { it.second })
            assertEquals(listOf<Int?>(25, 25), f.limits)
            assertTrue(f.metadata.getPendingUploads(EntityType.MOMENT).isEmpty())
            assertTrue(f.inbox.pending("MOMENT").isEmpty())
            assertEquals(8L, f.inbox.cursor("MOMENT"))
            assertEquals(Instant.fromEpochMilliseconds(8), result.lastSyncTime)
        }

    @Test fun `pending local resolution still joins remote note attachments`() =
        runTest {
            val f = MomentDownloadFixture()
            val remote = f.mark().copy(state = MarkState.FILLED, resolvedNoteIds = setOf(Uuid.random()))
            val localNote = Uuid.random()
            f.repository.rows[remote.id] = remote.copy(syncVersion = 6, resolvedNoteIds = setOf(localNote))
            f.metadata.enqueuePending(remote.id.toString(), EntityType.MOMENT, PendingOperation.UPDATE)
            val pending = f.metadata.getPendingUploads(EntityType.MOMENT).single()
            f.page = MomentChangesResponse(listOf(f.wire(remote)), emptyList(), 7)
            assertTrue(f.downloader().download().success)
            assertEquals(remote.resolvedNoteIds + localNote, f.repository.getMark(remote.id)!!.resolvedNoteIds)
            assertEquals(pending, f.metadata.getPendingUploads(EntityType.MOMENT).single())
        }

    @Test fun `deletion uses the server tombstone clock instead of the fetched checkpoint`() =
        runTest {
            val f = MomentDownloadFixture()
            val id = Uuid.random()
            f.page = MomentChangesResponse(emptyList(), listOf(MomentDeletion(id.toString(), 1234, 8)), 9999)
            val result = f.downloader().download()
            assertTrue(result.success)
            assertEquals(1, result.downloadedItems)
            assertEquals(Instant.fromEpochMilliseconds(1234) to 8L, f.repository.deleted[id])
            assertTrue(f.inbox.pending("MOMENT").isEmpty())
        }

    @Test fun `disabled or unsupported moments leave retained records and queued edits untouched`() =
        runTest {
            val f = MomentDownloadFixture()
            val mark = f.mark()
            f.page = MomentChangesResponse(listOf(f.wire(mark)), emptyList(), 7)
            app.logdate.client.sync.recovery
                .DurableCloudApiClient(f.api, f.inbox)
                .getMomentChanges("token", 0)
                .getOrThrow()
            f.calls.clear()
            f.metadata.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.CREATE)
            val pending = f.metadata.getPendingUploads(EntityType.MOMENT).single()
            f.enabled = false
            val result = f.downloader().download()
            assertTrue(result.success)
            assertTrue(f.calls.isEmpty())
            assertEquals(1, f.inbox.pending("MOMENT").size)
            assertEquals(pending, f.metadata.getPendingUploads(EntityType.MOMENT).single())
            assertEquals(null, f.repository.getMark(mark.id))
        }

    @Test fun `mixed unreadable unsupported and corrupt records remain encrypted while a good mark applies`() =
        runTest {
            val f = MomentDownloadFixture()
            val good = f.mark()
            val wrong = f.mark()
            val future = f.mark()
            val broken = f.mark()
            f.page =
                MomentChangesResponse(
                    listOf(
                        f.wire(good),
                        f.wire(wrong, f.codec("foreign")),
                        f.wire(future).copy(schema = 2),
                        f.wire(broken).copy(payload = "LDSE2:corrupt"),
                    ),
                    emptyList(),
                    7,
                )
            val result = f.downloader().download()
            assertFalse(result.success)
            assertEquals(1, result.downloadedItems)
            assertTrue(result.errors.any { it.type == app.logdate.client.sync.SyncErrorType.KEY_RECOVERY_ERROR })
            assertEquals(good, f.repository.getMark(good.id))
            assertEquals(1, f.unreadable.count())
            assertEquals(null, f.metadata.getLastSyncTime(EntityType.MOMENT))
            assertEquals(7L, f.inbox.cursor("MOMENT"))
            for ((id, reason) in listOf(
                wrong.id to "KEY_RECOVERY_REQUIRED",
                future.id to "UNSUPPORTED_FORMAT",
                broken.id to "CORRUPT_PAYLOAD",
            )) {
                val row = f.db.get(f.scope.owner, f.scope.origin, "MOMENT", id.toString())!!
                assertEquals("FAILED", row.state)
                assertEquals(reason, row.reason)
                assertTrue(row.payload.contains("LDSE2:"))
            }
        }

    @Test fun `offline replay applies saved work but does not report a successful fresh download`() =
        runTest {
            val f = MomentDownloadFixture()
            val mark = f.mark()
            f.page = MomentChangesResponse(listOf(f.wire(mark)), emptyList(), 7)
            app.logdate.client.sync.recovery
                .DurableCloudApiClient(f.api, f.inbox)
                .getMomentChanges("token", 0)
                .getOrThrow()
            f.onFetch = { _, _ -> Result.failure(CloudApiException("NETWORK_ERROR", "Offline")) }
            val result = f.downloader().download()
            assertFalse(result.success)
            assertEquals(1, result.downloadedItems)
            assertEquals(mark, f.repository.getMark(mark.id))
            assertEquals(null, f.metadata.getLastSyncTime(EntityType.MOMENT))
        }

    @Test fun `expired bound token refreshes before cached records are applied`() =
        runTest {
            val f = MomentDownloadFixture()
            val mark = f.mark()
            f.page = MomentChangesResponse(listOf(f.wire(mark)), emptyList(), 7)
            f.onFetch =
                { token, _ ->
                    if (token ==
                        "new-token"
                    ) {
                        Result.success(f.page)
                    } else {
                        Result.failure(CloudApiException("EXPIRED", "Expired", 401))
                    }
                }
            assertTrue(f.downloader().download().success)
            assertEquals(listOf("test-access-token", "new-token"), f.calls.map { it.first })
            assertEquals(mark, f.repository.getMark(mark.id))
        }

    @Test fun `revocation refuses cached replay and retains every encrypted record`() =
        runTest {
            val f = MomentDownloadFixture()
            val mark = f.mark()
            f.page = MomentChangesResponse(listOf(f.wire(mark)), emptyList(), 7)
            app.logdate.client.sync.recovery
                .DurableCloudApiClient(f.api, f.inbox)
                .getMomentChanges("token", 0)
                .getOrThrow()
            f.onFetch = { _, _ -> Result.failure(CloudApiException("DEVICE_REVOKED", "Refused", 403)) }
            assertFalse(f.downloader().download().success)
            assertEquals(null, f.repository.getMark(mark.id))
            assertEquals(1, f.inbox.pending("MOMENT").size)
        }

    @Test fun `account switch during fetch prevents application and scope checkpoint contamination`() =
        runTest {
            val f = MomentDownloadFixture()
            val mark = f.mark()
            val previous = f.scope
            f.page = MomentChangesResponse(listOf(f.wire(mark)), emptyList(), 7)
            f.onFetch = { _, _ ->
                f.sessions.saveSession(f.sessions.getSession()!!.copy(accountId = "other-owner"))
                Result.success(f.page)
            }
            assertFalse(f.downloader().download().success)
            assertEquals(null, f.repository.getMark(mark.id))
            assertEquals(0L, f.inbox.cursor("MOMENT", previous))
            assertEquals(0L, f.inbox.cursor("MOMENT"))
        }

    @Test fun `cancelled repository application keeps the encrypted row and propagates cancellation`() =
        runTest {
            val f = MomentDownloadFixture()
            val mark = f.mark()
            f.page = MomentChangesResponse(listOf(f.wire(mark)), emptyList(), 7)
            f.repository.beforeApply = { throw CancellationException("interrupted") }
            assertFailsWith<CancellationException> { f.downloader().download() }
            assertEquals(
                "WAITING",
                f.inbox
                    .pending("MOMENT")
                    .single()
                    .state,
            )
            assertEquals(null, f.metadata.getLastSyncTime(EntityType.MOMENT))
        }

    @Test fun `a nonadvancing server page fails without an unbounded loop`() =
        runTest {
            val f = MomentDownloadFixture()
            f.page = MomentChangesResponse(emptyList(), emptyList(), 0, true)
            val result = f.downloader().download()
            assertFalse(result.success)
            assertEquals(1, f.calls.size)
            assertEquals(null, f.metadata.getLastSyncTime(EntityType.MOMENT))
        }

    @Test fun `a long backlog yields another bounded pass after twenty pages`() =
        runTest {
            val f = MomentDownloadFixture()
            f.onFetch = { _, since -> Result.success(MomentChangesResponse(emptyList(), emptyList(), since + 1, true)) }
            val result = f.downloader().download()
            assertTrue(result.success)
            assertTrue(result.hasMorePending)
            assertEquals(20, f.calls.size)
            assertEquals(20L, f.inbox.cursor("MOMENT"))
        }

    @Test fun `rollout disabled during fetch holds the staged record without applying it`() =
        runTest {
            val f = MomentDownloadFixture()
            val mark = f.mark()
            f.page = MomentChangesResponse(listOf(f.wire(mark)), emptyList(), 7)
            f.onFetch = { _, _ ->
                f.enabled = false
                Result.success(f.page)
            }
            assertTrue(f.downloader().download().success)
            assertEquals(null, f.repository.getMark(mark.id))
            assertEquals(1, f.inbox.pending("MOMENT").size)
            assertEquals(null, f.metadata.getLastSyncTime(EntityType.MOMENT))
        }

    @Test fun `incompatible capture identity remains a conflict without hiding the good mark`() =
        runTest {
            val f = MomentDownloadFixture()
            val local = f.mark()
            val good = f.mark()
            f.repository.rows[local.id] = local
            f.page = MomentChangesResponse(listOf(f.wire(local.copy(originDeviceId = "different-watch")), f.wire(good)), emptyList(), 7)
            val result = f.downloader().download()
            assertFalse(result.success)
            assertEquals(1, result.downloadedItems)
            assertEquals(local, f.repository.getMark(local.id))
            assertFalse(f.inbox.isAppliedVersion("MOMENT", local.id.toString(), 7))
            assertEquals(null, f.metadata.getLastSyncTime(EntityType.MOMENT))
        }

    @Test fun `malformed durable wire remains failed and cannot produce a successful applied checkpoint`() =
        runTest {
            val f = MomentDownloadFixture()
            f.inbox.stage(
                "MOMENT",
                7,
                listOf(
                    app.logdate.client.sync.recovery
                        .WireDownload("broken-wire", 7, false, "private-malformed-wire"),
                ),
            )
            val result = f.downloader().download()
            assertFalse(result.success)
            assertEquals(null, f.metadata.getLastSyncTime(EntityType.MOMENT))
            assertEquals("FAILED", f.db.get(f.scope.owner, f.scope.origin, "MOMENT", "broken-wire")!!.state)
            assertEquals("private-malformed-wire", f.db.get(f.scope.owner, f.scope.origin, "MOMENT", "broken-wire")!!.payload)
        }

    @Test fun `a backoff held unreadable record prevents later empty pages claiming successful application`() =
        runTest {
            val f = MomentDownloadFixture()
            val mark = f.mark()
            f.page = MomentChangesResponse(listOf(f.wire(mark, f.codec("foreign"))), emptyList(), 7)
            assertFalse(f.downloader().download().success)
            f.page = MomentChangesResponse(emptyList(), emptyList(), 8)
            assertFalse(f.downloader().download().success)
            assertEquals(null, f.metadata.getLastSyncTime(EntityType.MOMENT))
            assertEquals(8L, f.inbox.cursor("MOMENT"))
            assertEquals(1, f.unreadable.count())
        }

    @Test fun `a corrected newer payload clears its unreadable warning only after applying`() =
        runTest {
            val f = MomentDownloadFixture()
            val mark = f.mark()
            f.page = MomentChangesResponse(listOf(f.wire(mark, f.codec("foreign"))), emptyList(), 7)
            assertFalse(f.downloader().download().success)
            assertEquals(1, f.unreadable.count())
            f.page = MomentChangesResponse(listOf(f.wire(mark.copy(syncVersion = 8))), emptyList(), 8)
            assertTrue(f.downloader().download().success)
            assertEquals(0, f.unreadable.count())
            assertEquals(mark.copy(syncVersion = 8), f.repository.getMark(mark.id))
        }
}
