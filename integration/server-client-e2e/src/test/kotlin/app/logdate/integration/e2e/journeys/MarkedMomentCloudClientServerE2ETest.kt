@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.integration.e2e.journeys

import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.DesktopCryptoManager
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.networking.DefaultServerDiscoveryClient
import app.logdate.client.networking.DeviceLinkApiClient
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.ConditionalCreateResult
import app.logdate.client.sync.cloud.DefaultCloudMomentDataSource
import app.logdate.client.sync.cloud.LogDateCloudApiClient
import app.logdate.client.sync.conflict.MarkedMomentConflictResolver
import app.logdate.client.sync.crypto.MarkedMomentPayloadCodec
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import app.logdate.integration.e2e.harness.withServerClientHarness
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.sync.DeviceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MarkedMomentCloudClientServerE2ETest {
    @Test
    fun `linked watch mark resolves through encrypted cloud conflicts and cannot resurrect after deletion`() =
        runTest {
            withServerClientHarness {
                val account = apiClient.createAccountWithSyntheticPasskey("marks_${Random.nextInt(100000, 999999)}").data
                val token = account.tokens.accessToken
                val origin = baseUrl.removeSuffix("/api/v1")
                val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
                config.updateServerDescriptor(
                    DefaultServerDiscoveryClient(
                        httpClient,
                    ).discoverServer(origin).getOrThrow().copy(serverOrigin = origin, apiBaseUrl = baseUrl),
                )
                val api = LogDateCloudApiClient(config, httpClient)
                val links = DeviceLinkApiClient(httpClient, config)
                val watchToken =
                    links
                        .redeem(links.mintLinkCode(token).getOrThrow().code, "Moment watch")
                        .getOrThrow()
                        .tokens.accessToken
                val cryptoA = DesktopCryptoManager()
                val cryptoB = DesktopCryptoManager()
                val identityA = IdentityKeyManager(MomentTestSecrets(), cryptoA)
                val identityB = IdentityKeyManager(MomentTestSecrets(), cryptoB)
                identityB.recoverIdentity(identityA.setupNewIdentity().words)

                fun codec(
                    identity: IdentityKeyManager,
                    crypto: DesktopCryptoManager,
                ) = MarkedMomentPayloadCodec(
                    SyncPayloadCipher(ContentEncryptionService(identity, KeyDerivation(crypto), crypto), identity, crypto),
                )
                val watch = DefaultCloudMomentDataSource(api, codec(identityA, cryptoA))
                val phone = DefaultCloudMomentDataSource(api, codec(identityB, cryptoB))
                val captured = Instant.parse("2026-10-12T12:00:00.123456789Z")
                val mark =
                    MarkedMoment(
                        Uuid.random(),
                        captured,
                        "America/Los_Angeles",
                        null,
                        72,
                        MarkState.OPEN,
                        "A private moment",
                        emptySet(),
                        "watch-origin",
                        captured,
                        0,
                    )
                val created =
                    assertIs<ConditionalCreateResult.Created>(watch.createIfAbsent(watchToken, mark, DeviceId("watch")).getOrThrow())
                assertEquals(
                    ConditionalCreateResult.AlreadyExists(created.serverVersion),
                    watch.createIfAbsent(watchToken, mark, DeviceId("watch")).getOrThrow(),
                )
                val onPhone = phone.get(token, mark.id).getOrThrow()
                assertEquals(mark.copy(syncVersion = created.serverVersion), onPhone)
                val raw = api.getMoment(token, mark.id.toString()).getOrThrow()
                assertTrue(raw.payload.startsWith("LDSE2:"))
                assertFalse(raw.payload.contains(mark.caption))
                assertFalse(raw.payload.contains(mark.originDeviceId))
                val dismissed = onPhone.copy(state = MarkState.DISMISSED, lastUpdated = captured + kotlin.time.Duration.parse("1s"))
                phone.update(token, dismissed, DeviceId("phone")).getOrThrow()
                val noteId = Uuid.random()
                val filled =
                    onPhone.copy(
                        state = MarkState.FILLED,
                        resolvedNoteIds = setOf(noteId),
                        lastUpdated =
                            captured + kotlin.time.Duration.parse("2s"),
                    )
                assertEquals(
                    "CONFLICT",
                    assertIs<CloudApiException>(phone.update(token, filled, DeviceId("phone")).exceptionOrNull()).errorCode,
                )
                val current = phone.get(token, mark.id).getOrThrow()
                val merged = MarkedMomentConflictResolver().merge(filled, current)
                phone.update(token, merged, DeviceId("phone")).getOrThrow()
                val returned = watch.get(watchToken, mark.id).getOrThrow()
                assertEquals(MarkState.FILLED, returned.state)
                assertEquals(setOf(noteId), returned.resolvedNoteIds)
                assertEquals(captured, returned.capturedAt)
                val page = watch.changes(watchToken, Instant.fromEpochMilliseconds(0), 1).getOrThrow()
                assertEquals(listOf(returned), page.changes)
                phone.delete(token, mark.id).getOrThrow()
                assertIs<ConditionalCreateResult.Deleted>(watch.createIfAbsent(watchToken, mark, DeviceId("watch")).getOrThrow())
                val deleted = watch.changes(watchToken, page.lastSyncTimestamp, 1).getOrThrow()
                assertEquals(listOf(mark.id), deleted.deletions)
                assertTrue(deleted.deletionVersions.getValue(mark.id) > returned.syncVersion)
                assertTrue(phone.get(token, mark.id).isFailure)
            }
        }
}

private class MomentTestSecrets : SecureStorage {
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

    override suspend fun encrypt(data: ByteArray): ByteArray = error("Not used by in-memory fixture")

    override suspend fun decrypt(data: ByteArray): ByteArray? = error("Not used by in-memory fixture")
}
