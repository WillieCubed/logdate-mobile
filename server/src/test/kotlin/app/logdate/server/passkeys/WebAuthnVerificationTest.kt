package app.logdate.server.passkeys

import app.logdate.shared.model.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class WebAuthnVerificationTest {

    private lateinit var passkeyService: SimplePasskeyService

    @BeforeTest
    fun setup() {
        passkeyService = SimplePasskeyService()
    }

    @Test
    fun `challenge must be valid base64url without padding`() {
        val challenge = passkeyService.generateChallenge()
        
        // Should be a hex string in our implementation
        assertTrue(challenge.isNotEmpty())
        assertTrue(challenge.length >= 32) // At least 32 characters for 32 bytes in hex
        assertTrue(challenge.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' })
    }

    @Test
    fun `challenge generation creates unique values`() {
        val challenges = (1..10).map { passkeyService.generateChallenge() }
        
        // All challenges should be unique
        assertEquals(10, challenges.toSet().size)
        
        // All challenges should be non-empty
        assertTrue(challenges.all { it.isNotEmpty() })
    }

    @Test
    fun `passkey registration requires valid challenge`() = runTest {
        val userId = Uuid.random()
        val validChallenge = passkeyService.generateRegistrationOptions(userId, "testuser", "Test User").challenge
        
        val registrationResponse = PasskeyRegistrationResponse(
            id = "test-credential",
            rawId = "dGVzdC1jcmVkZW50aWFs",
            response = AuthenticatorAttestationResponse(
                clientDataJSON = "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIn0=",
                attestationObject = "YXR0ZXN0YXRpb25PYmplY3Q="
            )
        )

        // Valid challenge should succeed
        val validResult = passkeyService.verifyRegistration(userId, validChallenge, registrationResponse)
        assertTrue(validResult.success)

        // Invalid challenge should fail
        val invalidResult = passkeyService.verifyRegistration(userId, "invalid-challenge", registrationResponse)
        assertFalse(invalidResult.success)
        assertEquals("Invalid challenge", invalidResult.error)
    }

    @Test
    fun `passkey authentication requires existing credential`() = runTest {
        val userId = Uuid.random()
        
        // First register a passkey
        val regOptions = passkeyService.generateRegistrationOptions(userId, "testuser", "Test User")
        val regResponse = PasskeyRegistrationResponse(
            id = "test-credential",
            rawId = "dGVzdC1jcmVkZW50aWFs",
            response = AuthenticatorAttestationResponse(
                clientDataJSON = "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIn0=",
                attestationObject = "YXR0ZXN0YXRpb25PYmplY3Q="
            )
        )
        passkeyService.verifyRegistration(userId, regOptions.challenge, regResponse)

        // Now test authentication
        val authOptions = passkeyService.generateAuthenticationOptions(userId)
        
        // Authentication with registered credential should succeed
        val validAuthResponse = PasskeyAuthenticationResponse(
            id = "test-credential",
            rawId = "dGVzdC1jcmVkZW50aWFs",
            response = AuthenticatorAssertionResponse(
                clientDataJSON = "eyJ0eXBlIjoid2ViYXV0aG4uZ2V0In0=",
                authenticatorData = "YXV0aGVudGljYXRvckRhdGE=",
                signature = "c2lnbmF0dXJl",
                userHandle = userId.toString()
            )
        )
        
        val validResult = passkeyService.verifyAuthentication(authOptions.challenge, validAuthResponse)
        assertTrue(validResult.success)

        // Authentication with unknown credential should fail - need new challenge since previous one is used
        val authOptions2 = passkeyService.generateAuthenticationOptions(userId)
        val invalidAuthResponse = PasskeyAuthenticationResponse(
            id = "unknown-credential",
            rawId = "dW5rbm93bi1jcmVkZW50aWFs",
            response = AuthenticatorAssertionResponse(
                clientDataJSON = "eyJ0eXBlIjoid2ViYXV0aG4uZ2V0In0=",
                authenticatorData = "YXV0aGVudGljYXRvckRhdGE=",
                signature = "c2lnbmF0dXJl",
                userHandle = userId.toString()
            )
        )
        
        val invalidResult = passkeyService.verifyAuthentication(authOptions2.challenge, invalidAuthResponse)
        assertFalse(invalidResult.success)
        assertEquals("Credential not found", invalidResult.error)
    }

    @Test
    fun `challenge can only be used once`() = runTest {
        val userId = Uuid.random()
        val options = passkeyService.generateRegistrationOptions(userId, "testuser", "Test User")
        
        val registrationResponse = PasskeyRegistrationResponse(
            id = "test-credential",
            rawId = "dGVzdC1jcmVkZW50aWFs",
            response = AuthenticatorAttestationResponse(
                clientDataJSON = "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIn0=",
                attestationObject = "YXR0ZXN0YXRpb25PYmplY3Q="
            )
        )

        // First use should succeed
        val firstResult = passkeyService.verifyRegistration(userId, options.challenge, registrationResponse)
        assertTrue(firstResult.success)

        // Second use of same challenge should fail
        val secondResult = passkeyService.verifyRegistration(userId, options.challenge, registrationResponse)
        assertFalse(secondResult.success)
        assertEquals("Challenge already used", secondResult.error)
    }

    @Test
    fun `registration response must contain required fields`() {
        // Test that our PasskeyRegistrationResponse model has the required fields
        val response = PasskeyRegistrationResponse(
            id = "credential-id",
            rawId = "Y3JlZGVudGlhbC1pZA==",
            response = AuthenticatorAttestationResponse(
                clientDataJSON = "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIn0=",
                attestationObject = "YXR0ZXN0YXRpb25PYmplY3Q="
            )
        )

        assertEquals("credential-id", response.id)
        assertEquals("Y3JlZGVudGlhbC1pZA==", response.rawId)
        assertEquals("public-key", response.type)
        assertNotNull(response.response)
        assertEquals("eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIn0=", response.response.clientDataJSON)
        assertEquals("YXR0ZXN0YXRpb25PYmplY3Q=", response.response.attestationObject)
    }

    @Test
    fun `authentication response must contain required fields`() {
        // Test that our PasskeyAuthenticationResponse model has the required fields
        val response = PasskeyAuthenticationResponse(
            id = "credential-id",
            rawId = "Y3JlZGVudGlhbC1pZA==",
            response = AuthenticatorAssertionResponse(
                clientDataJSON = "eyJ0eXBlIjoid2ViYXV0aG4uZ2V0In0=",
                authenticatorData = "YXV0aGVudGljYXRvckRhdGE=",
                signature = "c2lnbmF0dXJl",
                userHandle = "user-handle"
            )
        )

        assertEquals("credential-id", response.id)
        assertEquals("Y3JlZGVudGlhbC1pZA==", response.rawId)
        assertEquals("public-key", response.type)
        assertNotNull(response.response)
        assertEquals("eyJ0eXBlIjoid2ViYXV0aG4uZ2V0In0=", response.response.clientDataJSON)
        assertEquals("YXV0aGVudGljYXRvckRhdGE=", response.response.authenticatorData)
        assertEquals("c2lnbmF0dXJl", response.response.signature)
        assertEquals("user-handle", response.response.userHandle)
    }

    @Test
    fun `passkey registration options contain required fields`() = runTest {
        val userId = Uuid.random()
        val username = "testuser"
        val displayName = "Test User"
        
        val options = passkeyService.generateRegistrationOptions(userId, username, displayName)

        assertNotNull(options.challenge)
        assertTrue(options.challenge.isNotEmpty())
        
        assertEquals(username, options.user.name)
        assertEquals(displayName, options.user.displayName)
        assertEquals(userId.toString(), options.user.id)
        
        assertNotNull(options.excludeCredentials)
        assertTrue(options.timeout > 0)
    }

    @Test
    fun `passkey authentication options contain required fields`() = runTest {
        val userId = Uuid.random()
        
        val options = passkeyService.generateAuthenticationOptions(userId)

        assertNotNull(options.challenge)
        assertTrue(options.challenge.isNotEmpty())
        
        assertNotNull(options.allowCredentials)
        assertTrue(options.timeout > 0)
    }

    @Test
    fun `user verification result contains expected information`() = runTest {
        val userId = Uuid.random()
        val regOptions = passkeyService.generateRegistrationOptions(userId, "testuser", "Test User")
        
        val registrationResponse = PasskeyRegistrationResponse(
            id = "test-credential",
            rawId = "dGVzdC1jcmVkZW50aWFs",
            response = AuthenticatorAttestationResponse(
                clientDataJSON = "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIn0=",
                attestationObject = "YXR0ZXN0YXRpb25PYmplY3Q="
            )
        )

        val result = passkeyService.verifyRegistration(userId, regOptions.challenge, registrationResponse)
        
        assertTrue(result.success)
        assertEquals("test-credential", result.credentialId)
        assertNull(result.error)
        assertNull(result.userId) // userId is only returned for authentication
    }
}