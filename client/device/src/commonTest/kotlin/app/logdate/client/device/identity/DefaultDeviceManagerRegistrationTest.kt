package app.logdate.client.device.identity

import app.logdate.client.datastore.UserSession
import app.logdate.client.device.identity.data.AccountDeviceApi
import app.logdate.client.device.identity.data.CloudDeviceRepository
import app.logdate.client.device.models.DevicePlatform
import app.logdate.client.device.storage.FakeSecureStorage
import app.logdate.client.device.storage.SecureSessionStorage
import app.logdate.shared.config.DefaultLogDateConfigRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class DefaultDeviceManagerRegistrationTest {
    @Test
    fun `registration follows the replacement identity instead of the constructor identity`() =
        runTest {
            val cache = FakeSecureStorage()
            val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://journal.example.com")
            val sessions = SecureSessionStorage(cache, config, backgroundScope)
            sessions.saveSession(UserSession("access", "refresh", "journal-owner"))
            val originalId = Uuid.random()
            val replacementId = Uuid.random()
            val ids = MutableStateFlow(originalId)
            val provider =
                object : DeviceIdProvider {
                    override fun getDeviceId() = ids

                    override suspend fun refreshDeviceId() {
                        ids.value = Uuid.random()
                    }
                }
            val registeredPaths = mutableListOf<String>()
            HttpClient(
                MockEngine { request ->
                    registeredPaths += request.url.encodedPath
                    respond("{}")
                },
            ).use { client ->
                val manager =
                    DefaultDeviceManager(
                        provider,
                        CloudDeviceRepository(AccountDeviceApi(client, sessions), sessions, cache),
                        "Replacement phone",
                        DevicePlatform.ANDROID,
                        "1",
                    )
                ids.value = replacementId

                repeat(2) { assertTrue(manager.registerWithCloud().getOrThrow()) }

                assertEquals(listOf("/api/v1/devices/$replacementId", "/api/v1/devices/$replacementId"), registeredPaths)
                assertEquals(replacementId, manager.getCurrentDeviceInfo().id)
                assertEquals("journal-owner", sessions.getSession()?.accountId)
            }
        }
}
