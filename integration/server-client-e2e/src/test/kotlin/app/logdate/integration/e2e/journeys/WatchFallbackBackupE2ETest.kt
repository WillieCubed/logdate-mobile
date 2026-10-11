@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.integration.e2e.journeys

import androidx.room.Room
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.networking.DefaultServerDiscoveryClient
import app.logdate.client.networking.DeviceLinkApiClient
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.sync.CreateCollision
import app.logdate.client.sync.CreateCollisionPolicy
import app.logdate.client.sync.DefaultSyncManager
import app.logdate.client.sync.RoomSyncTransactionManager
import app.logdate.client.sync.cloud.CloudRequestLocation
import app.logdate.client.sync.cloud.CloudRequestLocationProvider
import app.logdate.client.sync.cloud.ConditionalCreateResult
import app.logdate.client.sync.cloud.ContentState
import app.logdate.client.sync.cloud.ContentUploadRequest
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.LogDateCloudApiClient
import app.logdate.client.sync.metadata.DatabaseSyncMetadataService
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncRetryScheduleStore
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.client.sync.recovery.DurableCloudApiClient
import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import app.logdate.integration.e2e.harness.ServerClientE2EHarness
import app.logdate.integration.e2e.harness.withServerClientHarness
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.CloudAccountRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * The watch's fallback backup against the real server: when a linked watch and its phone both upload
 * the same capture, in either order, the server ends up with exactly one version of it; an edit the phone made
 * after receiving the capture goes up as one edit of that version.
 */
class WatchFallbackBackupE2ETest {
    @Test
    fun `a capture the watch backed up first leaves exactly one server version`() =
        runTest {
            withServerClientHarness {
                val pair = linkWatch("fallback_once")
                val note = capture("said on the watch")
                val watchCreate = pair.client.createContentIfAbsent(pair.watchToken, request(note)).getOrThrow()
                val created = assertIs<ConditionalCreateResult.Created>(watchCreate)

                val received = note.lastUpdated.toEpochMilliseconds()
                withPhoneEngine(pair, note, receivedVersion = received, scope = backgroundScope) { manager, metadata ->
                    val result = manager.uploadPendingChanges()

                    assertTrue(result.success, result.errors.toString())
                    assertTrue(metadata.getPendingUploads(EntityType.NOTE).isEmpty())
                }

                assertEquals(listOf(created.serverVersion), serverVersions(pair, note.uid))
                val state = pair.client.contentState(pair.phoneToken, note.uid.toString()).getOrThrow()
                assertEquals(ContentState.Present(created.serverVersion), state)
            }
        }

    @Test
    fun `a capture the phone backed up first is settled by the watch's strict create`() =
        runTest {
            withServerClientHarness {
                val pair = linkWatch("fallback_phone_first")
                val note = capture("said on the watch")
                withPhoneEngine(pair, note, note.lastUpdated.toEpochMilliseconds(), backgroundScope) { manager, metadata ->
                    val result = manager.uploadPendingChanges()
                    assertTrue(result.success, result.errors.toString())
                    assertEquals(1, result.uploadedItems)
                    assertTrue(metadata.getPendingUploads(EntityType.NOTE).isEmpty())
                }
                val uploaded = assertIs<ContentState.Present>(pair.client.contentState(pair.phoneToken, note.uid.toString()).getOrThrow())

                val again = pair.client.createContentIfAbsent(pair.watchToken, request(note)).getOrThrow()

                assertEquals(ConditionalCreateResult.AlreadyExists(uploaded.serverVersion), again)
                assertEquals(listOf(uploaded.serverVersion), serverVersions(pair, note.uid))
            }
        }

    @Test
    fun `an edit the phone made after the watch's backup goes up as one edit`() =
        runTest {
            withServerClientHarness {
                val pair = linkWatch("fallback_edit")
                val original = capture("said on the watch")
                val edited =
                    original.copy(content = "said on the watch and fixed on the phone", lastUpdated = original.lastUpdated + 1.seconds)
                val watchCreate = pair.client.createContentIfAbsent(pair.watchToken, request(original)).getOrThrow()
                val created = assertIs<ConditionalCreateResult.Created>(watchCreate)

                val received = original.lastUpdated.toEpochMilliseconds()
                withPhoneEngine(pair, edited, receivedVersion = received, scope = backgroundScope) { manager, metadata ->
                    val first = manager.uploadPendingChanges()
                    assertTrue(first.success, first.errors.toString())
                    assertEquals(PendingOperation.UPDATE, metadata.getPendingUploads(EntityType.NOTE).single().operation)

                    val second = manager.uploadPendingChanges()
                    assertTrue(second.success, second.errors.toString())
                    assertTrue(metadata.getPendingUploads(EntityType.NOTE).isEmpty())
                }

                val change =
                    pair.client
                        .getContentChanges(pair.phoneToken, 0L)
                        .getOrThrow()
                        .changes
                        .single { it.id == original.uid.toString() }
                assertEquals(edited.content, change.content)
                assertTrue(change.serverVersion > created.serverVersion)
            }
        }

    @Test
    fun `a deleted watch capture is never recreated by the late phone upload`() =
        runTest {
            withServerClientHarness {
                val pair = linkWatch("fallback_deleted")
                val note = capture("deleted after watch backup")
                pair.client.createContentIfAbsent(pair.watchToken, request(note)).getOrThrow()
                pair.client.deleteContent(pair.phoneToken, note.uid.toString()).getOrThrow()
                withPhoneEngine(pair, note, note.lastUpdated.toEpochMilliseconds(), backgroundScope) { manager, metadata ->
                    val result = manager.uploadPendingChanges()
                    assertTrue(result.success, result.errors.toString())
                    assertTrue(metadata.getPendingUploads(EntityType.NOTE).isEmpty())
                }
                assertIs<ContentState.Deleted>(pair.client.contentState(pair.phoneToken, note.uid.toString()).getOrThrow())
            }
        }

    private class LinkedPair(
        val origin: String,
        val config: DefaultLogDateConfigRepository,
        val client: LogDateCloudApiClient,
        val phoneAccountId: String,
        val phoneToken: String,
        val phoneRefreshToken: String,
        val watchToken: String,
    )

    private suspend fun ServerClientE2EHarness.linkWatch(prefix: String): LinkedPair {
        val origin = baseUrl.removeSuffix("/api/v1")
        val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
        val descriptor = DefaultServerDiscoveryClient(httpClient).discoverServer(origin).getOrThrow()
        config.updateServerDescriptor(descriptor.copy(serverOrigin = origin))
        val phone = apiClient.createAccountWithSyntheticPasskey("${prefix}_${Random.nextInt(100000, 999999)}").data
        val links = DeviceLinkApiClient(httpClient, config)
        val watch = links.redeem(links.mintLinkCode(phone.tokens.accessToken).getOrThrow().code, "Pixel Watch 3").getOrThrow()
        return LinkedPair(
            origin = origin,
            config = config,
            client = LogDateCloudApiClient(config, httpClient),
            phoneAccountId = phone.account.id.toString(),
            phoneToken = phone.tokens.accessToken,
            phoneRefreshToken = phone.tokens.refreshToken,
            watchToken = watch.tokens.accessToken,
        )
    }

    /** The phone's real upload pass over a Room outbox with [note] queued as a create it received from the watch. */
    private suspend fun ServerClientE2EHarness.withPhoneEngine(
        pair: LinkedPair,
        note: JournalNote.Text,
        receivedVersion: Long,
        scope: CoroutineScope,
        block: suspend (DefaultSyncManager, DatabaseSyncMetadataService) -> Unit,
    ) {
        val file = Files.createTempFile("watch-fallback-backup", ".db")
        val database = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(file.toString()))
        try {
            val owner = pair.phoneAccountId
            val phoneOrigin = pair.origin
            val ownerProvider = mockk<CanonicalOwnerProvider> { coEvery { getCanonicalOwnerId() } returns owner }
            val metadata = DatabaseSyncMetadataService(database.syncMetadataDao(), pair.config, ownerProvider)
            val transactions = RoomSyncTransactionManager(database)
            val inbox =
                DownloadInbox(
                    database.downloadInboxDao(),
                    transactions,
                    { DownloadScope(owner, phoneOrigin) },
                    { System.currentTimeMillis() },
                )
            val notes =
                mockk<SyncableJournalNotesRepository>(relaxed = true) {
                    every { allNotesObserved } returns flowOf(listOf(note))
                    coEvery { getNoteById(note.uid) } returns note
                }
            metadata.enqueuePending(note.uid.toString(), EntityType.NOTE, PendingOperation.CREATE)
            val session = UserSession(pair.phoneToken, pair.phoneRefreshToken, owner)
            val sessions =
                mockk<SessionStorage> {
                    every { getSession() } returns session
                    every { getSessionFlow() } returns flowOf(session)
                    every { getOriginBoundSession() } returns OriginBoundSession(phoneOrigin, session)
                }
            val accounts =
                object : CloudAccountRepository by mockk(relaxed = true), CloudRequestLocationProvider {
                    override fun captureLocation() = CloudRequestLocation(phoneOrigin, baseUrl)

                    override fun isCurrentOrigin(origin: String) = origin == phoneOrigin
                }
            val retrySchedule =
                mockk<SyncRetryScheduleStore>(relaxed = true) {
                    coEvery { nextAttemptAt(any(), any()) } returns null
                }
            val manager =
                DefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(DurableCloudApiClient(pair.client, inbox)),
                    cloudJournalDataSource = mockk(relaxed = true),
                    cloudAssociationDataSource = mockk(relaxed = true),
                    cloudMediaDataSource = mockk(relaxed = true),
                    cloudDraftDataSource = mockk(relaxed = true),
                    cloudAccountRepository = accounts,
                    sessionStorage = sessions,
                    mediaManager = mockk(relaxed = true),
                    mediaSyncRefStore = mockk(relaxed = true),
                    journalRepository = mockk(relaxed = true),
                    journalNotesRepository = notes,
                    journalContentRepository = mockk(relaxed = true),
                    journalConflictResolver = mockk(relaxed = true),
                    noteConflictResolver = mockk(relaxed = true),
                    conflictStore = mockk(relaxed = true),
                    deadLetterStore = mockk(relaxed = true),
                    retryScheduleStore = retrySchedule,
                    syncMetadataService = metadata,
                    transactionManager = transactions,
                    dataUsagePolicy = mockk(relaxed = true),
                    cloudApiClient = pair.client,
                    downloadInbox = inbox,
                    syncScope = scope,
                    createCollisionPolicy = CreateCollisionPolicy { _, _ -> CreateCollision.CreatedElsewhere(receivedVersion) },
                )
            block(manager, metadata)
        } finally {
            database.close()
            Files.deleteIfExists(file)
            Files.deleteIfExists(file.resolveSibling("${file.fileName}-wal"))
            Files.deleteIfExists(file.resolveSibling("${file.fileName}-shm"))
        }
    }

    private suspend fun serverVersions(
        pair: LinkedPair,
        id: Uuid,
    ): List<Long> =
        pair.client
            .getContentChanges(pair.phoneToken, 0L)
            .getOrThrow()
            .changes
            .filter { it.id == id.toString() }
            .map { it.serverVersion }

    private fun capture(text: String): JournalNote.Text {
        val now = Clock.System.now()
        return JournalNote.Text(uid = Uuid.random(), creationTimestamp = now, lastUpdated = now, content = text)
    }

    private fun request(note: JournalNote.Text) =
        ContentUploadRequest(
            id = note.uid.toString(),
            type = "TEXT",
            content = note.content,
            mediaUri = null,
            createdAt = note.creationTimestamp.toEpochMilliseconds(),
            lastUpdated = note.lastUpdated.toEpochMilliseconds(),
        )
}
