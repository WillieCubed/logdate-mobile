@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.integration.e2e.journeys

import androidx.room.Room
import app.logdate.client.data.journals.OfflineFirstJournalRepository
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.SyncableJournalRepository
import app.logdate.client.sync.DefaultSyncManager
import app.logdate.client.sync.RoomSyncTransactionManager
import app.logdate.client.sync.cloud.CloudContentDataSource
import app.logdate.client.sync.cloud.CloudRequestLocation
import app.logdate.client.sync.cloud.CloudRequestLocationProvider
import app.logdate.client.sync.cloud.ContentSyncResult
import app.logdate.client.sync.cloud.DefaultCloudAssociationDataSource
import app.logdate.client.sync.conflict.ConflictResolver
import app.logdate.client.sync.conflict.LastWriteWinsResolver
import app.logdate.client.sync.metadata.DatabaseSyncMetadataService
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.SyncRetryScheduleStore
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.client.sync.recovery.DurableCloudApiClient
import app.logdate.client.sync.space.RoomSpaceSwapStore
import app.logdate.client.sync.space.SpaceKind
import app.logdate.client.sync.space.SpaceLocalRecords
import app.logdate.client.sync.space.SpaceRecordMapper
import app.logdate.client.sync.space.SpaceRecordRegistry
import app.logdate.client.sync.space.SpaceSyncDependencies
import app.logdate.client.sync.space.SpaceTransportGate
import app.logdate.client.sync.space.XrpcSpaceDataHomes
import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import app.logdate.integration.e2e.harness.ServerClientE2EHarness
import app.logdate.integration.e2e.harness.withServerClientHarness
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.AccountStorageMode
import app.logdate.shared.model.CloudAccountRepository
import app.logdate.shared.model.Journal
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Two devices of one account sync journals through its sealed personal space on a real server,
 * with real Room databases and the real sync engine. The journal mapping is a minimal one written
 * for this test; the real per-kind mappings are their own work.
 */
class PersonalSpaceSyncE2ETest {
    @Test
    fun `journals created, edited, raced and deleted on one device reach the other through the personal space`() =
        runTest {
            withServerClientHarness {
                val account = signUp()
                val phone = Device(this, account, backgroundScope)
                val tablet = Device(this, account, backgroundScope)
                try {
                    val journal = Journal(title = "Lake trips", description = "Every swim this summer")
                    phone.journals.create(journal)
                    val uploaded = phone.manager.uploadPendingChanges()
                    assertTrue(uploaded.success, uploaded.errors.joinToString { it.cause?.stackTraceToString().orEmpty() })

                    val stored = rawChanges(account)
                    assertFalse("Lake trips" in stored, "The server received the journal's title")
                    assertFalse(JOURNAL in stored, "The server received the record's type")

                    val firstDownload = tablet.manager.downloadRemoteChanges()
                    assertTrue(firstDownload.success, firstDownload.errors.toString())
                    assertEquals("Lake trips", tablet.journals.getJournalById(journal.id)?.title)
                    assertEquals("Every swim this summer", tablet.journals.getJournalById(journal.id)?.description)

                    val renamed = tablet.journals.getJournalById(journal.id)!!.copy(title = "Lake and river trips", lastUpdated = later(1))
                    tablet.journals.update(renamed)
                    assertTrue(tablet.manager.uploadPendingChanges().success)
                    assertTrue(phone.manager.downloadRemoteChanges().success)
                    assertEquals("Lake and river trips", phone.journals.getJournalById(journal.id)?.title)

                    // Both edit before either downloads the other's change: the newer edit wins on both.
                    tablet.journals.update(
                        tablet.journals.getJournalById(journal.id)!!.copy(title = "Tablet's name", lastUpdated = later(2)),
                    )
                    assertTrue(tablet.manager.uploadPendingChanges().success)
                    phone.journals.update(phone.journals.getJournalById(journal.id)!!.copy(title = "Phone's name", lastUpdated = later(3)))
                    val raced = phone.manager.uploadPendingChanges()
                    assertTrue(raced.success, raced.errors.toString())
                    assertTrue(tablet.manager.downloadRemoteChanges().success)
                    assertEquals("Phone's name", tablet.journals.getJournalById(journal.id)?.title)

                    phone.journals.delete(journal.id)
                    assertTrue(phone.manager.uploadPendingChanges().success)
                    assertTrue(tablet.manager.downloadRemoteChanges().success)
                    assertNull(tablet.journals.getJournalById(journal.id))
                    assertEquals(0, tablet.inbox.count())
                    assertTrue(phone.metadata.getPendingUploads(EntityType.JOURNAL).isEmpty())
                } finally {
                    phone.close()
                    tablet.close()
                }
            }
        }

    private class Account(
        val owner: String,
        val did: String,
        val token: String,
        val refreshToken: String,
        val origin: String,
        val apiBaseUrl: String,
    )

    private suspend fun ServerClientE2EHarness.signUp(): Account {
        val created = apiClient.createAccountWithSyntheticPasskey("spacesync_${Random.nextInt(100000, 999999)}").data
        return Account(
            owner = created.account.id.toString(),
            did = requireNotNull(created.account.did) { "The new account has no DID" },
            token = created.tokens.accessToken,
            refreshToken = created.tokens.refreshToken,
            origin = baseUrl.removeSuffix("/api/v1"),
            apiBaseUrl = baseUrl,
        )
    }

    private suspend fun ServerClientE2EHarness.rawChanges(account: Account): String =
        httpClient
            .get("${account.origin}/xrpc/app.logdate.space.listRepoOps") {
                parameter("space", "at://${account.did}/space/app.logdate.space.personal/self")
                parameter("since", "0")
                header(HttpHeaders.Authorization, "Bearer ${account.token}")
            }.bodyAsText()

    /** One device: its own database, outbox, inbox and sync engine, signed in to [account]. */
    private class Device(
        harness: ServerClientE2EHarness,
        account: Account,
        scope: CoroutineScope,
    ) : AutoCloseable {
        private val file: Path = Files.createTempFile("personal-space-device", ".db")
        val database = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(file.toString()))
        private val config = DefaultLogDateConfigRepository(initialBackendUrl = account.origin)
        private val ownerProvider = mockk<CanonicalOwnerProvider> { coEvery { getCanonicalOwnerId() } returns account.owner }
        val metadata = DatabaseSyncMetadataService(database.syncMetadataDao(), config, ownerProvider)
        private val transactions = RoomSyncTransactionManager(database)
        val inbox =
            DownloadInbox(
                database.downloadInboxDao(),
                transactions,
                { DownloadScope(account.owner, account.origin) },
                { System.currentTimeMillis() },
            )
        val journals =
            OfflineFirstJournalRepository(
                journalDao = database.journalDao(),
                remoteDataSource = mockk(relaxed = true),
                draftRepository = mockk(relaxed = true),
                syncMetadataService = metadata,
                database = database,
                currentScope = { JournalMergeScope(account.owner, account.origin) },
                mergeTransactionManager = transactions,
                externalScope = scope,
            )

        private val session = UserSession(account.token, account.refreshToken, account.owner)
        private val sessions =
            mockk<SessionStorage> {
                every { getSession() } returns session
                every { getSessionFlow() } returns kotlinx.coroutines.flow.flowOf(session)
                every { getOriginBoundSession() } returns OriginBoundSession(account.origin, session)
            }
        private val accounts =
            object : CloudAccountRepository by mockk(relaxed = true), CloudRequestLocationProvider {
                override fun captureLocation() = CloudRequestLocation(account.origin, account.apiBaseUrl)

                override fun isCurrentOrigin(origin: String) = origin == account.origin
            }
        private val resolver = LastWriteWinsResolver<Journal>()
        private val noNotes = mockk<JournalNotesRepository>(relaxed = true) { every { allNotesObserved } returns flowOf(emptyList()) }
        private val noContent =
            mockk<CloudContentDataSource> {
                coEvery { getContentChanges(any(), any(), any()) } returns
                    Result.success(ContentSyncResult(emptyList(), emptyList(), Instant.fromEpochMilliseconds(0)))
            }

        val manager =
            DefaultSyncManager(
                // Notes and memberships stay on their own endpoints in this test, and the account has none.
                cloudContentDataSource = noContent,
                cloudJournalDataSource = mockk(relaxed = true),
                cloudAssociationDataSource = DefaultCloudAssociationDataSource(DurableCloudApiClient(harness.apiClient, inbox)),
                cloudMediaDataSource = mockk(relaxed = true),
                cloudDraftDataSource = mockk(relaxed = true),
                cloudAccountRepository = accounts,
                sessionStorage = sessions,
                mediaManager = mockk(relaxed = true),
                mediaSyncRefStore = mockk(relaxed = true),
                journalRepository = journals,
                journalNotesRepository = noNotes,
                journalContentRepository = mockk(relaxed = true),
                journalConflictResolver = resolver,
                noteConflictResolver = mockk(relaxed = true),
                conflictStore = mockk(relaxed = true),
                deadLetterStore = mockk(relaxed = true),
                retryScheduleStore = mockk<SyncRetryScheduleStore>(relaxed = true) { coEvery { nextAttemptAt(any(), any()) } returns null },
                syncMetadataService = metadata,
                transactionManager = transactions,
                dataUsagePolicy = mockk(relaxed = true),
                downloadInbox = inbox,
                syncScope = scope,
                spaceSync =
                    SpaceSyncDependencies(
                        registry =
                            SpaceRecordRegistry(
                                listOf(
                                    SpaceKind.single(
                                        EntityType.JOURNAL,
                                        TestJournalMapper,
                                        LocalJournals(journals, resolver),
                                    ) { journal, version ->
                                        journal.copy(syncVersion = version)
                                    },
                                ),
                            ),
                        gate = SpaceTransportGate({ true }, { true }, { AccountStorageMode.PERSONAL_SPACE_V1 }),
                        homes = XrpcSpaceDataHomes(harness.httpClient),
                        swapStore = RoomSpaceSwapStore(database.spaceRecordStateDao()),
                        accountDid = { account.did },
                        identityKey = { IDENTITY_KEY },
                    ),
            )

        override fun close() {
            database.close()
            Files.deleteIfExists(file)
            Files.deleteIfExists(file.resolveSibling("${file.fileName}-wal"))
            Files.deleteIfExists(file.resolveSibling("${file.fileName}-shm"))
        }
    }

    /** A minimal journal mapping, written for this test: title, description and timestamps. */
    private object TestJournalMapper : SpaceRecordMapper<Journal> {
        override val collection = JOURNAL

        override fun rkeyOf(value: Journal) = value.id.toString()

        override fun toRecord(value: Journal): JsonObject =
            buildJsonObject {
                put("\$type", JOURNAL)
                put("title", value.title)
                if (value.description.isNotEmpty()) put("description", value.description)
                put("createdAt", value.created.toString())
                put("updatedAt", value.lastUpdated.toString())
            }

        override fun fromRecord(
            rkey: String,
            record: JsonObject,
        ) = Journal(
            id = Uuid.parse(rkey),
            title = record.getValue("title").jsonPrimitive.content,
            description = record["description"]?.jsonPrimitive?.content.orEmpty(),
            created = Instant.parse(record.getValue("createdAt").jsonPrimitive.content),
            lastUpdated = Instant.parse(record.getValue("updatedAt").jsonPrimitive.content),
        )
    }

    private class LocalJournals(
        private val journals: OfflineFirstJournalRepository,
        override val conflictResolver: ConflictResolver<Journal>,
    ) : SpaceLocalRecords<Journal> {
        private val syncable: SyncableJournalRepository = journals

        override suspend fun load(entityId: String) = journals.getJournalById(Uuid.parse(entityId))

        override fun lastUpdatedOf(item: Journal) = item.lastUpdated

        override suspend fun acknowledgeUpload(
            item: Journal,
            seq: Long,
            syncedAt: Instant,
        ) = syncable.updateSyncMetadata(item.id, seq, syncedAt)

        override suspend fun replaceLocal(item: Journal) {
            if (journals.getJournalById(item.id) == null) syncable.createFromSync(item) else syncable.updateFromSync(item)
        }
    }

    private companion object {
        const val JOURNAL = "app.logdate.journal.journal"
        val IDENTITY_KEY = ByteArray(32) { (it * 5).toByte() }
        private val start = Clock.System.now()

        fun later(minutes: Int): Instant = start + minutes.minutes
    }
}
