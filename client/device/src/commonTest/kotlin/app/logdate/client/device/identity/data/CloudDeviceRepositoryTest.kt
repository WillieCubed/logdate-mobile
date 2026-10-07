package app.logdate.client.device.identity.data

import app.logdate.client.datastore.UserSession
import app.logdate.client.device.models.DeviceInfo
import app.logdate.client.device.models.DevicePlatform
import app.logdate.client.device.storage.FakeSecureStorage
import app.logdate.client.device.storage.SecureSessionStorage
import app.logdate.shared.config.DefaultLogDateConfigRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CloudDeviceRepositoryTest {
    @Test
    fun `switching accounts clears the previous device list before the new request finishes`() =
        runTest {
            val cache = FakeSecureStorage()
            val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://journal.example.com")
            val sessions = SecureSessionStorage(cache, config, backgroundScope)
            sessions.saveSession(UserSession("access-a", "refresh-a", "account-a"))
            val id = Uuid.random()
            val releaseSecondRequest = CompletableDeferred<Unit>()
            val client =
                HttpClient(MockEngine) {
                    engine {
                        dispatcher = UnconfinedTestDispatcher(testScheduler)
                        addHandler { request ->
                            if (request.headers["Authorization"] == "Bearer access-a") {
                                respond(
                                    """[{"id":"$id","name":"Test device","platform":"MACOS","appVersion":"0.1.0","createdAt":1000,"lastActive":2000}]""",
                                )
                            } else {
                                releaseSecondRequest.await()
                                respond("[]")
                            }
                        }
                    }
                }
            client.use {
                var latest = emptyList<DeviceInfo>()
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    CloudDeviceRepository(AccountDeviceApi(it), sessions, cache).getAssociatedDevices().collect { latest = it }
                }
                runCurrent()
                assertEquals(id, latest.single().id)
                sessions.saveSession(UserSession("access-b", "refresh-b", "account-b"))
                runCurrent()
                assertEquals(emptyList(), latest)
                releaseSecondRequest.complete(Unit)
                runCurrent()
            }
        }

    @Test
    fun `registration saved offline is retried after relaunch`() =
        runTest {
            val cache = FakeSecureStorage()
            val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://journal.example.com")
            val sessions = SecureSessionStorage(cache, config, backgroundScope)
            sessions.saveSession(UserSession("access", "refresh", "account-a"))
            val device =
                DeviceInfo(
                    id = Uuid.random(),
                    name = "Test device",
                    platform = DevicePlatform.ANDROID,
                    appVersion = "0.1.0",
                    createdAt = Instant.fromEpochMilliseconds(1000),
                    lastActive = Instant.fromEpochMilliseconds(2000),
                )
            HttpClient(MockEngine { respond("", HttpStatusCode.ServiceUnavailable) }).use {
                assertEquals(false, CloudDeviceRepository(AccountDeviceApi(it), sessions, cache).registerDevice(device))
            }
            var registrations = 0
            HttpClient(
                MockEngine { request ->
                    if (request.method == HttpMethod.Put) {
                        registrations++
                        assertEquals("/api/v1/devices/${device.id}", request.url.encodedPath)
                        respond("{}")
                    } else {
                        respond(
                            """[{"id":"${device.id}","name":"Test device","platform":"ANDROID","appVersion":"0.1.0","createdAt":1000,"lastActive":2000}]""",
                        )
                    }
                },
            ).use {
                val reopened = CloudDeviceRepository(AccountDeviceApi(it), sessions, cache)
                assertEquals(
                    device.id,
                    reopened
                        .getAssociatedDevices()
                        .first { it.isNotEmpty() }
                        .single()
                        .id,
                )
                assertEquals(1, registrations)
            }
        }

    @Test
    fun `a renamed device keeps its name when the app registers after relaunch`() =
        runTest {
            val cache = FakeSecureStorage()
            val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://journal.example.com")
            val sessions = SecureSessionStorage(cache, config, backgroundScope)
            sessions.saveSession(UserSession("access", "refresh", "account-a"))
            val names = mutableListOf<String>()
            val client =
                HttpClient(
                    MockEngine { request ->
                        val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                        names +=
                            Json
                                .parseToJsonElement(body)
                                .jsonObject
                                .getValue("name")
                                .jsonPrimitive.content
                        respond("{}")
                    },
                )
            client.use {
                val device =
                    DeviceInfo(
                        id = Uuid.random(),
                        name = "Renamed device",
                        platform = DevicePlatform.ANDROID,
                        appVersion = "0.1.0",
                        createdAt = Instant.fromEpochMilliseconds(1000),
                        lastActive = Instant.fromEpochMilliseconds(2000),
                    )
                assertTrue(CloudDeviceRepository(AccountDeviceApi(it), sessions, cache).updateDeviceInfo(device))
                val reopened = CloudDeviceRepository(AccountDeviceApi(it), sessions, cache)
                assertTrue(reopened.registerDevice(device.copy(name = "System device name")))
                assertEquals(listOf("Renamed device", "Renamed device"), names)
            }
        }

    @Test
    fun `failed refresh preserves the most recently fetched devices`() =
        runTest {
            val cache = FakeSecureStorage()
            val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://journal.example.com")
            val sessions = SecureSessionStorage(cache, config, backgroundScope)
            sessions.saveSession(UserSession("access", "refresh", "account-a"))
            val id = Uuid.random()
            var requests = 0
            val client =
                HttpClient(MockEngine) {
                    engine {
                        dispatcher = UnconfinedTestDispatcher(testScheduler)
                        addHandler {
                            if (++requests == 1) {
                                respond(
                                    """[{"id":"$id","name":"Test device","platform":"MACOS","appVersion":"0.1.0","createdAt":1000,"lastActive":2000}]""",
                                )
                            } else {
                                respond("", HttpStatusCode.ServiceUnavailable)
                            }
                        }
                    }
                }
            client.use {
                var latest = emptyList<DeviceInfo>()
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    CloudDeviceRepository(AccountDeviceApi(it), sessions, cache).getAssociatedDevices().collect { latest = it }
                }
                runCurrent()
                assertEquals(id, latest.single().id)
                advanceTimeBy(5_001)
                runCurrent()
                assertTrue(requests >= 2)
                assertEquals(id, latest.single().id)
            }
        }

    @Test
    fun `relaunch retains account devices while offline and another account cannot see them`() =
        runTest {
            val cache = FakeSecureStorage()
            val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://journal.example.com")
            val sessions =
                SecureSessionStorage(
                    cache,
                    config,
                    CoroutineScope(
                        backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler),
                    ),
                )
            sessions.saveSession(UserSession("access", "refresh", "account-a"))
            val id = Uuid.random()
            val online =
                HttpClient(
                    MockEngine {
                        respond(
                            """[{"id":"$id","name":"Test device","platform":"MACOS","appVersion":"0.1.0","createdAt":1000,"lastActive":2000}]""",
                        )
                    },
                )
            online.use {
                val devices = CloudDeviceRepository(AccountDeviceApi(it), sessions, cache).getAssociatedDevices().first { it.isNotEmpty() }
                assertEquals(id, devices.single().id)
            }
            val offline = HttpClient(MockEngine { respond("", HttpStatusCode.ServiceUnavailable) })
            offline.use {
                val reopened = CloudDeviceRepository(AccountDeviceApi(it), sessions, cache)
                assertEquals(
                    id,
                    reopened
                        .getAssociatedDevices()
                        .first()
                        .single()
                        .id,
                )
                sessions.saveSession(UserSession("access-b", "refresh-b", "account-b"))
                assertEquals(emptyList(), reopened.getAssociatedDevices().first())
            }
        }
}
