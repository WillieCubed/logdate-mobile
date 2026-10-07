@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.integration.e2e.journeys

import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.identity.data.AccountDeviceApi
import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import app.logdate.integration.e2e.harness.withServerClientHarness
import app.logdate.shared.model.RegisterDeviceRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class AccountDeviceClientServerE2ETest {
    @Test
    fun `signed in devices persist in the account list without duplicate registrations`() =
        runTest {
            withServerClientHarness {
                val account = apiClient.createAccountWithSyntheticPasskey("devices_${Uuid.random().toString().take(8)}").data
                val session =
                    OriginBoundSession(
                        baseUrl.removeSuffix("/api/v1"),
                        UserSession(account.tokens.accessToken, account.tokens.refreshToken, account.account.id.toString()),
                    )
                val devices = AccountDeviceApi(httpClient)
                val id = Uuid.random()
                repeat(2) {
                    assertTrue(devices.register(session, id, RegisterDeviceRequest("Test device", "MACOS", "0.1.0")))
                }
                val listed = devices.list(session)
                assertEquals(id.toString(), listed.single().id)
                assertEquals("MACOS", listed.single().platform)
                val other = apiClient.createAccountWithSyntheticPasskey("devices_${Uuid.random().toString().take(8)}").data
                assertTrue(
                    devices
                        .list(
                            session.copy(
                                session =
                                    UserSession(
                                        other.tokens.accessToken,
                                        other.tokens.refreshToken,
                                        other.account.id.toString(),
                                    ),
                            ),
                        ).isEmpty(),
                )
            }
        }
}
