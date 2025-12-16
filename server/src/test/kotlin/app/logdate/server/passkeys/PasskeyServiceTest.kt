package app.logdate.server.passkeys

import app.logdate.shared.model.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PasskeyServiceTest {
    
    private lateinit var passkeyService: SimplePasskeyService
    private val testUserId = Uuid.random()
    private val testUsername = "testuser"
    private val testDisplayName = "Test User"
    
    @BeforeTest
    fun setup() {
        passkeyService = SimplePasskeyService()
    }
    
    @Test
    fun `generateRegistrationOptions creates valid options`() = runTest {
        val options = passkeyService.generateRegistrationOptions(
            userId = testUserId,
            username = testUsername,
            displayName = testDisplayName
        )
        
        // Verify user entity
        assertEquals(testUsername, options.user.name)
        assertEquals(testDisplayName, options.user.displayName)
        assertNotNull(options.user.id)
        
        // Verify challenge
        assertNotNull(options.challenge)
        assertTrue(options.challenge.isNotEmpty())
        
        // Verify excludeCredentials is initialized
        assertNotNull(options.excludeCredentials)
        
        // Verify timeout is reasonable
        assertTrue(options.timeout > 0)
    }
    
    @Test
    fun `generateAuthenticationOptions creates valid options`() = runTest {
        val options = passkeyService.generateAuthenticationOptions(testUserId)
        
        // Verify challenge
        assertNotNull(options.challenge)
        assertTrue(options.challenge.isNotEmpty())
        
        // Verify allowCredentials is initialized
        assertNotNull(options.allowCredentials)
        
        // Verify timeout is reasonable
        assertTrue(options.timeout > 0)
    }
    
    @Test
    fun `verifyRegistration accepts valid registration response`() = runTest {
        // First generate registration options
        val options = passkeyService.generateRegistrationOptions(testUserId, testUsername, testDisplayName)
        
        // Create a mock registration response
        val registrationResponse = PasskeyRegistrationResponse(
            id = "test-credential-id",
            rawId = "dGVzdC1jcmVkZW50aWFsLWlk", // base64url encoded "test-credential-id"
            response = AuthenticatorAttestationResponse(
                clientDataJSON = "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIiwiY2hhbGxlbmdlIjoiY2hhbGxlbmdlIiwib3JpZ2luIjoiaHR0cHM6Ly90ZXN0LmxvZ2RhdGUuYXBwIn0=",
                attestationObject = "YXR0ZXN0YXRpb25PYmplY3Q="
            )
        )
        
        // Verify the registration
        val result = passkeyService.verifyRegistration(
            userId = testUserId,
            challenge = options.challenge,
            registrationResponse = registrationResponse
        )
        
        assertTrue(result.success)
        assertEquals("test-credential-id", result.credentialId)
    }
    
    @Test
    fun `verifyAuthentication accepts valid authentication response`() = runTest {
        // First register a passkey
        val registrationOptions = passkeyService.generateRegistrationOptions(testUserId, testUsername, testDisplayName)
        val registrationResponse = PasskeyRegistrationResponse(
            id = "test-credential-id",
            rawId = "dGVzdC1jcmVkZW50aWFsLWlk",
            response = AuthenticatorAttestationResponse(
                clientDataJSON = "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIiwiY2hhbGxlbmdlIjoiY2hhbGxlbmdlIiwib3JpZ2luIjoiaHR0cHM6Ly90ZXN0LmxvZ2RhdGUuYXBwIn0=",
                attestationObject = "YXR0ZXN0YXRpb25PYmplY3Q="
            )
        )
        
        passkeyService.verifyRegistration(testUserId, registrationOptions.challenge, registrationResponse)
        
        // Now test authentication
        val authOptions = passkeyService.generateAuthenticationOptions(testUserId)
        val authResponse = PasskeyAuthenticationResponse(
            id = "test-credential-id",
            rawId = "dGVzdC1jcmVkZW50aWFsLWlk",
            response = AuthenticatorAssertionResponse(
                clientDataJSON = "eyJ0eXBlIjoid2ViYXV0aG4uZ2V0IiwiY2hhbGxlbmdlIjoiY2hhbGxlbmdlIiwib3JpZ2luIjoiaHR0cHM6Ly90ZXN0LmxvZ2RhdGUuYXBwIn0=",
                authenticatorData = "YXV0aGVudGljYXRvckRhdGE=",
                signature = "c2lnbmF0dXJl",
                userHandle = testUserId.toString()
            )
        )
        
        val result = passkeyService.verifyAuthentication(
            challenge = authOptions.challenge,
            authenticationResponse = authResponse
        )
        
        assertTrue(result.success)
        assertEquals(testUserId, result.userId)
    }
    
    @Test
    fun `verifyRegistration rejects invalid challenge`() = runTest {
        val registrationResponse = PasskeyRegistrationResponse(
            id = "test-credential-id",
            rawId = "dGVzdC1jcmVkZW50aWFsLWlk",
            response = AuthenticatorAttestationResponse(
                clientDataJSON = "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIn0=",
                attestationObject = "YXR0ZXN0YXRpb25PYmplY3Q="
            )
        )
        
        val result = passkeyService.verifyRegistration(
            userId = testUserId,
            challenge = "invalid-challenge",
            registrationResponse = registrationResponse
        )
        
        assertFalse(result.success)
        assertEquals("Invalid challenge", result.error)
    }
    
    @Test
    fun `verifyAuthentication rejects unknown credential`() = runTest {
        val authOptions = passkeyService.generateAuthenticationOptions(testUserId)
        val authResponse = PasskeyAuthenticationResponse(
            id = "unknown-credential-id",
            rawId = "dW5rbm93bi1jcmVkZW50aWFsLWlk",
            response = AuthenticatorAssertionResponse(
                clientDataJSON = "eyJ0eXBlIjoid2ViYXV0aG4uZ2V0In0=",
                authenticatorData = "YXV0aGVudGljYXRvckRhdGE=",
                signature = "c2lnbmF0dXJl",
                userHandle = testUserId.toString()
            )
        )
        
        val result = passkeyService.verifyAuthentication(
            challenge = authOptions.challenge,
            authenticationResponse = authResponse
        )
        
        assertFalse(result.success)
        assertEquals("Credential not found", result.error)
    }
    
    @Test
    fun `getUserCredentials returns empty list for new user`() = runTest {
        val credentials = passkeyService.getUserCredentials(testUserId)
        assertTrue(credentials.isEmpty())
    }
    
    @Test
    fun `getUserCredentials returns registered credentials`() = runTest {
        // Register a passkey
        val options = passkeyService.generateRegistrationOptions(testUserId, testUsername, testDisplayName)
        val registrationResponse = PasskeyRegistrationResponse(
            id = "test-credential-id",
            rawId = "dGVzdC1jcmVkZW50aWFsLWlk",
            response = AuthenticatorAttestationResponse(
                clientDataJSON = "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIn0=",
                attestationObject = "YXR0ZXN0YXRpb25PYmplY3Q="
            )
        )
        
        passkeyService.verifyRegistration(testUserId, options.challenge, registrationResponse)
        
        // Check credentials
        val credentials = passkeyService.getUserCredentials(testUserId)
        assertEquals(1, credentials.size)
        assertEquals("test-credential-id", credentials[0])
    }
    
    @Test
    fun `challenge generation creates unique challenges`() = runTest {
        val challenge1 = passkeyService.generateChallenge()
        val challenge2 = passkeyService.generateChallenge()
        
        assertNotEquals(challenge1, challenge2)
        assertTrue(challenge1.isNotEmpty())
        assertTrue(challenge2.isNotEmpty())
    }
    
    @Test
    fun `multiple registrations for same user are tracked separately`() = runTest {
        // Register first passkey
        val options1 = passkeyService.generateRegistrationOptions(testUserId, testUsername, testDisplayName)
        val response1 = PasskeyRegistrationResponse(
            id = "credential-1",
            rawId = "Y3JlZGVudGlhbC0x",
            response = AuthenticatorAttestationResponse(
                clientDataJSON = "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIn0=",
                attestationObject = "YXR0ZXN0YXRpb25PYmplY3Q="
            )
        )
        passkeyService.verifyRegistration(testUserId, options1.challenge, response1)
        
        // Register second passkey
        val options2 = passkeyService.generateRegistrationOptions(testUserId, testUsername, testDisplayName)
        val response2 = PasskeyRegistrationResponse(
            id = "credential-2", 
            rawId = "Y3JlZGVudGlhbC0y",
            response = AuthenticatorAttestationResponse(
                clientDataJSON = "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIn0=",
                attestationObject = "YXR0ZXN0YXRpb25PYmplY3Q="
            )
        )
        passkeyService.verifyRegistration(testUserId, options2.challenge, response2)
        
        // Verify both are tracked
        val credentials = passkeyService.getUserCredentials(testUserId)
        assertEquals(2, credentials.size)
        assertTrue(credentials.contains("credential-1"))
        assertTrue(credentials.contains("credential-2"))
    }
}