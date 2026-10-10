package app.logdate.client.sync

import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadInboxTest
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.client.sync.recovery.WireDownload
import app.logdate.client.sync.test.InMemorySyncConflictStore
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.lastWriteWinsResolver
import app.logdate.client.sync.test.testSyncTransactionManager
import app.logdate.shared.model.Journal
import app.logdate.shared.model.sync.ContentChange
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A linked watch keeps only a window of the account. A record outside it that only the server has is
 * skipped, never fetched again, while the download still moves past it and anything already on the
 * device stays current.
 */
class WindowedDownloadTest {
    @Test
    fun `a remote record outside the window is skipped and the cursor moves past it`() =
        runTest {
            val metadata = fakeSyncMetadataService()
            val inside = journal(version = 2)
            val outside = journal(version = 3)
            val created = mutableListOf<Journal>()
            val engine = downloadEngine(metadata)

            val result =
                engine.download(
                    strategy(
                        page = ChangesPage(listOf(inside, outside), emptyList(), Instant.fromEpochMilliseconds(50), hasMore = false),
                        local = emptyMap(),
                        created = created,
                        appliesRemoteCreate = { it.id == inside.id },
                    ),
                    "token",
                    Instant.fromEpochMilliseconds(0),
                )

            assertTrue(result.success)
            assertEquals(1, result.downloadedItems)
            assertEquals(listOf(inside), created)
            assertEquals(Instant.fromEpochMilliseconds(50), metadata.getLastSyncTime(EntityType.JOURNAL))
        }

    @Test
    fun `a record already on this device stays current outside the window`() =
        runTest {
            val local = journal(version = 1)
            val remote = local.copy(title = "edited elsewhere", syncVersion = 4, lastUpdated = Instant.fromEpochMilliseconds(9))
            val replaced = mutableListOf<Journal>()
            val engine = downloadEngine()

            engine.download(
                strategy(
                    page = ChangesPage(listOf(remote), emptyList(), Instant.fromEpochMilliseconds(50), hasMore = false),
                    local = mapOf(local.id to local),
                    replaced = replaced,
                    appliesRemoteCreate = { false },
                ),
                "token",
                Instant.fromEpochMilliseconds(0),
            )

            assertEquals(listOf(remote), replaced)
        }

    @Test
    fun `an inbox record outside the window is settled without queueing its media`() =
        runTest {
            val id = Uuid.random()
            val database = DownloadInboxTest.Database()
            val inbox = stagedImage(database, id)
            val engine = downloadEngine(database = database, inbox = inbox)

            val result =
                engine.download(
                    strategy(
                        page =
                            ChangesPage(
                                listOf(journal(version = 20, id = id)),
                                emptyList(),
                                Instant.fromEpochMilliseconds(20),
                                hasMore = false,
                            ),
                        local = emptyMap(),
                        appliesRemoteCreate = { false },
                        entityType = EntityType.NOTE,
                    ),
                    "token",
                    Instant.fromEpochMilliseconds(0),
                )

            assertTrue(result.success)
            assertEquals(0, inbox.count())
        }

    @Test
    fun `media recovery can be left out for applied records`() =
        runTest {
            val id = Uuid.random()
            val database = DownloadInboxTest.Database()
            val inbox = stagedImage(database, id)
            val created = mutableListOf<Journal>()
            val engine = downloadEngine(database = database, inbox = inbox)

            engine.download(
                strategy(
                    page =
                        ChangesPage(
                            listOf(journal(version = 20, id = id)),
                            emptyList(),
                            Instant.fromEpochMilliseconds(20),
                            hasMore = false,
                        ),
                    local = emptyMap(),
                    created = created,
                    entityType = EntityType.NOTE,
                    enqueuesMediaRecovery = false,
                ),
                "token",
                Instant.fromEpochMilliseconds(0),
            )

            assertEquals(1, created.size)
            assertEquals(0, inbox.count())
        }

    private fun downloadEngine(
        metadata: SyncMetadataService = fakeSyncMetadataService(),
        database: DownloadInboxTest.Database? = null,
        inbox: DownloadInbox? = null,
    ) = SyncDownloadEngine(
        transactionManager = database ?: testSyncTransactionManager(),
        syncMetadataService = metadata,
        conflictStore = InMemorySyncConflictStore(),
        mapCloudApiError = { SyncResult(false) },
        mapException = { _, _ -> SyncResult(false) },
        downloadInbox = inbox,
    )

    private suspend fun stagedImage(
        database: DownloadInboxTest.Database,
        id: Uuid,
    ): DownloadInbox {
        val inbox = DownloadInbox(database, database, { DownloadScope("owner", "server") }, { 1L })
        val wire =
            ContentChange(
                id.toString(),
                "IMAGE",
                mediaUri = "https://example.invalid/media/file",
                createdAt = 1,
                lastUpdated = 1,
                serverVersion = 20,
            )
        inbox.stage("NOTE", 20, listOf(WireDownload(id.toString(), 20, false, Json.encodeToString(ContentChange.serializer(), wire))))
        return inbox
    }

    private fun strategy(
        page: ChangesPage<Journal>,
        local: Map<Uuid, Journal>,
        created: MutableList<Journal> = mutableListOf(),
        replaced: MutableList<Journal> = mutableListOf(),
        appliesRemoteCreate: (Journal) -> Boolean = { true },
        entityType: EntityType = EntityType.JOURNAL,
        enqueuesMediaRecovery: Boolean = true,
    ) = DownloadStrategy(
        entityType = entityType,
        logLabel = "journal",
        fetchChanges = { _, _ -> Result.success(page) },
        localItems = { local },
        idOf = { it.id },
        syncVersionOf = { it.syncVersion },
        lastUpdatedOf = { it.lastUpdated },
        conflictResolver = lastWriteWinsResolver(),
        applyCreate = { created += it },
        applyReplace = { _, replacement -> replaced += replacement },
        applyDelete = { error("Unexpected deletion") },
        appliesRemoteCreate = appliesRemoteCreate,
        enqueuesMediaRecovery = enqueuesMediaRecovery,
    )

    private fun journal(
        version: Long,
        id: Uuid = Uuid.random(),
    ) = Journal(
        id = id,
        title = "journal",
        created = Instant.fromEpochMilliseconds(1),
        lastUpdated = Instant.fromEpochMilliseconds(1),
        syncVersion = version,
    )
}
