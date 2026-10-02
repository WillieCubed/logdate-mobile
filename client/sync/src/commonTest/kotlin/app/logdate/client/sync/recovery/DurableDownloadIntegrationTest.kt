package app.logdate.client.sync.recovery

import app.logdate.client.sync.ChangesPage
import app.logdate.client.sync.DownloadStrategy
import app.logdate.client.sync.SyncDownloadEngine
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.test.InMemorySyncConflictStore
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.lastWriteWinsResolver
import app.logdate.shared.model.Journal
import app.logdate.shared.model.sync.JournalChange
import app.logdate.shared.model.sync.JournalChangesResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class DurableDownloadIntegrationTest {
    @Test
    fun `wrong key repair keeps converted version after a newer inbox version arrives`() =
        runTest {
            val id = Uuid.random()
            val db = DownloadInboxTest.Database()
            val scope = DownloadScope("owner", "origin")
            val inbox = DownloadInbox(db, db, { scope }, { 1L })
            inbox.stage("JOURNAL", 10, listOf(WireDownload(id.toString(), 10, false, "old wire")))
            val observed = mapOf(id to 10L)
            inbox.stage("JOURNAL", 11, listOf(WireDownload(id.toString(), 11, false, "new wire")))
            val metadata = fakeSyncMetadataService()
            val engine =
                SyncDownloadEngine(
                    db,
                    metadata,
                    InMemorySyncConflictStore(),
                    { SyncResult(false) },
                    { _, _ -> SyncResult(false) },
                    downloadInbox = inbox,
                )
            engine.repairUnreadable(EntityType.JOURNAL, "journal", listOf(id), setOf(id), scope, observed)
            assertEquals(10L, metadata.getPendingUploads(EntityType.JOURNAL).single().expectedServerVersion)
            inbox.failed("JOURNAL", id.toString(), "KEY_RECOVERY_REQUIRED", observed.getValue(id), scope)
            val retained = inbox.pending("JOURNAL").single()
            assertEquals(11L, retained.version)
            assertEquals("WAITING", retained.state)
            assertEquals(0, retained.attempts)
        }

    @Test
    fun `unreadable repair preserves a pending local deletion`() =
        runTest {
            val id = Uuid.random()
            val metadata = fakeSyncMetadataService()
            metadata.enqueuePending(id.toString(), EntityType.JOURNAL, app.logdate.client.sync.metadata.PendingOperation.DELETE)
            val engine =
                SyncDownloadEngine(
                    DownloadInboxTest.Database(),
                    metadata,
                    InMemorySyncConflictStore(),
                    { SyncResult(success = false) },
                    { _, _ -> SyncResult(success = false) },
                )
            engine.repairUnreadable(EntityType.JOURNAL, "journal", listOf(id), setOf(id))
            assertEquals(
                app.logdate.client.sync.metadata.PendingOperation.DELETE,
                metadata.getPendingUploads(EntityType.JOURNAL).single().operation,
            )
        }

    @Test
    fun `failed record remains queued while unrelated record applies and restart retries it`() =
        runTest {
            val db = DownloadInboxTest.Database()
            var now = 1000L
            val scope = { DownloadScope("owner", "origin") }
            val inbox = DownloadInbox(db, db, scope, { now })
            val first = Uuid.random()
            val second = Uuid.random()
            val api =
                fakeCloudApiClient {
                    getJournalChangesResponse =
                        Result.success(
                            JournalChangesResponse(
                                listOf(first, second).mapIndexed { index, id ->
                                    JournalChange(id.toString(), "encrypted title", "", 1, 1, index + 1L)
                                },
                                emptyList(),
                                2,
                            ),
                        )
                }
            val local = mutableMapOf<Uuid, Journal>()
            var fail = true

            suspend fun execute(queue: DownloadInbox): SyncResult {
                val source = DefaultCloudJournalDataSource(DurableCloudApiClient(api, queue))
                val engine =
                    SyncDownloadEngine(
                        db,
                        fakeSyncMetadataService(),
                        InMemorySyncConflictStore(),
                        { SyncResult(success = false) },
                        { _, _ -> SyncResult(success = false) },
                        downloadInbox = queue,
                    )
                return engine.download(
                    DownloadStrategy(
                        entityType = EntityType.JOURNAL,
                        logLabel = "journal",
                        fetchChanges = { token, cursor ->
                            source.getJournalChanges(token, cursor).map {
                                ChangesPage(
                                    it.changes,
                                    it.deletions,
                                    it.lastSyncTimestamp,
                                    it.hasMore,
                                    it.unreadable,
                                    it.failures,
                                    it.unreadableVersions,
                                )
                            }
                        },
                        localItems = { local.toMap() },
                        idOf = { it.id },
                        syncVersionOf = { it.syncVersion },
                        lastUpdatedOf = { it.lastUpdated },
                        conflictResolver = lastWriteWinsResolver(),
                        applyCreate = { if (it.id == first && fail) error("storage unavailable") else local[it.id] = it },
                        applyReplace = { _, item -> local[item.id] = item },
                        applyDelete = { local.remove(it) },
                    ),
                    "token",
                    Instant.fromEpochMilliseconds(0),
                )
            }
            assertFalse(execute(inbox).success)
            assertEquals(setOf(second), local.keys)
            assertEquals(1, inbox.count())
            assertEquals(2L, inbox.cursor("JOURNAL"))
            now += 10000
            fail = false
            api.getJournalChangesResponse = Result.success(JournalChangesResponse(emptyList(), emptyList(), 2))
            val restarted = DownloadInbox(db, db, scope, { now })
            assertTrue(execute(restarted).success)
            assertEquals(setOf(first, second), local.keys)
            assertEquals(0, restarted.count())
        }

    @Test
    fun `failed replacement rolls back its deletion`() =
        runTest {
            val id = Uuid.random()
            val original =
                Journal(
                    id = id,
                    title = "local original",
                    created = Instant.fromEpochMilliseconds(1),
                    lastUpdated = Instant.fromEpochMilliseconds(1),
                    syncVersion = 1,
                )
            val local = mutableMapOf(id to original)
            val transactions =
                object : app.logdate.client.sync.SyncTransactionManager {
                    override suspend fun <T> withTransaction(block: suspend () -> T): T {
                        val snapshot = local.toMap()
                        return try {
                            block()
                        } catch (error: Exception) {
                            local.clear()
                            local.putAll(snapshot)
                            throw error
                        }
                    }
                }
            val engine =
                SyncDownloadEngine(
                    transactions,
                    fakeSyncMetadataService(),
                    InMemorySyncConflictStore(),
                    { SyncResult(success = false) },
                    { _, _ -> SyncResult(success = false) },
                )
            val remote = original.copy(title = "remote newer", syncVersion = 2)
            engine.download(
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
                    conflictResolver = lastWriteWinsResolver(),
                    applyCreate = { local[it.id] = it },
                    applyReplace = { old, _ ->
                        local.remove(old.id)
                        error("storage write failed")
                    },
                    applyDelete = { local.remove(it) },
                ),
                "token",
                Instant.fromEpochMilliseconds(0),
            )
            assertEquals(original, local[id])
        }

    @Test
    fun `pending local deletion prevents a retried remote record from resurrecting it`() =
        runTest {
            val id = Uuid.random()
            val local = mutableMapOf<Uuid, Journal>()
            val metadata = fakeSyncMetadataService()
            metadata.enqueuePending(id.toString(), EntityType.JOURNAL, app.logdate.client.sync.metadata.PendingOperation.DELETE)
            val db = DownloadInboxTest.Database()
            val queue = DownloadInbox(db, db, { DownloadScope("a", "server") }, { 1L })
            val remote =
                Journal(
                    id = id,
                    title = "remote",
                    created = Instant.fromEpochMilliseconds(1),
                    lastUpdated = Instant.fromEpochMilliseconds(1),
                    syncVersion = 10,
                )
            queue.stage("JOURNAL", 10, listOf(WireDownload(id.toString(), 10, false, "wire")))
            val engine =
                SyncDownloadEngine(
                    db,
                    metadata,
                    InMemorySyncConflictStore(),
                    { SyncResult(false) },
                    { _, _ -> SyncResult(false) },
                    downloadInbox = queue,
                )
            engine.download(
                DownloadStrategy(
                    entityType = EntityType.JOURNAL,
                    logLabel = "journal",
                    fetchChanges = {
                        _,
                        _,
                        ->
                        Result.success(ChangesPage(listOf(remote), emptyList(), Instant.fromEpochMilliseconds(10), false))
                    },
                    localItems = { local.toMap() },
                    idOf = { it.id },
                    syncVersionOf = { it.syncVersion },
                    lastUpdatedOf = { it.lastUpdated },
                    conflictResolver = lastWriteWinsResolver(),
                    applyCreate = { local[it.id] = it },
                    applyReplace = { _, item -> local[item.id] = item },
                    applyDelete = { local.remove(it) },
                ),
                "token",
                Instant.fromEpochMilliseconds(0),
            )
            assertTrue(local.isEmpty())
            assertEquals(
                app.logdate.client.sync.metadata.PendingOperation.DELETE,
                metadata.getPendingUploads(EntityType.JOURNAL).single().operation,
            )
        }

    @Test
    fun `local deletion during fetch must not be overwritten by the fetched record`() =
        runTest {
            val id = Uuid.random()
            val local = mutableMapOf<Uuid, Journal>()
            val metadata = fakeSyncMetadataService()
            val db = DownloadInboxTest.Database()
            val queue = DownloadInbox(db, db, { DownloadScope("a", "server") }, { 1L })
            val remote =
                Journal(
                    id = id,
                    title = "remote",
                    created = Instant.fromEpochMilliseconds(1),
                    lastUpdated = Instant.fromEpochMilliseconds(1),
                    syncVersion = 10,
                )
            queue.stage("JOURNAL", 10, listOf(WireDownload(id.toString(), 10, false, "wire")))
            val engine =
                SyncDownloadEngine(
                    db,
                    metadata,
                    InMemorySyncConflictStore(),
                    { SyncResult(false) },
                    { _, _ -> SyncResult(false) },
                    downloadInbox = queue,
                )
            engine.download(
                DownloadStrategy(
                    entityType = EntityType.JOURNAL,
                    logLabel = "journal",
                    fetchChanges = { _, _ ->
                        metadata.enqueuePending(id.toString(), EntityType.JOURNAL, app.logdate.client.sync.metadata.PendingOperation.DELETE)
                        Result.success(ChangesPage(listOf(remote), emptyList(), Instant.fromEpochMilliseconds(10), false))
                    },
                    localItems = { local.toMap() },
                    idOf = { it.id },
                    syncVersionOf = { it.syncVersion },
                    lastUpdatedOf = { it.lastUpdated },
                    conflictResolver = lastWriteWinsResolver(),
                    applyCreate = { local[it.id] = it },
                    applyReplace = { _, item -> local[item.id] = item },
                    applyDelete = { local.remove(it) },
                ),
                "token",
                Instant.fromEpochMilliseconds(0),
            )
            assertTrue(local.isEmpty())
            assertEquals(
                app.logdate.client.sync.metadata.PendingOperation.DELETE,
                metadata.getPendingUploads(EntityType.JOURNAL).single().operation,
            )
        }

    @Test
    fun `stale deletion cannot remove a newer locally synchronized version`() =
        runTest {
            val id = Uuid.random()
            val current =
                Journal(
                    id = id,
                    title = "newer",
                    created = Instant.fromEpochMilliseconds(1),
                    lastUpdated = Instant.fromEpochMilliseconds(1),
                    syncVersion = 20,
                )
            val local = mutableMapOf(id to current)
            val db = DownloadInboxTest.Database()
            val queue = DownloadInbox(db, db, { DownloadScope("a", "server") }, { 1L })
            queue.stage("JOURNAL", 10, listOf(WireDownload(id.toString(), 10, true, "wire")))
            val engine =
                SyncDownloadEngine(
                    db,
                    fakeSyncMetadataService(),
                    InMemorySyncConflictStore(),
                    { SyncResult(false) },
                    { _, _ -> SyncResult(false) },
                    downloadInbox = queue,
                )
            engine.download(
                DownloadStrategy(
                    entityType = EntityType.JOURNAL,
                    logLabel = "journal",
                    fetchChanges = {
                        _,
                        _,
                        ->
                        Result.success(ChangesPage(emptyList<Journal>(), listOf(id), Instant.fromEpochMilliseconds(10), false))
                    },
                    localItems = { local.toMap() },
                    idOf = { it.id },
                    syncVersionOf = { it.syncVersion },
                    lastUpdatedOf = { it.lastUpdated },
                    conflictResolver = lastWriteWinsResolver(),
                    applyCreate = { local[it.id] = it },
                    applyReplace = { _, item -> local[item.id] = item },
                    applyDelete = { local.remove(it) },
                ),
                "token",
                Instant.fromEpochMilliseconds(0),
            )
            assertEquals(current, local[id])
        }

    @Test
    fun `replay after metadata commit must still queue missing media`() =
        runTest {
            val id = Uuid.random()
            val current =
                Journal(
                    id = id,
                    title = "recovered metadata",
                    created = Instant.fromEpochMilliseconds(1),
                    lastUpdated = Instant.fromEpochMilliseconds(1),
                    syncVersion = 20,
                )
            val local = mutableMapOf(id to current)
            val db = DownloadInboxTest.Database()
            val queue = DownloadInbox(db, db, { DownloadScope("a", "server") }, { 1L })
            val wire =
                app.logdate.shared.model.sync.ContentChange(
                    id.toString(),
                    "IMAGE",
                    mediaUri = "https://example.invalid/media/file",
                    createdAt = 1,
                    lastUpdated = 1,
                    serverVersion = 20,
                )
            queue.stage(
                "NOTE",
                20,
                listOf(
                    WireDownload(
                        id.toString(),
                        20,
                        false,
                        kotlinx.serialization.json.Json
                            .encodeToString(
                                app.logdate.shared.model.sync.ContentChange
                                    .serializer(),
                                wire,
                            ),
                    ),
                ),
            )
            val engine =
                SyncDownloadEngine(
                    db,
                    fakeSyncMetadataService(),
                    InMemorySyncConflictStore(),
                    { SyncResult(false) },
                    { _, _ -> SyncResult(false) },
                    downloadInbox = queue,
                )
            engine.download(
                DownloadStrategy(
                    entityType = EntityType.NOTE,
                    logLabel = "note",
                    fetchChanges = {
                        _,
                        _,
                        ->
                        Result.success(ChangesPage(listOf(current), emptyList(), Instant.fromEpochMilliseconds(20), false))
                    },
                    localItems = { local.toMap() },
                    idOf = { it.id },
                    syncVersionOf = { it.syncVersion },
                    lastUpdatedOf = { it.lastUpdated },
                    conflictResolver = lastWriteWinsResolver(),
                    applyCreate = { error("Already exists") },
                    applyReplace = { _, _ -> error("Already applied") },
                    applyDelete = { error("Unexpected deletion") },
                ),
                "token",
                Instant.fromEpochMilliseconds(0),
            )
            assertEquals(listOf(id.toString()), queue.pending("MEDIA_NOTE").map { it.entityId })
            assertEquals(current, local[id])
        }

    @Test
    fun `failed inbox acknowledgment rolls back record application`() =
        runTest {
            val id = Uuid.random()
            val remote =
                Journal(
                    id = id,
                    title = "remote",
                    created = Instant.fromEpochMilliseconds(1),
                    lastUpdated = Instant.fromEpochMilliseconds(1),
                    syncVersion = 20,
                )
            val local = mutableMapOf<Uuid, Journal>()
            val db = DownloadInboxTest.Database()
            val dao =
                object : app.logdate.client.database.dao.sync.DownloadInboxDao by db {
                    override suspend fun put(row: app.logdate.client.database.entities.sync.DownloadInboxEntity) {
                        if (row.state == "APPLIED") error("disk unavailable during acknowledgment")
                        db.put(row)
                    }
                }
            val transactions =
                object : app.logdate.client.sync.SyncTransactionManager {
                    override suspend fun <T> withTransaction(block: suspend () -> T): T {
                        val before = local.toMap()
                        return try {
                            db.withTransaction(block)
                        } catch (error: Throwable) {
                            local.clear()
                            local.putAll(before)
                            throw error
                        }
                    }
                }
            val queue = DownloadInbox(dao, transactions, { DownloadScope("a", "server") }, { 1L })
            queue.stage("JOURNAL", 20, listOf(WireDownload(id.toString(), 20, false, "wire")))
            val engine =
                SyncDownloadEngine(
                    transactions,
                    fakeSyncMetadataService(),
                    InMemorySyncConflictStore(),
                    { SyncResult(false) },
                    { _, _ -> SyncResult(false) },
                    downloadInbox = queue,
                )
            engine.download(
                DownloadStrategy(
                    entityType = EntityType.JOURNAL,
                    logLabel = "journal",
                    fetchChanges = {
                        _,
                        _,
                        ->
                        Result.success(ChangesPage(listOf(remote), emptyList(), Instant.fromEpochMilliseconds(20), false))
                    },
                    localItems = { local.toMap() },
                    idOf = { it.id },
                    syncVersionOf = { it.syncVersion },
                    lastUpdatedOf = { it.lastUpdated },
                    conflictResolver = lastWriteWinsResolver(),
                    applyCreate = { local[it.id] = it },
                    applyReplace = { _, item -> local[item.id] = item },
                    applyDelete = { local.remove(it) },
                ),
                "token",
                Instant.fromEpochMilliseconds(0),
            )
            assertTrue(local.isEmpty(), "Domain record and recovery acknowledgment must commit together")
            assertEquals(1, queue.count())
        }
}
