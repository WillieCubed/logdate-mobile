package app.logdate.feature.core.account

import app.logdate.client.domain.account.AuthenticateWithPasskeyUseCase
import app.logdate.client.domain.account.CreatePasskeyAccountUseCase
import app.logdate.client.networking.DiscoveryFailure
import app.logdate.client.networking.ServerDiscoveryException
import app.logdate.feature.core.settings.ui.ServerPreset
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.account_failure_cloud_not_responding
import logdate.client.feature.core.generated.resources.account_failure_cloud_unreachable
import logdate.client.feature.core.generated.resources.account_failure_cloud_unreadable
import logdate.client.feature.core.generated.resources.account_failure_custom_unreachable
import logdate.client.feature.core.generated.resources.account_failure_server_not_responding
import logdate.client.feature.core.generated.resources.account_failure_server_unreachable
import logdate.client.feature.core.generated.resources.account_failure_unknown
import kotlin.test.Test
import kotlin.test.assertEquals

class CloudAccountFailureMessagesTest {
    private fun cloudMessage(kind: DiscoveryFailure) =
        serverSelectionFailureMessage(
            ServerDiscoveryException(kind, "https://cloud.logdate.app answered HTTP 504"),
            ServerPreset.PRODUCTION,
        )

    @Test
    fun `LogDate Cloud not responding says to try again later`() {
        assertEquals(Res.string.account_failure_cloud_not_responding, cloudMessage(DiscoveryFailure.NOT_RESPONDING))
    }

    @Test
    fun `LogDate Cloud out of reach says to check the connection`() {
        assertEquals(Res.string.account_failure_cloud_unreachable, cloudMessage(DiscoveryFailure.UNREACHABLE))
    }

    @Test
    fun `an unreadable answer from LogDate Cloud says to update LogDate`() {
        assertEquals(Res.string.account_failure_cloud_unreadable, cloudMessage(DiscoveryFailure.UNREADABLE))
    }

    @Test
    fun `a custom server is named as that server and its address is mentioned`() {
        val message =
            serverSelectionFailureMessage(ServerDiscoveryException(DiscoveryFailure.UNREACHABLE, "boom"), ServerPreset.CUSTOM)

        assertEquals(Res.string.account_failure_custom_unreachable, message)
    }

    @Test
    fun `sign-in tells a stalled server apart from no connection`() {
        assertEquals(
            Res.string.account_failure_server_not_responding,
            signInFailureMessage(AuthenticateWithPasskeyUseCase.AuthenticationError.ServerUnavailable),
        )
        assertEquals(
            Res.string.account_failure_server_unreachable,
            signInFailureMessage(AuthenticateWithPasskeyUseCase.AuthenticationError.NetworkError),
        )
    }

    @Test
    fun `an unknown account creation failure gives a generic message instead of the exception text`() {
        assertEquals(
            Res.string.account_failure_unknown,
            accountCreationFailureMessage(
                CreatePasskeyAccountUseCase.CreateAccountError.Unknown("HTTP 504 from https://cloud.logdate.app"),
            ),
        )
    }

    @Test
    fun `no message depends on the technical failure`() {
        for (kind in DiscoveryFailure.entries) {
            for (preset in ServerPreset.entries) {
                assertEquals(
                    serverSelectionFailureMessage(ServerDiscoveryException(kind, "boom"), preset),
                    serverSelectionFailureMessage(ServerDiscoveryException(kind, "https://cloud.logdate.app answered HTTP 504"), preset),
                )
            }
        }
    }
}
