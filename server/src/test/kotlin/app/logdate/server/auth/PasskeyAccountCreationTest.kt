package app.logdate.server.auth

import app.logdate.server.passkeys.SimplePasskeyService
import app.logdate.shared.model.AuthenticatorAssertionResponse
import app.logdate.shared.model.AuthenticatorAttestationResponse
import app.logdate.shared.model.PasskeyAuthenticationResponse
import app.logdate.shared.model.PasskeyRegistrationResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Integration tests for the end-to-end account creation workflow using passkeys.
 *
 * This class coordinates multiple components—repositories, passkey services, and
 * session managers—to simulate the full user journey from generating registration
 * options to successfully persisting a new account and its associated credentials.
 */
@OptIn(ExperimentalUuidApi::class)
class PasskeyAccountCreationTest {
    private lateinit var accountRepository: AccountRepository
    private lateinit var passkeyService: SimplePasskeyService
    private lateinit var sessionManager: SessionManager

    @BeforeTest
    fun setup() {
        accountRepository = InMemoryAccountRepository()
        passkeyService = SimplePasskeyService()
        sessionManager = InMemorySessionManager()
    }

    @Test
    fun `complete account creation workflow succeeds`() =
        runTest {
            val username = "testuser"
            val displayName = "Test User"
            val userId = Uuid.random()

            // Step 1: Generate registration options
            val registrationOptions =
                passkeyService.generateRegistrationOptions(
                    userId = userId,
                    username = username,
                    displayName = displayName,
                )

            assertNotNull(registrationOptions.challenge)
            assertEquals(username, registrationOptions.user.name)
            assertEquals(displayName, registrationOptions.user.displayName)

            // Step 2: Create a mock registration response (simulating client)
            val registrationResponse =
                PasskeyRegistrationResponse(
                    id = "test-credential-id",
                    rawId = "dGVzdC1jcmVkZW50aWFsLWlk",
                    response =
                        AuthenticatorAttestationResponse(
                            clientDataJSON =
                                "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIiwiY2hhbGxlbmdlIjoiY2hhbGxlbmdlIiwi" +
                                    "b3JpZ2luIjoiaHR0cHM6Ly90ZXN0LmxvZ2RhdGUuYXBwIn0=",
                            attestationObject = "YXR0ZXN0YXRpb25PYmplY3Q=",
                        ),
                )

            // Step 3: Verify registration
            val verificationResult =
                passkeyService.verifyRegistration(
                    userId = userId,
                    challenge = registrationOptions.challenge,
                    registrationResponse = registrationResponse,
                )

            assertTrue(verificationResult.success)
            assertEquals("test-credential-id", verificationResult.credentialId)

            // Step 4: Create account
            val account =
                Account(
                    id = userId,
                    username = username,
                    displayName = displayName,
                    createdAt = Clock.System.now(),
                    lastSignInAt = null,
                    timezone = null,
                    locale = null,
                    isActive = true,
                )

            val savedAccount = accountRepository.save(account)
            assertEquals(userId, savedAccount.id)
            assertEquals(username, savedAccount.username)

            // Step 5: Verify the passkey is associated with the user
            val userCredentials = passkeyService.getUserCredentials(userId)
            assertEquals(1, userCredentials.size)
            assertEquals("test-credential-id", userCredentials[0])
        }

    @Test
    fun `account creation fails with invalid challenge`() =
        runTest {
            val username = "testuser"
            val displayName = "Test User"
            val userId = Uuid.random()

            // Create registration response with invalid challenge
            val registrationResponse =
                PasskeyRegistrationResponse(
                    id = "test-credential-id",
                    rawId = "dGVzdC1jcmVkZW50aWFsLWlk",
                    response =
                        AuthenticatorAttestationResponse(
                            clientDataJSON = "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIn0=",
                            attestationObject = "YXR0ZXN0YXRpb25PYmplY3Q=",
                        ),
                )

            // Try to verify with invalid challenge
            val result =
                passkeyService.verifyRegistration(
                    userId = userId,
                    challenge = "invalid-challenge",
                    registrationResponse = registrationResponse,
                )

            assertFalse(result.success)
            assertEquals("Invalid challenge", result.error)
        }

    @Test
    fun `account creation prevents duplicate usernames`() =
        runTest {
            val username = "testuser"
            val displayName = "Test User"

            // Create first account
            val userId1 = Uuid.random()
            val account1 =
                Account(
                    id = userId1,
                    username = username,
                    displayName = displayName,
                    createdAt = Clock.System.now(),
                )
            accountRepository.save(account1)

            // Try to create second account with same username
            val usernameExists = accountRepository.usernameExists(username)
            assertTrue(usernameExists)
        }

    @Test
    fun `authentication workflow succeeds after registration`() =
        runTest {
            val username = "testuser"
            val displayName = "Test User"
            val userId = Uuid.random()

            // First, complete registration
            val registrationOptions = passkeyService.generateRegistrationOptions(userId, username, displayName)
            val registrationResponse =
                PasskeyRegistrationResponse(
                    id = "test-credential-id",
                    rawId = "dGVzdC1jcmVkZW50aWFsLWlk",
                    response =
                        AuthenticatorAttestationResponse(
                            clientDataJSON = "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIn0=",
                            attestationObject = "YXR0ZXN0YXRpb25PYmplY3Q=",
                        ),
                )

            val regResult = passkeyService.verifyRegistration(userId, registrationOptions.challenge, registrationResponse)
            assertTrue(regResult.success)

            // Now test authentication
            val authOptions = passkeyService.generateAuthenticationOptions(userId)
            val authResponse =
                PasskeyAuthenticationResponse(
                    id = "test-credential-id",
                    rawId = "dGVzdC1jcmVkZW50aWFsLWlk",
                    response =
                        AuthenticatorAssertionResponse(
                            clientDataJSON = "eyJ0eXBlIjoid2ViYXV0aG4uZ2V0In0=",
                            authenticatorData = "YXV0aGVudGljYXRvckRhdGE=",
                            signature = "c2lnbmF0dXJl",
                            userHandle = userId.toString(),
                        ),
                )

            val authResult = passkeyService.verifyAuthentication(authOptions.challenge, authResponse)
            assertTrue(authResult.success)
            assertEquals(userId, authResult.userId)
        }

    @Test
    fun `session manager stores and retrieves sessions`() =
        runTest {
            val sessionId = "test-session-123"
            val userId = Uuid.random()
            val session =
                TemporarySession(
                    id = sessionId,
                    temporaryUserId = userId,
                    challenge = "test-challenge",
                    username = "testuser",
                    displayName = "Test User",
                    bio = null,
                    deviceInfo =
                        DeviceInfo(
                            platform = "android",
                            deviceName = "Test Device",
                            osVersion = "14",
                            appVersion = "1.0.0",
                        ),
                    sessionType = SessionType.ACCOUNT_CREATION,
                    createdAt = Clock.System.now(),
                    expiresAt = Clock.System.now().plus(kotlin.time.Duration.parse("PT15M")),
                )

            val storedSessionId = sessionManager.storeSession(session)
            assertEquals(sessionId, storedSessionId)
            val retrievedSession = sessionManager.getSession(sessionId)

            assertNotNull(retrievedSession)
            assertEquals(sessionId, retrievedSession.id)
            assertEquals(userId, retrievedSession.temporaryUserId)
            assertEquals("testuser", retrievedSession.username)
            assertEquals(SessionType.ACCOUNT_CREATION, retrievedSession.sessionType)
        }

    @Test
    fun `expired sessions are rejected`() =
        runTest {
            val sessionId = "expired-session"
            val userId = Uuid.random()
            val expiredSession =
                TemporarySession(
                    id = sessionId,
                    temporaryUserId = userId,
                    challenge = "test-challenge",
                    username = "testuser",
                    displayName = "Test User",
                    bio = null,
                    deviceInfo = null,
                    sessionType = SessionType.ACCOUNT_CREATION,
                    createdAt = Clock.System.now().minus(kotlin.time.Duration.parse("PT1H")),
                    expiresAt = Clock.System.now().minus(kotlin.time.Duration.parse("PT30M")),
                )

            sessionManager.storeSession(expiredSession)
            val retrievedSession = sessionManager.getSession(sessionId)

            // Session manager should return null for expired sessions
            assertNull(retrievedSession)
        }

    @Test
    fun `multiple passkeys can be registered for same user`() =
        runTest {
            val username = "testuser"
            val displayName = "Test User"
            val userId = Uuid.random()

            // Register first passkey
            val options1 = passkeyService.generateRegistrationOptions(userId, username, displayName)
            val response1 =
                PasskeyRegistrationResponse(
                    id = "credential-1",
                    rawId = "Y3JlZGVudGlhbC0x",
                    response =
                        AuthenticatorAttestationResponse(
                            clientDataJSON = "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIn0=",
                            attestationObject = "YXR0ZXN0YXRpb25PYmplY3Q=",
                        ),
                )
            val result1 = passkeyService.verifyRegistration(userId, options1.challenge, response1)
            assertTrue(result1.success)

            // Register second passkey
            val options2 = passkeyService.generateRegistrationOptions(userId, username, displayName)
            val response2 =
                PasskeyRegistrationResponse(
                    id = "credential-2",
                    rawId = "Y3JlZGVudGlhbC0y",
                    response =
                        AuthenticatorAttestationResponse(
                            clientDataJSON = "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIn0=",
                            attestationObject = "YXR0ZXN0YXRpb25PYmplY3Q=",
                        ),
                )
            val result2 = passkeyService.verifyRegistration(userId, options2.challenge, response2)
            assertTrue(result2.success)

            // Verify both credentials are tracked
            val credentials = passkeyService.getUserCredentials(userId)
            assertEquals(2, credentials.size)
            assertTrue(credentials.contains("credential-1"))
            assertTrue(credentials.contains("credential-2"))
        }
}
