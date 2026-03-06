package app.logdate.server.passkeys

import app.logdate.shared.model.AuthenticatorAssertionResponse
import app.logdate.shared.model.AuthenticatorAttestationResponse
import app.logdate.shared.model.PasskeyAuthenticationResponse
import app.logdate.shared.model.PasskeyRegistrationResponse
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class WebAuthnPasskeyServiceStrictModeTest {
    @Test
    fun `strict mode rejects non base64url registration payloads`() {
        val service =
            WebAuthnPasskeyService(
                passkeyRepository = InMemoryPasskeyRepository(),
                strictVerificationEnabled = true,
            )
        val userId = Uuid.random()
        val options = service.generateRegistrationOptions(userId, "strict_user", "Strict User")

        val result =
            service.verifyRegistration(
                userId = userId,
                challenge = options.challenge,
                registrationResponse =
                    PasskeyRegistrationResponse(
                        id = "not-base64",
                        rawId = "not-base64",
                        response =
                            AuthenticatorAttestationResponse(
                                clientDataJSON = "not-base64",
                                attestationObject = "not-base64",
                            ),
                    ),
            )

        assertFalse(result.success)
        assertNotNull(result.error)
    }

    @Test
    fun `strict mode rejects non base64url authentication payloads`() {
        val service =
            WebAuthnPasskeyService(
                passkeyRepository = InMemoryPasskeyRepository(),
                strictVerificationEnabled = true,
            )

        val result =
            service.verifyAuthentication(
                challenge = "not-base64",
                authenticationResponse =
                    PasskeyAuthenticationResponse(
                        id = "not-base64",
                        rawId = "not-base64",
                        response =
                            AuthenticatorAssertionResponse(
                                clientDataJSON = "not-base64",
                                authenticatorData = "not-base64",
                                signature = "not-base64",
                                userHandle = null,
                            ),
                    ),
            )

        assertFalse(result.success)
        assertNotNull(result.error)
    }

    @Test
    fun `non strict mode accepts simplified registration payloads for test flows`() {
        val service =
            WebAuthnPasskeyService(
                passkeyRepository = InMemoryPasskeyRepository(),
                strictVerificationEnabled = false,
            )
        val userId = Uuid.random()
        val options = service.generateRegistrationOptions(userId, "legacy_user", "Legacy User")

        val result =
            service.verifyRegistration(
                userId = userId,
                challenge = options.challenge,
                registrationResponse =
                    PasskeyRegistrationResponse(
                        id = "test-credential",
                        rawId = "test-credential",
                        response =
                            AuthenticatorAttestationResponse(
                                clientDataJSON = "test-client-data",
                                attestationObject = "test-attestation",
                            ),
                    ),
            )

        assertTrue(result.success)
        assertNotNull(result.credentialId)
    }
}
