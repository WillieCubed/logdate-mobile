@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.integration.e2e.journeys

import app.logdate.client.datastore.UserSession
import app.logdate.client.device.identity.DefaultDeviceManager
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.device.identity.data.AccountDeviceApi
import app.logdate.client.device.identity.data.CloudDeviceRepository
import app.logdate.client.device.models.DevicePlatform
import app.logdate.client.device.storage.SecureSessionStorage
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.sync.cloud.DeviceId
import app.logdate.client.sync.cloud.JournalUploadRequest
import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import app.logdate.integration.e2e.harness.withServerClientHarness
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.RegisteredDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ReplacementDeviceRegistrationClientServerE2ETest {
    @Test
    fun `external installation replacement registers once and keeps the account journal and source device`() =
        runTest {
            withServerClientHarness {
                val account = apiClient.createAccountWithSyntheticPasskey("replace_${Uuid.random().toString().take(8)}").data
                val config = DefaultLogDateConfigRepository(initialBackendUrl = baseUrl.removeSuffix("/api/v1"))
                val secrets = RegistrationTestSecrets()
                val sessions = SecureSessionStorage(secrets, config, backgroundScope)
                sessions.saveSession(UserSession(account.tokens.accessToken, account.tokens.refreshToken, account.account.id.toString()))
                val ownerSession = assertNotNull(sessions.getOriginBoundSession())
                val devices = AccountDeviceApi(httpClient, sessions)
                val sourceId = Uuid.random()
                val replacementId = Uuid.random()
                val provider = ReplacementRegistrationIds(sourceId)
                val manager = registrationManager(provider, CloudDeviceRepository(devices, sessions, secrets))
                val journalId = Uuid.random().toString()
                apiClient
                    .uploadJournal(
                        ownerSession.session.accessToken,
                        JournalUploadRequest(journalId, "Retained account journal", "", 1L, 1L, deviceId = DeviceId(sourceId.toString())),
                    ).getOrThrow()
                val journalBefore = apiClient.getJournalChanges(ownerSession.session.accessToken, 0L).getOrThrow()

                assertTrue(manager.registerWithCloud().getOrThrow())
                manager.renameDevice("Source phone")
                provider.current.value = replacementId
                repeat(2) { assertTrue(manager.registerWithCloud().getOrThrow()) }
                assertRegistrationIds(devices.list(ownerSession), sourceId, replacementId)
                manager.renameDevice("Replacement phone")

                val reopenedSessions = SecureSessionStorage(secrets, config, backgroundScope)
                assertTrue(reopenedSessions.hasValidSession())
                val reopenedApi = AccountDeviceApi(httpClient, reopenedSessions)
                val reopened = registrationManager(provider, CloudDeviceRepository(reopenedApi, reopenedSessions, secrets))
                repeat(2) { assertTrue(reopened.registerWithCloud().getOrThrow()) }
                val listed = reopenedApi.list(assertNotNull(reopenedSessions.getOriginBoundSession()))
                assertRegistrationIds(listed, sourceId, replacementId)
                assertEquals("Source phone", listed.single { it.id == sourceId.toString() }.name)
                assertEquals("Replacement phone", listed.single { it.id == replacementId.toString() }.name)
                assertEquals(ownerSession, reopenedSessions.getOriginBoundSession())
                val journalAfter = apiClient.getJournalChanges(ownerSession.session.accessToken, 0L).getOrThrow()
                assertEquals(journalBefore, journalAfter)
                assertEquals("Retained account journal", journalAfter.changes.single { it.id == journalId }.title)
            }
        }
}

private fun registrationManager(
    provider: DeviceIdProvider,
    repository: CloudDeviceRepository,
) = DefaultDeviceManager(provider, repository, "System phone name", DevicePlatform.ANDROID, "test-version")

private fun assertRegistrationIds(
    listed: List<RegisteredDevice>,
    sourceId: Uuid,
    replacementId: Uuid,
) {
    assertNotEquals(sourceId, replacementId)
    assertEquals(2, listed.size)
    assertEquals(setOf(sourceId.toString(), replacementId.toString()), listed.map { it.id }.toSet())
    assertEquals(1, listed.count { it.id == sourceId.toString() })
    assertEquals(1, listed.count { it.id == replacementId.toString() })
}

private class ReplacementRegistrationIds(
    source: Uuid,
) : DeviceIdProvider {
    val current = MutableStateFlow(source)

    override fun getDeviceId() = current

    override suspend fun refreshDeviceId(): Unit = error("The test changes the installation ID externally")
}

private class RegistrationTestSecrets : SecureStorage {
    private val state = MutableStateFlow<Map<String, String>>(emptyMap())

    override suspend fun getString(key: String): String? = state.value[key]

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        state.update { it + (key to value) }
    }

    override suspend fun remove(key: String) {
        state.update { it - key }
    }

    override suspend fun clear() {
        state.value = emptyMap()
    }

    override fun observeString(key: String): Flow<String?> = state.map { it[key] }

    override fun observeAll(): Flow<Map<String, String>> = state

    override suspend fun encrypt(data: ByteArray): ByteArray = error("Encryption is outside this registration fixture")

    override suspend fun decrypt(data: ByteArray): ByteArray? = error("Encryption is outside this registration fixture")
}
