package app.logdate.client.sync.location

import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.networking.LocationHistoryApiClientContract
import app.logdate.client.networking.LocationHistoryTransportException
import app.logdate.client.repository.location.HistoryRecord
import app.logdate.client.repository.location.HistoryRecordStore
import app.logdate.client.sync.SyncPausedReason
import app.logdate.client.sync.cloud.InMemorySecureStorage
import app.logdate.client.sync.cloud.TestCryptoManager
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.client.sync.metadata.InMemoryIdentityRecoveryNeededStore
import app.logdate.client.sync.test.FakeSessionStorage
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.sync.LocationHistoryChangesResponse
import app.logdate.shared.model.sync.LocationHistoryRecord
import app.logdate.shared.model.sync.LocationHistoryUpload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocationHistorySyncEngineTest {
    @Test
    fun `upload encrypts and download decrypts a complete account scoped page`() =
        runTest {
            val fixture = fixture()
            fixture.store.items += HistoryRecord("one", "observation", "private coordinates", "device")
            assertTrue(fixture.engine.upload("test-access-token").success)
            val upload = fixture.api.uploads.single()
            assertTrue(upload.payload!!.startsWith("LDSE2:"))
            assertFalse(upload.payload!!.contains("private coordinates"))
            assertFalse(
                fixture.store.items
                    .single()
                    .dirty,
            )
            fixture.store.items.clear()
            fixture.api.page = LocationHistoryChangesResponse(listOf(upload.toServerRecord()), 1, false)
            assertTrue(fixture.engine.download("test-access-token").success)
            assertEquals(
                "private coordinates",
                fixture.store.items
                    .single()
                    .payload,
            )
            assertEquals(1L, fixture.store.savedCursor)
            assertEquals("test-account-id", fixture.store.lastOwner)
        }

    @Test
    fun `unreadable page never advances cursor or applies partial records`() =
        runTest {
            val fixture = fixture()
            fixture.api.page =
                LocationHistoryChangesResponse(
                    listOf(
                        LocationHistoryRecord(
                            "bad",
                            "observation",
                            "LDSE2:corrupt",
                            1,
                            "device",
                            1,
                            5,
                            false,
                        ),
                    ),
                    5,
                    false,
                )
            assertFalse(fixture.engine.download("test-access-token").success)
            assertEquals(0L, fixture.store.savedCursor)
            assertTrue(fixture.store.items.isEmpty())
        }

    @Test
    fun `sign out during upload leaves pending record unacknowledged`() =
        runTest {
            val fixture = fixture()
            fixture.store.items += HistoryRecord("one", "observation", "private", "device")
            fixture.api.afterUpload = { fixture.sessions.clearSession() }
            assertFalse(fixture.engine.upload("test-access-token").success)
            assertTrue(
                fixture.store.items
                    .single()
                    .dirty,
            )
        }

    @Test
    fun `disabled feature sends nothing`() =
        runTest {
            val fixture = fixture(enabled = false)
            fixture.store.items += HistoryRecord("one", "observation", "private", "device")
            assertTrue(fixture.engine.upload("test-access-token").success)
            assertTrue(fixture.api.uploads.isEmpty())
        }

    @Test
    fun `token from another session cannot upload current account records`() =
        runTest {
            val fixture = fixture()
            fixture.store.items += HistoryRecord("one", "observation", "private", "device")
            assertFalse(fixture.engine.upload("other-session-token").success)
            assertTrue(fixture.api.uploads.isEmpty())
            assertTrue(
                fixture.store.items
                    .single()
                    .dirty,
            )
        }

    @Test
    fun `bounded upload signals another pass until the durable queue drains`() =
        runTest {
            val fixture = fixture()
            fixture.store.items += (1..1001).map { HistoryRecord("id-$it", "observation", "private", "device") }
            val first = fixture.engine.upload("test-access-token")
            assertTrue(first.success)
            assertTrue(first.hasMorePending)
            assertEquals(1000, first.uploadedItems)
            val second = fixture.engine.upload("test-access-token")
            assertTrue(second.success)
            assertFalse(second.hasMorePending)
            assertEquals(1, second.uploadedItems)
            assertEquals(0, fixture.engine.upload("test-access-token").uploadedItems)
        }

    @Test
    fun `server switch during upload preserves the old origin queue`() =
        runTest {
            val fixture = fixture()
            fixture.store.items += HistoryRecord("one", "observation", "private", "device")
            fixture.api.afterUpload = { fixture.config.updateBackendUrl("https://other.test") }
            assertFalse(fixture.engine.upload("test-access-token").success)
            assertTrue(
                fixture.store.items
                    .single()
                    .dirty,
            )
            assertEquals(listOf("https://example.test"), fixture.api.origins)
        }

    @Test
    fun `history-only account requires recovery even when history feature is disabled`() =
        runTest {
            val fixture = fixture(enabled = false, createIdentity = false)
            fixture.api.page =
                LocationHistoryChangesResponse(
                    listOf(
                        LocationHistoryRecord(
                            "existing",
                            "observation",
                            "LDSE2:existing-ciphertext",
                            1,
                            "other-device",
                            1,
                            1,
                            false,
                        ),
                    ),
                    1,
                    false,
                )
            val recoveryStore = InMemoryIdentityRecoveryNeededStore()
            val manager =
                testDefaultSyncManager(
                    identityKeyManager = fixture.identity,
                    cloudApiClient = fakeCloudApiClient(),
                    sessionStorage = fixture.sessions,
                    identityRecoveryNeededStore = recoveryStore,
                    locationHistorySyncEngine = fixture.engine,
                )

            manager.fullSync()

            assertFalse(fixture.identity.hasIdentityKey(), "Remote history must prevent minting an unrelated identity")
            assertTrue(recoveryStore.isNeeded())
            assertEquals(SyncPausedReason.NEEDS_RECOVERY_PHRASE, manager.getSyncStatus().pausedReason)
            assertTrue(fixture.api.uploads.isEmpty())
        }

    @Test
    fun `recovery probe allows empty account on servers without history endpoint`() =
        runTest {
            for (status in listOf(404, 405, 501)) {
                val fixture = fixture()
                fixture.api.changesError = LocationHistoryTransportException(status)
                assertFalse(fixture.engine.hasRemoteRecords("test-access-token"))
            }
        }

    @Test
    fun `recovery probe preserves unknown account state on transient or authentication errors`() =
        runTest {
            for (status in listOf(401, 403, 429, 500, 503)) {
                val fixture = fixture(enabled = false)
                fixture.api.changesError = LocationHistoryTransportException(status)
                assertFailsWith<LocationHistoryTransportException> {
                    fixture.engine.hasRemoteRecords("test-access-token")
                }
            }
        }

    private suspend fun fixture(
        enabled: Boolean = true,
        createIdentity: Boolean = true,
    ): Fixture {
        val crypto = TestCryptoManager()
        val identity = IdentityKeyManager(InMemorySecureStorage(), crypto)
        if (createIdentity) identity.setupNewIdentity()
        val cipher = SyncPayloadCipher(ContentEncryptionService(identity, KeyDerivation(crypto), crypto), identity, crypto)
        val store = MemoryHistoryStore()
        val api = HistoryApiFake()
        val sessions = FakeSessionStorage()
        val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://example.test")
        val engine =
            LocationHistorySyncEngine(
                { scoped ->
                    api.origins += scoped.getCurrentBackendUrl()
                    api
                },
                store,
                cipher,
                sessions,
                config,
                { enabled },
            )
        return Fixture(store, api, sessions, engine, config, identity)
    }
}

private data class Fixture(
    val store: MemoryHistoryStore,
    val api: HistoryApiFake,
    val sessions: FakeSessionStorage,
    val engine: LocationHistorySyncEngine,
    val config: DefaultLogDateConfigRepository,
    val identity: IdentityKeyManager,
)

private class HistoryApiFake : LocationHistoryApiClientContract {
    val uploads = mutableListOf<LocationHistoryUpload>()
    var page = LocationHistoryChangesResponse(emptyList(), 0, false)
    val origins = mutableListOf<String>()
    var afterUpload: suspend () -> Unit = {}
    var changesError: Exception? = null

    override suspend fun upload(
        accessToken: String,
        records: List<LocationHistoryUpload>,
    ): List<LocationHistoryRecord> {
        uploads += records
        afterUpload()
        return records.map { it.toServerRecord() }
    }

    override suspend fun changes(
        accessToken: String,
        since: Long,
        limit: Int,
    ): LocationHistoryChangesResponse {
        changesError?.let { throw it }
        return page
    }
}

private fun LocationHistoryUpload.toServerRecord() =
    LocationHistoryRecord(
        id,
        recordType,
        payload,
        payloadSchemaVersion,
        deviceId,
        deviceVersion,
        1,
        deleted,
    )

internal class MemoryHistoryStore : HistoryRecordStore {
    val items = mutableListOf<HistoryRecord>()
    var savedCursor = 0L
    var lastOwner = ""

    override fun observe(
        ownerId: String,
        origin: String,
    ): Flow<List<HistoryRecord>> = flowOf(items)

    override suspend fun records(
        ownerId: String,
        origin: String,
    ) = items.toList()

    override suspend fun put(
        ownerId: String,
        origin: String,
        record: HistoryRecord,
    ) {
        val index = items.indexOfFirst { it.id == record.id }
        if (index < 0) items += record else items[index] = record
    }

    override suspend fun pending(
        ownerId: String,
        origin: String,
        limit: Int,
    ) = items.filter { it.dirty }.take(limit)

    override suspend fun cursor(
        ownerId: String,
        origin: String,
    ) = savedCursor

    override suspend fun applyPage(
        ownerId: String,
        origin: String,
        records: List<HistoryRecord>,
        cursor: Long,
    ) {
        lastOwner = ownerId
        items += records
        savedCursor = cursor
    }

    override suspend fun acknowledge(
        ownerId: String,
        origin: String,
        id: String,
        deviceVersion: Long,
        serverVersion: Long,
    ) {
        lastOwner = ownerId
        val index = items.indexOfFirst { it.id == id && it.deviceVersion == deviceVersion }
        if (index >= 0) items[index] = items[index].copy(serverVersion = serverVersion, dirty = false)
    }
}
