package app.logdate.client.sync.recovery

import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.mediaRefOrNull
import app.logdate.client.sync.ChangesPage
import app.logdate.client.sync.DownloadStrategy
import app.logdate.client.sync.SyncDownloadEngine
import app.logdate.client.sync.SyncMediaTransfer
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.CloudMediaDataSource
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.cloud.MediaDownloadResponse
import app.logdate.client.sync.cloud.MediaFile
import app.logdate.client.sync.diagnostics.DiagnosticSource
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.InMemoryMediaSyncRefStore
import app.logdate.client.sync.test.InMemorySyncConflictStore
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.lastWriteWinsResolver
import app.logdate.shared.model.Journal
import app.logdate.shared.model.diagnostics.DiagnosticAction
import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import app.logdate.shared.model.diagnostics.DiagnosticReason
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import app.logdate.shared.model.sync.ContentChange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class DownloadDiagnosticInstrumentationTest {
    @Test
    fun `page and retry events keep durable operation IDs but use fresh attempt IDs`() =
        runTest {
            val db = DownloadInboxTest.Database()
            val source = DiagnosticSource(UploadScope("owner", "origin"), "epoch-one")
            val observed = mutableListOf<Pair<SyncDiagnosticEvent, DiagnosticSource?>>()
            val selected = DownloadScope("owner", "origin")
            val first = DownloadInbox(db, db, { selected }, { 1_000L }, { source }, { event, captured -> observed += event to captured })

            first.stage(
                "NOTE",
                2,
                listOf(WireDownload("first", 1, false, "ciphertext"), WireDownload("second", 2, false, "ciphertext")),
            )
            val rows = db.rows.associateBy { it.entityId }
            val persisted = observed.filter { it.first.phase == DiagnosticPhase.PERSIST && it.first.outcome == DiagnosticOutcome.SUCCEEDED }
            assertEquals(rows.values.map { it.operationId }.toSet(), persisted.mapNotNull { it.first.operationId }.toSet())
            assertEquals(2, persisted.size)
            assertTrue(persisted.all { it.first.cursorAdvanced && it.second === source })

            first.failed("NOTE", "first", DiagnosticReason.LOCAL_STORAGE.name)
            val restart =
                DownloadInbox(db, db, { selected }, { 100_000L }, { source }, { event, captured -> observed += event to captured })
            restart.failed("NOTE", "first", DiagnosticReason.LOCAL_STORAGE.name)
            val retries =
                observed.filter {
                    it.first.phase == DiagnosticPhase.APPLY && it.first.outcome == DiagnosticOutcome.RETRY_SCHEDULED
                }
            assertEquals(2, retries.size)
            assertTrue(retries.all { it.first.operationId == rows.getValue("first").operationId })
            assertTrue(retries.all { it.first.reason == DiagnosticReason.LOCAL_STORAGE && it.first.action == DiagnosticAction.RETRY })
            assertTrue(retries.all { it.second === source })
            assertNotNull(retries[0].first.attemptId)
            assertNotNull(retries[1].first.attemptId)
            assertTrue(retries[0].first.attemptId != retries[1].first.attemptId)
        }

    @Test
    fun `rollback never reports persisted page and a throwing diagnostic sink cannot break recovery`() =
        runTest {
            val db = DownloadInboxTest.Database()
            val observed = mutableListOf<SyncDiagnosticEvent>()
            val selected = DownloadScope("owner", "origin")
            val inbox = DownloadInbox(db, db, { selected }, { 1L }, diagnostics = { event, _ -> observed += event })
            db.failCheckpoint = true
            assertFailsWith<IllegalStateException> {
                inbox.stage("NOTE", 2, listOf(WireDownload("record", 2, false, "ciphertext")))
            }
            assertTrue(observed.none { it.phase == DiagnosticPhase.PERSIST && it.outcome == DiagnosticOutcome.SUCCEEDED })
            assertEquals(
                DiagnosticReason.PERSISTENCE_FAILED,
                observed.single { it.phase == DiagnosticPhase.PERSIST && it.outcome == DiagnosticOutcome.FAILED }.reason,
            )
            assertEquals(0, db.rows.size)
            assertEquals(0L, inbox.cursor("NOTE"))

            db.failCheckpoint = false
            val throwing = DownloadInbox(db, db, { selected }, { 1L }, diagnostics = { _, _ -> error("diagnostics unavailable") })
            throwing.stage("NOTE", 2, listOf(WireDownload("record", 2, false, "ciphertext")))
            throwing.failed("NOTE", "record", DiagnosticReason.LOCAL_STORAGE.name)
            assertEquals(2L, throwing.cursor("NOTE"))
            assertEquals("FAILED", db.rows.single().state)
        }

    @Test
    fun `record apply is reported only after outer transaction and cancellation interrupts the same operation`() =
        runTest {
            val db = DownloadInboxTest.Database()
            var depth = 0
            val transaction =
                object : SyncTransactionManager {
                    override suspend fun <T> withTransaction(block: suspend () -> T): T {
                        depth++
                        try {
                            return db.withTransaction(block)
                        } finally {
                            depth--
                        }
                    }
                }
            val id = Uuid.random()
            val selected = DownloadScope("owner", "origin")
            val inbox = DownloadInbox(db, transaction, { selected }, { 1L })
            inbox.stage("JOURNAL", 2, listOf(WireDownload(id.toString(), 2, false, "wire")))
            val operation = db.rows.single().operationId
            val source = DiagnosticSource(UploadScope("owner", "origin"), "epoch-one")
            val observed = mutableListOf<Pair<SyncDiagnosticEvent, Int>>()
            val local = mutableMapOf<Uuid, Journal>()
            var cancel = false
            val engine =
                SyncDownloadEngine(
                    transaction,
                    fakeSyncMetadataService(),
                    InMemorySyncConflictStore(),
                    { _: CloudApiException -> SyncResult(false) },
                    { _, _ -> SyncResult(false) },
                    downloadInbox = inbox,
                    diagnosticSource = { source },
                    diagnostics = { event, captured ->
                        assertTrue(captured === source)
                        observed += event to depth
                    },
                )
            var remote = Journal(id = id, title = "remote", syncVersion = 2)

            fun strategy() =
                DownloadStrategy(
                    entityType = EntityType.JOURNAL,
                    logLabel = "journal",
                    fetchChanges = {
                        _,
                        _,
                        ->
                        Result.success(ChangesPage(listOf(remote), emptyList(), Instant.fromEpochMilliseconds(2), false))
                    },
                    localItems = { local.toMap() },
                    idOf = { it.id },
                    syncVersionOf = { it.syncVersion },
                    lastUpdatedOf = { it.lastUpdated },
                    conflictResolver = lastWriteWinsResolver<Journal>(),
                    applyCreate = { if (cancel) throw CancellationException("stop") else local[it.id] = it },
                    applyReplace = { _, item -> local[item.id] = item },
                    applyDelete = { local.remove(it) },
                )
            assertTrue(engine.download(strategy(), "token", Instant.fromEpochMilliseconds(0)).success)
            val applied = observed.single { it.first.phase == DiagnosticPhase.APPLY && it.first.outcome == DiagnosticOutcome.SUCCEEDED }
            assertEquals(operation, applied.first.operationId)
            assertNotNull(applied.first.attemptId)
            assertEquals(0, applied.second, "record success was emitted inside the outer transaction")

            local.clear()
            inbox.stage("JOURNAL", 3, listOf(WireDownload(id.toString(), 3, false, "new wire")))
            remote = remote.copy(syncVersion = 3)
            val replacementOperation = db.rows.single().operationId
            cancel = true
            assertFailsWith<CancellationException> { engine.download(strategy(), "token", Instant.fromEpochMilliseconds(0)) }
            val interrupted =
                observed.single {
                    it.first.phase == DiagnosticPhase.APPLY && it.first.outcome == DiagnosticOutcome.INTERRUPTED
                }
            assertEquals(replacementOperation, interrupted.first.operationId)
            assertNotNull(interrupted.first.attemptId)
            assertEquals(0, interrupted.second)
        }

    @Test
    fun `media retry and later success correlate to the durable operation without exposing its URL`() =
        runTest {
            val db = DownloadInboxTest.Database()
            var now = 1L
            val selected = DownloadScope("owner", "origin")
            val source = DiagnosticSource(UploadScope("owner", "origin"), "epoch-one")
            val afterOptIn = DiagnosticSource(UploadScope("owner", "origin"), "epoch-two")
            var currentSource = source
            val observed = mutableListOf<Pair<SyncDiagnosticEvent, DiagnosticSource?>>()
            val id = Uuid.random()
            val remoteRef = "https://example.invalid/private-media-id"
            val inbox =
                DownloadInbox(db, db, { selected }, { now }, { currentSource }, { event, captured ->
                    observed += event to captured
                })
            val note = JournalNote.Image(id, Instant.fromEpochMilliseconds(1), Instant.fromEpochMilliseconds(1), remoteRef, syncVersion = 4)
            val notes = FakeJournalNotesRepository()
            notes.createFromSync(note)
            inbox.stage(
                "NOTE",
                4,
                listOf(
                    WireDownload(
                        id.toString(),
                        4,
                        false,
                        Json.encodeToString(
                            ContentChange(id.toString(), "IMAGE", mediaUri = remoteRef, createdAt = 1, lastUpdated = 1, serverVersion = 4),
                        ),
                    ),
                ),
            )
            inbox.applied("NOTE", id.toString(), 4)
            val operation = db.rows.single { it.entityType == "MEDIA_NOTE" }.operationId
            assertTrue(operation != db.rows.single { it.entityType == "NOTE" }.operationId)
            val api =
                fakeCloudApiClient {
                    downloadMediaResponse = Result.failure(CloudApiException("MISSING", "remote URL must stay private", 404))
                }
            val refs = InMemoryMediaSyncRefStore()
            val media = InMemoryMediaManager()
            val delegate = DefaultCloudMediaDataSource(api)
            val changingSource =
                object : CloudMediaDataSource by delegate {
                    override suspend fun downloadMedia(
                        accessToken: String,
                        mediaId: String,
                    ): Result<MediaFile> {
                        currentSource = afterOptIn
                        return delegate.downloadMedia(accessToken, mediaId)
                    }
                }
            val transfer = SyncMediaTransfer(media, refs, changingSource)
            SyncMediaRecovery(inbox, notes, transfer, refs, db).recover("token")
            val retry = observed.single { it.first.phase == DiagnosticPhase.MEDIA && it.first.outcome == DiagnosticOutcome.RETRY_SCHEDULED }
            assertTrue(retry.second === source, "retry was attributed to a consent epoch entered during transfer")
            assertEquals(operation, retry.first.operationId)
            assertEquals(DiagnosticReason.MISSING_MEDIA, retry.first.reason)
            assertNotNull(retry.first.attemptId)
            assertTrue(Json.encodeToString(retry.first).contains("private-media-id").not())

            now += 10_000
            api.downloadMediaResponse =
                Result.success(MediaDownloadResponse(id.toString(), "image.bin", "image/png", 3, byteArrayOf(1, 2, 3), remoteRef))
            val restarted =
                DownloadInbox(db, db, { selected }, { now }, { currentSource }, { event, captured -> observed += event to captured })
            SyncMediaRecovery(restarted, notes, transfer, refs, db).recover("token")
            val success = observed.single { it.first.phase == DiagnosticPhase.MEDIA && it.first.outcome == DiagnosticOutcome.SUCCEEDED }
            assertTrue(success.second === afterOptIn)
            assertEquals(operation, success.first.operationId)
            assertNotNull(success.first.attemptId)
            assertTrue(success.first.attemptId != retry.first.attemptId)
            assertTrue(
                notes
                    .getNoteById(id)
                    ?.mediaRefOrNull()
                    .orEmpty()
                    .startsWith("file://"),
            )
        }
}
