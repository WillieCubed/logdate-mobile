@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.integration.e2e.journeys

import androidx.room.Room
import app.logdate.client.data.moments.OfflineFirstMarkedMomentRepository
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.DesktopCryptoManager
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.networking.DefaultServerDiscoveryClient
import app.logdate.client.networking.DeviceLinkApiClient
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.repository.moments.atMillisecondPrecision
import app.logdate.client.sync.DefaultSyncManager
import app.logdate.client.sync.RoomSyncTransactionManager
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.cloud.CloudRequestLocation
import app.logdate.client.sync.cloud.CloudRequestLocationProvider
import app.logdate.client.sync.cloud.DefaultCloudAssociationDataSource
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.DefaultCloudDraftDataSource
import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.cloud.DefaultCloudMomentDataSource
import app.logdate.client.sync.cloud.LogDateCloudApiClient
import app.logdate.client.sync.crypto.MarkedMomentPayloadCodec
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.client.sync.metadata.DatabaseSyncMetadataService
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.moments.MomentSyncConfig
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.client.sync.recovery.DurableCloudApiClient
import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import app.logdate.integration.e2e.harness.withServerClientHarness
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.CloudAccountRepository
import app.logdate.shared.model.sync.DeviceId
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MarkedMomentRoomDownloadE2ETest {
    @Test
    fun `full sync runner recovers retained ciphertext after Room restart and joins local resolution before server deletion`() =
        runTest {
            withServerClientHarness {
                val account = apiClient.createAccountWithSyntheticPasskey("markroom_${Uuid.random().toString().take(8)}").data
                val token = account.tokens.accessToken
                val owner = account.account.id.toString()
                val origin = baseUrl.removeSuffix("/api/v1")
                val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
                config.updateServerDescriptor(
                    DefaultServerDiscoveryClient(httpClient)
                        .discoverServer(origin)
                        .getOrThrow()
                        .copy(serverOrigin = origin, apiBaseUrl = baseUrl),
                )
                val api = LogDateCloudApiClient(config, httpClient)
                val links = DeviceLinkApiClient(httpClient, config)
                val watchToken =
                    links
                        .redeem(links.mintLinkCode(token).getOrThrow().code, "Room moment watch")
                        .getOrThrow()
                        .tokens.accessToken
                val watchCrypto = DesktopCryptoManager()
                val phoneCrypto = DesktopCryptoManager()
                val watchIdentity = IdentityKeyManager(MomentTestSecrets(), watchCrypto)
                val phoneIdentity = IdentityKeyManager(MomentTestSecrets(), phoneCrypto)
                phoneIdentity.recoverIdentity(watchIdentity.setupNewIdentity().words)

                fun cipher(
                    identity: IdentityKeyManager,
                    crypto: DesktopCryptoManager,
                ) = SyncPayloadCipher(ContentEncryptionService(identity, KeyDerivation(crypto), crypto), identity, crypto)
                val phoneCipher = cipher(phoneIdentity, phoneCrypto)
                val watch = DefaultCloudMomentDataSource(api, MarkedMomentPayloadCodec(cipher(watchIdentity, watchCrypto)))
                val captured = Instant.parse("2026-10-12T12:00:00.123456789Z")
                val mark =
                    MarkedMoment(
                        Uuid.random(),
                        captured,
                        "UTC",
                        null,
                        72,
                        MarkState.OPEN,
                        "Private capture",
                        emptySet(),
                        "test-watch",
                        captured,
                        0,
                    )
                watch.createIfAbsent(watchToken, mark, DeviceId("watch")).getOrThrow()
                val file = Files.createTempFile("logdate-moment-runner", ".db")
                Files.delete(file)

                fun open() = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(file.toString()))
                var database = open()
                val job = SupervisorJob()
                try {
                    val selected = DownloadScope(owner, origin)

                    fun inbox() =
                        DownloadInbox(
                            database.downloadInboxDao(),
                            RoomSyncTransactionManager(database),
                            { selected },
                            { System.currentTimeMillis() },
                        )
                    val staged = inbox()
                    DurableCloudApiClient(api, staged).getMomentChanges(token, 0).getOrThrow()
                    assertTrue(
                        staged
                            .pending("MOMENT")
                            .single()
                            .payload
                            .contains("LDSE2:"),
                    )
                    assertFalse(
                        staged
                            .pending("MOMENT")
                            .single()
                            .payload
                            .contains(mark.caption),
                    )
                    val fetched = staged.cursor("MOMENT")
                    database.close()
                    database = open()
                    val transactions = RoomSyncTransactionManager(database)
                    val canonical = mockk<CanonicalOwnerProvider> { coEvery { getCanonicalOwnerId() } returns owner }
                    val metadata = DatabaseSyncMetadataService(database.syncMetadataDao(), config, canonical)
                    val repository = OfflineFirstMarkedMomentRepository(database.markedMomentDao(), transactions, metadata)
                    val retained = inbox()
                    assertEquals(fetched, retained.cursor("MOMENT"))
                    val durable = DurableCloudApiClient(api, retained)
                    val phone = DefaultCloudMomentDataSource(durable, MarkedMomentPayloadCodec(phoneCipher))
                    val session = UserSession(token, account.tokens.refreshToken, owner)
                    val sessions =
                        mockk<SessionStorage> {
                            every { getSession() } returns session
                            every { getSessionFlow() } returns flowOf(session)
                            every { getOriginBoundSession() } returns OriginBoundSession(origin, session)
                        }
                    val accounts =
                        object : CloudAccountRepository by mockk(relaxed = true), CloudRequestLocationProvider {
                            override fun captureLocation() = CloudRequestLocation(origin, baseUrl)

                            override fun isCurrentOrigin(origin: String) = origin == selected.origin
                        }
                    val journals = mockk<JournalRepository>(relaxed = true) { every { allJournalsObserved } returns flowOf(emptyList()) }
                    val notes = mockk<JournalNotesRepository>(relaxed = true) { every { allNotesObserved } returns flowOf(emptyList()) }
                    var interruptReceipt = false
                    val receiptTransactions =
                        object : SyncTransactionManager {
                            override suspend fun <T> withTransaction(block: suspend () -> T): T =
                                transactions.withTransaction {
                                    val result = block()
                                    if (interruptReceipt) {
                                        interruptReceipt = false
                                        throw CancellationException("Interrupted receipt commit")
                                    }
                                    result
                                }
                        }
                    val device = mockk<DeviceIdProvider> { every { getDeviceId() } returns MutableStateFlow(Uuid.random()) }
                    val manager =
                        DefaultSyncManager(
                            cloudContentDataSource = DefaultCloudContentDataSource(durable, phoneCipher),
                            cloudJournalDataSource = DefaultCloudJournalDataSource(durable, phoneCipher),
                            cloudAssociationDataSource = DefaultCloudAssociationDataSource(durable),
                            cloudMediaDataSource = mockk(relaxed = true),
                            cloudDraftDataSource = DefaultCloudDraftDataSource(durable, phoneCipher),
                            cloudAccountRepository = accounts,
                            sessionStorage = sessions,
                            mediaManager = mockk(relaxed = true),
                            mediaSyncRefStore = mockk(relaxed = true),
                            journalRepository = journals,
                            journalNotesRepository = notes,
                            journalContentRepository = mockk(relaxed = true),
                            journalConflictResolver = mockk(relaxed = true),
                            noteConflictResolver = mockk(relaxed = true),
                            conflictStore = mockk(relaxed = true),
                            deadLetterStore = mockk(relaxed = true),
                            retryScheduleStore = mockk(relaxed = true),
                            syncMetadataService = metadata,
                            transactionManager = receiptTransactions,
                            deviceIdProvider = device,
                            dataUsagePolicy = mockk(relaxed = true),
                            identityKeyManager = phoneIdentity,
                            cloudApiClient = api,
                            downloadInbox = retained,
                            momentSyncConfig = MomentSyncConfig(phone, repository, { true }),
                            syncScope = CoroutineScope(Dispatchers.Default + job),
                        )
                    val recovered = manager.downloadRemoteChanges()
                    assertTrue(recovered.success, recovered.errors.toString())
                    assertEquals(1, recovered.downloadedItems)
                    val remote = phone.get(token, mark.id).getOrThrow()
                    assertEquals(remote.atMillisecondPrecision(), repository.getMark(mark.id))
                    assertTrue(retained.isAppliedVersion("MOMENT", mark.id.toString(), remote.syncVersion))
                    assertTrue(metadata.getPendingUploads(EntityType.MOMENT).isEmpty())
                    val localNote = Uuid.random()
                    val otherNote = Uuid.random()
                    repository.fill(mark.id, "Local resolution", setOf(localNote), captured + 1.seconds)
                    val pending = metadata.getPendingUploads(EntityType.MOMENT).single()
                    phone
                        .update(
                            token,
                            remote.copy(
                                state = MarkState.FILLED,
                                resolvedNoteIds = setOf(otherNote),
                                lastUpdated =
                                    captured + 2.seconds,
                            ),
                            DeviceId("other-phone"),
                        ).getOrThrow()
                    val resolved = manager.downloadRemoteChanges()
                    assertTrue(resolved.success, resolved.errors.toString())
                    assertEquals(1, resolved.downloadedItems)
                    assertEquals(setOf(localNote, otherNote), repository.getMark(mark.id)!!.resolvedNoteIds)
                    assertEquals(MarkState.FILLED, repository.getMark(mark.id)!!.state)
                    assertEquals(pending, metadata.getPendingUploads(EntityType.MOMENT).single())
                    val beforeReceipt = repository.getMark(mark.id)
                    interruptReceipt = true
                    assertFailsWith<CancellationException> { manager.uploadPendingChanges() }
                    assertEquals(
                        beforeReceipt,
                        repository.getMark(mark.id),
                        "Receipt interruption must roll back the stored server version",
                    )
                    assertEquals(
                        pending,
                        metadata.getPendingUploads(EntityType.MOMENT).single(),
                        "Receipt interruption must retain the operation",
                    )
                    val uploaded = manager.uploadPendingChanges()
                    assertTrue(uploaded.success, uploaded.errors.toString())
                    assertEquals(1, uploaded.uploadedItems)
                    assertTrue(metadata.getPendingUploads(EntityType.MOMENT).isEmpty())
                    assertEquals(setOf(localNote, otherNote), phone.get(token, mark.id).getOrThrow().resolvedNoteIds)
                    metadata.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.CREATE)
                    phone.delete(token, mark.id).getOrThrow()
                    assertFalse(
                        manager.uploadPendingChanges().success,
                        "An unversioned deletion collision must stay queued until downloaded",
                    )
                    assertTrue(metadata.hasPending(EntityType.MOMENT, mark.id.toString()))
                    val deletion =
                        api
                            .getMomentChanges(token, 0)
                            .getOrThrow()
                            .deletions
                            .single { it.id == mark.id.toString() }
                    val deleted = manager.downloadRemoteChanges()
                    assertTrue(deleted.success, deleted.errors.toString())
                    assertEquals(1, deleted.downloadedItems)
                    assertEquals(null, repository.getMark(mark.id))
                    assertEquals(Instant.fromEpochMilliseconds(deletion.deletedAt), database.markedMomentDao().getById(mark.id)!!.deletedAt)
                    assertTrue(metadata.hasPending(EntityType.MOMENT, mark.id.toString()))
                    assertTrue(retained.retained("MOMENT").isEmpty())
                    val settledCollision = manager.uploadPendingChanges()
                    assertTrue(settledCollision.success, settledCollision.errors.toString())
                    assertEquals(0, settledCollision.uploadedItems)
                    assertTrue(metadata.getPendingUploads(EntityType.MOMENT).isEmpty())
                    val createdHere = mark.copy(id = Uuid.random(), syncVersion = 0)
                    repository.applyFromSync(createdHere)
                    metadata.enqueuePending(createdHere.id.toString(), EntityType.MOMENT, PendingOperation.CREATE)
                    val createdUpload = manager.uploadPendingChanges()
                    assertTrue(createdUpload.success, createdUpload.errors.toString())
                    assertEquals(1, createdUpload.uploadedItems)
                    assertTrue(repository.getMark(createdHere.id)!!.syncVersion > 0)
                    repository.delete(createdHere.id, captured + 3.seconds)
                    val deletedUpload = manager.uploadPendingChanges()
                    assertTrue(deletedUpload.success, deletedUpload.errors.toString())
                    assertEquals(1, deletedUpload.uploadedItems)
                    assertTrue(metadata.getPendingUploads(EntityType.MOMENT).isEmpty())
                    assertTrue(phone.get(token, createdHere.id).isFailure)
                } finally {
                    job.cancelAndJoin()
                    database.close()
                    Files.deleteIfExists(file)
                    Files.deleteIfExists(file.resolveSibling("${file.fileName}-wal"))
                    Files.deleteIfExists(file.resolveSibling("${file.fileName}-shm"))
                }
            }
        }
}
