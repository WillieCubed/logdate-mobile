@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.integration.e2e.journeys

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.logdate.client.data.location.RoomHistoryRecordStore
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.DesktopCryptoManager
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.networking.LocationHistoryApiClient
import app.logdate.client.networking.LocationHistoryApiClientContract
import app.logdate.client.repository.location.HistoryRecord
import app.logdate.client.repository.location.HistoryRecordStore
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.client.sync.location.LocationHistorySyncEngine
import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import app.logdate.integration.e2e.harness.withServerClientHarness
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.location.HistoryCorrection
import app.logdate.shared.model.location.HistoryField
import app.logdate.shared.model.location.HistoryPayload
import app.logdate.shared.model.location.LocationObservation
import app.logdate.shared.model.sync.LocationHistoryChangesResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class EncryptedLocationHistoryDevicesE2ETest {
    @Test
    fun `two devices restore real encrypted history after interrupted pages and retain concurrent edits and deletions`() =
        runTest {
            withServerClientHarness {
                val account = apiClient.createAccountWithSyntheticPasskey("cipher_${Random.nextInt(100000, 999999)}").data
                val owner = account.account.id.toString()
                val token = account.tokens.accessToken
                val origin = baseUrl.removeSuffix("/api/v1")
                val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
                val cryptoA = DesktopCryptoManager()
                val cryptoB = DesktopCryptoManager()
                val identityA = IdentityKeyManager(HistoryTestSecrets(), cryptoA)
                val identityB = IdentityKeyManager(HistoryTestSecrets(), cryptoB)
                val recovery = identityA.setupNewIdentity()
                identityB.recoverIdentity(recovery.words)
                val cipherA = cipher(identityA, cryptoA)
                val cipherB = cipher(identityB, cryptoB)
                val directory = Files.createTempDirectory("location-devices-e2e")
                val databaseA = database(directory.resolve("device-a.db"))
                var databaseB = database(directory.resolve("device-b.db"))
                try {
                    val storeA = RoomHistoryRecordStore(databaseA.historyRecordDao())
                    var storeB = RoomHistoryRecordStore(databaseB.historyRecordDao())
                    val transport = LocationHistoryApiClient(httpClient, config)

                    fun engine(
                        store: HistoryRecordStore,
                        cipher: SyncPayloadCipher,
                        overrideApi: LocationHistoryApiClientContract? = null,
                    ) = LocationHistorySyncEngine(
                        { pinned -> overrideApi ?: LocationHistoryApiClient(httpClient, pinned) },
                        store,
                        cipher,
                        HistoryTestSession(UserSession(token, account.tokens.refreshToken, owner)),
                        config,
                        { true },
                    )
                    val engineA = engine(storeA, cipherA)
                    val plaintext =
                        (1..205).associate { index ->
                            val id = "observation:$index"
                            val payload =
                                HistoryPayload.Observation(
                                    LocationObservation(
                                        id,
                                        owner,
                                        "device-a",
                                        Instant.fromEpochMilliseconds(index.toLong()),
                                        36.17,
                                        -115.14,
                                        timeZoneId = "America/Los_Angeles",
                                    ),
                                )
                            id to Json.encodeToString<HistoryPayload>(payload)
                        }
                    plaintext.forEach { (id, value) -> storeA.put(owner, origin, HistoryRecord(id, "observation", value, "device-a")) }
                    assertEquals(205, storeA.pending(owner, origin, 1000).size)
                    assertTrue(engineA.upload(token).success)
                    val wire = transport.changes(token, 0, 100)
                    assertTrue(wire.records.all { it.payload!!.startsWith("LDSE2:") && !it.payload!!.contains("America/Los_Angeles") })
                    assertTrue(wire.records.all { it.payload != plaintext[it.id] })

                    var requests = 0
                    val interruptAfterPage =
                        object : LocationHistoryApiClientContract by transport {
                            override suspend fun changes(
                                accessToken: String,
                                since: Long,
                                limit: Int,
                            ): LocationHistoryChangesResponse {
                                if (++requests == 2) error("Simulated connection loss after first committed page")
                                return transport.changes(accessToken, since, limit)
                            }
                        }
                    assertFalse(engine(storeB, cipherB, interruptAfterPage).download(token).success)
                    assertEquals(100, storeB.records(owner, origin).size)
                    val persistedCursor = storeB.cursor(owner, origin)
                    assertTrue(persistedCursor > 0)
                    databaseB.close()
                    databaseB = database(directory.resolve("device-b.db"))
                    storeB = RoomHistoryRecordStore(databaseB.historyRecordDao())
                    assertEquals(persistedCursor, storeB.cursor(owner, origin))
                    val engineB = engine(storeB, cipherB)
                    assertTrue(engineB.download(token).success)
                    assertEquals(plaintext, storeB.records(owner, origin).associate { it.id to it.payload })

                    storeA.put(owner, origin, correction("edit-a", "Private Cafe A", "device-a"))
                    storeB.put(owner, origin, correction("edit-b", "Private Cafe B", "device-b"))
                    assertTrue(engineA.upload(token).success)
                    assertTrue(engineB.upload(token).success)
                    assertTrue(engineA.download(token).success)
                    assertTrue(engineB.download(token).success)
                    assertEquals(
                        setOf("edit-a", "edit-b"),
                        storeA
                            .records(owner, origin)
                            .filter { it.recordType == "correction" }
                            .map { it.id }
                            .toSet(),
                    )
                    assertEquals(
                        setOf("edit-a", "edit-b"),
                        storeB
                            .records(owner, origin)
                            .filter { it.recordType == "correction" }
                            .map { it.id }
                            .toSet(),
                    )
                    val original = storeB.record(owner, origin, "observation:1")!!
                    val deletion =
                        storeA
                            .record(
                                owner,
                                origin,
                                original.id,
                            )!!
                            .copy(payload = null, deleted = true, dirty = true, deviceVersion = 2)
                    storeA.put(owner, origin, deletion)
                    assertTrue(engineA.upload(token).success)
                    storeB.put(owner, origin, original.copy(deviceId = "device-b", deviceVersion = 2, dirty = true))
                    assertFalse(engineB.upload(token).success)
                    assertTrue(engineB.download(token).success)
                    assertTrue(storeB.record(owner, origin, original.id)!!.deleted)
                    assertEquals(null, storeB.record(owner, origin, original.id)!!.payload)
                    assertTrue(storeB.pending(owner, origin).isEmpty())
                    assertTrue(
                        transport
                            .changes(token, 205, 100)
                            .records
                            .filter { !it.deleted }
                            .all { !it.payload!!.contains("Private Cafe") },
                    )
                } finally {
                    databaseA.close()
                    databaseB.close()
                    Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
                }
            }
        }

    private fun database(path: Path) = Room.databaseBuilder<LogDateDatabase>(path.toString()).setDriver(BundledSQLiteDriver()).build()

    private fun cipher(
        identity: IdentityKeyManager,
        crypto: DesktopCryptoManager,
    ) = SyncPayloadCipher(ContentEncryptionService(identity, KeyDerivation(crypto), crypto), identity, crypto)

    private fun correction(
        id: String,
        value: String,
        device: String,
    ) = HistoryRecord(
        id,
        "correction",
        Json.encodeToString<HistoryPayload>(HistoryPayload.Correction(HistoryCorrection(id, "observation:1", HistoryField.PLACE, value))),
        device,
    )
}

private class HistoryTestSession(
    session: UserSession,
) : SessionStorage {
    private val state = MutableStateFlow<UserSession?>(session)

    override fun getSession(): UserSession? = state.value

    override fun getSessionFlow(): Flow<UserSession?> = state

    override suspend fun hasValidSession(): Boolean = state.value != null

    override fun saveSession(session: UserSession) {
        state.value = session
    }

    override fun clearSession() {
        state.value = null
    }
}

private class HistoryTestSecrets : SecureStorage {
    private val state = MutableStateFlow<Map<String, String>>(emptyMap())

    override suspend fun getString(key: String): String? = state.value[key]

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        state.value = state.value + (key to value)
    }

    override suspend fun remove(key: String) {
        state.value = state.value - key
    }

    override suspend fun clear() {
        state.value = emptyMap()
    }

    override fun observeString(key: String): Flow<String?> = state.map { it[key] }

    override fun observeAll(): Flow<Map<String, String>> = state

    override suspend fun encrypt(data: ByteArray): ByteArray = error("Not used: fixture keys exist only in process memory")

    override suspend fun decrypt(data: ByteArray): ByteArray? = error("Not used: fixture keys exist only in process memory")
}
