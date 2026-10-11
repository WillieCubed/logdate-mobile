package app.logdate.feature.core.account

import app.logdate.client.domain.account.AuthenticateWithPasskeyUseCase
import app.logdate.client.domain.account.CreatePasskeyAccountUseCase
import app.logdate.client.networking.DiscoveryFailure
import app.logdate.client.networking.ServerDiscoveryException
import app.logdate.feature.core.settings.ui.ServerPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CloudAccountFailureMessagesTest {
    private fun cloudMessage(kind: DiscoveryFailure) =
        serverSelectionFailureMessage(
            ServerDiscoveryException(kind, "https://cloud.logdate.app answered HTTP 504"),
            ServerPreset.PRODUCTION,
        )

    @Test
    fun `LogDate Cloud not responding says to try again later`() {
        assertEquals("LogDate Cloud isn’t responding right now. Try again in a few minutes.", cloudMessage(DiscoveryFailure.NOT_RESPONDING))
    }

    @Test
    fun `LogDate Cloud out of reach says to check the connection`() {
        assertEquals(
            "Can’t reach LogDate Cloud. Check your internet connection, then try again.",
            cloudMessage(DiscoveryFailure.UNREACHABLE),
        )
    }

    @Test
    fun `an unreadable answer from LogDate Cloud says to update LogDate`() {
        assertEquals(
            "LogDate Cloud answered with something this version of LogDate can’t read. Update LogDate, then try again.",
            cloudMessage(DiscoveryFailure.UNREADABLE),
        )
    }

    @Test
    fun `a custom server is named as that server and its address is mentioned`() {
        val message =
            serverSelectionFailureMessage(ServerDiscoveryException(DiscoveryFailure.UNREACHABLE, "boom"), ServerPreset.CUSTOM)

        assertEquals("Can’t reach that server. Check the address and your internet connection, then try again.", message)
    }

    @Test
    fun `sign-in tells a stalled server apart from no connection`() {
        assertEquals(
            "The server isn’t responding right now. Try again in a few minutes.",
            signInFailureMessage(AuthenticateWithPasskeyUseCase.AuthenticationError.ServerUnavailable),
        )
        assertEquals(
            "Can’t reach the server. Check your internet connection, then try again.",
            signInFailureMessage(AuthenticateWithPasskeyUseCase.AuthenticationError.NetworkError),
        )
    }

    @Test
    fun `no account failure message speaks as we`() {
        val messages =
            listOf(
                CreatePasskeyAccountUseCase.CreateAccountError.UsernameTaken,
                CreatePasskeyAccountUseCase.CreateAccountError.UsernameInvalid,
                CreatePasskeyAccountUseCase.CreateAccountError.DisplayNameInvalid,
                CreatePasskeyAccountUseCase.CreateAccountError.PasskeyNotSupported,
                CreatePasskeyAccountUseCase.CreateAccountError.PasskeyCancelled,
                CreatePasskeyAccountUseCase.CreateAccountError.PasskeyFailed,
                CreatePasskeyAccountUseCase.CreateAccountError.NetworkError,
                CreatePasskeyAccountUseCase.CreateAccountError.RateLimited,
                CreatePasskeyAccountUseCase.CreateAccountError.ServerError,
                CreatePasskeyAccountUseCase.CreateAccountError.Unknown("HTTP 504 from https://cloud.logdate.app"),
            ).map(::accountCreationFailureMessage)

        assertFalse(messages.any { Regex("\\b(we|our|us)\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) })
    }

    @Test
    fun `no message repeats the technical failure`() {
        val technical = "https://cloud.logdate.app answered HTTP 504"
        for (kind in DiscoveryFailure.entries) {
            for (preset in ServerPreset.entries) {
                assertFalse(technical in serverSelectionFailureMessage(ServerDiscoveryException(kind, technical), preset))
            }
        }
    }
}
