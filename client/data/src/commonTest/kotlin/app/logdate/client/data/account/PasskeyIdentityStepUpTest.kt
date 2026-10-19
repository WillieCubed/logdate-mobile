package app.logdate.client.data.account

import app.logdate.client.permissions.PasskeyErrorCodes
import app.logdate.client.permissions.PasskeyException
import app.logdate.client.repository.account.IdentityRecoveryException
import app.logdate.client.repository.account.IdentityRecoveryFailure
import app.logdate.shared.model.PasskeyAuthenticationOptions
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The passkey confirmation that every recovery-key change sends, and how an unfinished prompt is reported. */
class PasskeyIdentityStepUpTest {
    private val options =
        PasskeyAuthenticationOptions(challenge = "step-up-challenge", rpId = "logdate.app", allowCredentials = listOf("credential123"))

    @Test
    fun `the proof answers the server's challenge with the platform's assertion`() =
        runTest {
            val passkeys = DefaultPasskeyAccountRepositoryTest.FakePasskeyManager()

            val proof = stepUp(passkeys).confirm("token")

            assertEquals("step-up-challenge", proof.challenge)
            assertEquals("credential123", proof.credential.id)
            assertEquals("signature", proof.credential.response.signature)
        }

    @Test
    fun `an unfinished prompt says why`() =
        runTest {
            assertEquals(IdentityRecoveryFailure.STEP_UP_CANCELLED, failureFor(PasskeyErrorCodes.USER_CANCELLED))
            assertEquals(IdentityRecoveryFailure.STEP_UP_UNAVAILABLE, failureFor(PasskeyErrorCodes.NOT_SUPPORTED))
            assertEquals(IdentityRecoveryFailure.STEP_UP_FAILED, failureFor(PasskeyErrorCodes.TIMEOUT))
        }

    @Test
    fun `an unreadable assertion is a failed confirmation`() =
        runTest {
            val passkeys =
                DefaultPasskeyAccountRepositoryTest.FakePasskeyManager().apply {
                    authenticateWithPasskeyResponse =
                        Result.success("{}")
                }

            val failure = assertFailsWith<IdentityRecoveryException> { stepUp(passkeys).confirm("token") }

            assertEquals(IdentityRecoveryFailure.STEP_UP_FAILED, failure.failure)
        }

    private suspend fun failureFor(code: String): IdentityRecoveryFailure {
        val passkeys =
            DefaultPasskeyAccountRepositoryTest.FakePasskeyManager().apply {
                authenticateWithPasskeyResponse = Result.failure(PasskeyException("prompt", code))
            }
        return assertFailsWith<IdentityRecoveryException> { stepUp(passkeys).confirm("token") }.failure
    }

    private fun stepUp(passkeys: DefaultPasskeyAccountRepositoryTest.FakePasskeyManager) =
        PasskeyIdentityStepUp(
            beginStepUp = { Result.success(options) },
            passkeyManager = passkeys,
            credentialCodec = PasskeyCredentialCodec(Json { ignoreUnknownKeys = true }),
        )
}
