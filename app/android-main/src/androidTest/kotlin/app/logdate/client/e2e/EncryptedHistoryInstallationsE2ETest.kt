@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.client.e2e

import android.provider.Settings
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.data.location.RoomHistoryRecordStore
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.crypto.AndroidCryptoManager
import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.device.storage.AndroidSecureStorage
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.networking.LocationHistoryApiClient
import app.logdate.client.networking.LocationHistoryApiClientContract
import app.logdate.client.networking.LocationHistoryTransportException
import app.logdate.client.repository.location.HistoryRecord
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.client.sync.location.LocationHistorySyncEngine
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.location.HistoryPayload
import app.logdate.shared.model.location.LocationObservation
import app.logdate.shared.model.sync.LocationHistoryChangesResponse
import app.logdate.shared.model.sync.LocationHistoryUpload
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Run source and restore sequentially on two different Gradle Managed Devices with one host harness. */
@RunWith(AndroidJUnit4::class)
class EncryptedHistoryInstallationsE2ETest {
    @Test(timeout = 180_000)
    fun separateInstallationsExchangeEncryptedHistory() =
        runBlocking {
            val phase = InstrumentationRegistry.getArguments().getString("historyPhase")
            assumeTrue("Explicit phased host harness required", phase == "source" || phase == "restore")
            val fixtureConnection =
                URL("$FIXTURE/fixture").openConnection().apply {
                    connectTimeout = 5_000
                    readTimeout = 5_000
                }
            val fixture =
                fixtureConnection.getInputStream().bufferedReader().use {
                    Json.parseToJsonElement(it.readText()).jsonObject
                }

            fun field(name: String) = fixture.getValue(name).jsonPrimitive.content
            val owner = field("owner")
            val origin = field("origin")
            val token = field("token")
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val identity = IdentityKeyManager(InstallationHistorySecrets(AndroidSecureStorage(context), owner), AndroidCryptoManager())
            identity.recoverIdentity(field("recovery").split(" "))
            val crypto = AndroidCryptoManager()
            val cipher = SyncPayloadCipher(ContentEncryptionService(identity, KeyDerivation(crypto), crypto), identity, crypto)
            val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
            val session = InstallationHistorySession(UserSession(token, field("refreshToken"), owner))
            val databaseName = "history-installation-$owner-$phase.db"

            fun openDatabase() = Room.databaseBuilder(context, LogDateDatabase::class.java, databaseName).build()
            var database = openDatabase()
            val http =
                HttpClient(OkHttp) {
                    install(HttpTimeout) { requestTimeoutMillis = 15_000 }
                }
            try {
                var store = RoomHistoryRecordStore(database.historyRecordDao())
                val transport = LocationHistoryApiClient(http, config)

                fun engine(overrideApi: LocationHistoryApiClientContract? = null) =
                    LocationHistorySyncEngine(
                        { pinned -> overrideApi ?: LocationHistoryApiClient(http, pinned) },
                        store,
                        cipher,
                        session,
                        config,
                        { true },
                    )
                val plaintext =
                    (1..205).associate { index ->
                        val id = "observation:$index"
                        id to
                            Json.encodeToString<HistoryPayload>(
                                HistoryPayload.Observation(
                                    LocationObservation(
                                        id,
                                        owner,
                                        "android-source",
                                        Instant.fromEpochMilliseconds(index.toLong()),
                                        36.17,
                                        -115.14,
                                        timeZoneId = "America/Los_Angeles",
                                    ),
                                ),
                            )
                    }
                if (phase == "source") {
                    plaintext.forEach { (id, payload) ->
                        store.put(owner, origin, HistoryRecord(id, "observation", payload, "android-source"))
                    }
                    assertEquals(205, store.pending(owner, origin, 1000).size)
                    assertTrue(engine().upload(token).success)
                    val original = store.record(owner, origin, "observation:1")!!
                    val staleWire = transport.changes(token, 0, 100).records.first { it.id == original.id }
                    store.put(owner, origin, original.copy(payload = null, deleted = true, dirty = true, deviceVersion = 2))
                    assertTrue(engine().upload(token).success)
                    val staleFailure =
                        runCatching {
                            transport.upload(
                                token,
                                listOf(
                                    LocationHistoryUpload(
                                        staleWire.id,
                                        staleWire.recordType,
                                        staleWire.payload,
                                        staleWire.payloadSchemaVersion,
                                        "stale-installation",
                                        3,
                                        staleWire.serverVersion,
                                    ),
                                ),
                            )
                        }.exceptionOrNull()
                    assertEquals(
                        409,
                        (staleFailure as? LocationHistoryTransportException)?.statusCode,
                        "Stale upload must not resurrect a deletion",
                    )
                    assertTrue(engine().download(token).success)
                } else {
                    assertTrue(store.records(owner, origin).isEmpty(), "Restore must begin with a fresh installation store")
                    var requests = 0
                    val interrupted =
                        object : LocationHistoryApiClientContract by transport {
                            override suspend fun changes(
                                accessToken: String,
                                since: Long,
                                limit: Int,
                            ): LocationHistoryChangesResponse {
                                if (++requests == 2) error("Deliberate interruption after committed page")
                                return transport.changes(accessToken, since, limit)
                            }
                        }
                    assertFalse(engine(interrupted).download(token).success)
                    assertEquals(100, store.records(owner, origin).size)
                    val cursor = store.cursor(owner, origin)
                    database.close()
                    database = openDatabase()
                    store = RoomHistoryRecordStore(database.historyRecordDao())
                    assertEquals(cursor, store.cursor(owner, origin))
                    assertTrue(engine().download(token).success)
                }
                val restored = store.records(owner, origin)
                assertEquals(205, restored.size)
                assertEquals(plaintext - "observation:1", restored.filterNot { it.deleted }.associate { it.id to it.payload })
                assertTrue(store.record(owner, origin, "observation:1")!!.deleted)
                assertEquals(null, store.record(owner, origin, "observation:1")!!.payload)
                assertTrue(store.pending(owner, origin).isEmpty())
                val wire = transport.changes(token, 0, 100)
                assertTrue(
                    wire.records.filterNot { it.deleted }.all {
                        it.payload!!.startsWith("LDSE2:") &&
                            !it.payload!!.contains("America/Los_Angeles")
                    },
                )
                val installation = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                val completion = URL("$FIXTURE/complete?$phase").openConnection() as HttpURLConnection
                try {
                    completion.connectTimeout = 5_000
                    completion.readTimeout = 5_000
                    completion.requestMethod = "POST"
                    completion.doOutput = true
                    completion.outputStream.use { it.write(installation.toByteArray()) }
                    assertEquals(204, completion.responseCode, "Harness must verify two different installation IDs")
                } finally {
                    completion.disconnect()
                }
            } finally {
                database.close()
                http.close()
                context.deleteDatabase(databaseName)
            }
        }

    private companion object {
        const val FIXTURE = "http://10.0.2.2:18879"
    }
}

private class InstallationHistorySession(
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

private class InstallationHistorySecrets(
    private val storage: SecureStorage,
    owner: String,
) : SecureStorage by storage {
    private val prefix = "history-test:$owner:"

    override suspend fun getString(key: String): String? = storage.getString(prefix + key)

    override suspend fun putString(
        key: String,
        value: String,
    ) = storage.putString(prefix + key, value)

    override suspend fun remove(key: String) = storage.remove(prefix + key)

    override fun observeString(key: String): Flow<String?> = storage.observeString(prefix + key)

    override suspend fun clear() = error("Test namespace must not clear installation secrets")
}
