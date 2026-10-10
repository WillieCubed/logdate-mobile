@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.integration.e2e.journeys

import app.logdate.client.networking.DefaultServerDiscoveryClient
import app.logdate.client.networking.DeviceLinkApiClient
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.LogDateCloudApiClient
import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import app.logdate.integration.e2e.harness.withServerClientHarness
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.devices.DeviceLinkErrorCodes
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * The real cloud client against the real server for a linked watch's session: a refresh returns a
 * usable access token, and once the phone removes the watch both the refresh and every sync request
 * answer `401 DEVICE_SESSION_REVOKED`, which is how the watch learns to stop and erase itself.
 */
class WatchSessionRefreshClientServerE2ETest {
    @Test
    fun `a watch refreshes its session and learns when the phone removes it`() =
        runTest {
            withServerClientHarness {
                val origin = baseUrl.removeSuffix("/api/v1")
                val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
                val client = LogDateCloudApiClient(config, httpClient)
                val links = DeviceLinkApiClient(httpClient, config)
                val phone = apiClient.createAccountWithSyntheticPasskey("watch_session_${Random.nextInt(100000, 999999)}").data
                val watch = links.redeem(links.mintLinkCode(phone.tokens.accessToken).getOrThrow().code, "Pixel Watch 3").getOrThrow()
                config.updateServerDescriptor(
                    DefaultServerDiscoveryClient(httpClient).discoverServer(origin).getOrThrow().copy(serverOrigin = origin),
                )

                val refreshed = client.refreshSession(watch.tokens.refreshToken).getOrThrow()

                assertTrue(refreshed.accessToken.isNotBlank())
                assertNull(refreshed.refreshToken, "a new session is not close enough to its end for the server to rotate its token")
                assertTrue(client.getContentChanges(refreshed.accessToken, since = 0).isSuccess)

                links.revoke(watch.sessionId, phone.tokens.accessToken).getOrThrow()

                val refusedRefresh = assertIs<CloudApiException>(client.refreshSession(watch.tokens.refreshToken).exceptionOrNull())
                assertEquals(DeviceLinkErrorCodes.DEVICE_SESSION_REVOKED, refusedRefresh.errorCode)
                assertEquals(401, refusedRefresh.statusCode)
                val refusedSync = assertIs<CloudApiException>(client.getContentChanges(refreshed.accessToken, since = 0).exceptionOrNull())
                assertEquals(DeviceLinkErrorCodes.DEVICE_SESSION_REVOKED, refusedSync.errorCode)
                assertEquals(401, refusedSync.statusCode)
                val refusedState =
                    assertIs<CloudApiException>(client.contentState(refreshed.accessToken, Uuid.random().toString()).exceptionOrNull())
                assertEquals(401, refusedState.statusCode)
            }
        }
}
