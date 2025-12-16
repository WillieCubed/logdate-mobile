package app.logdate.server

import app.logdate.server.auth.StubTokenService
import app.logdate.server.passkeys.SimplePasskeyService
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Simple tests to verify that the server components compile and basic functionality works.
 */
@OptIn(ExperimentalUuidApi::class)
class SimpleServerTest {
    
    @Test
    fun testStubTokenService() {
        val tokenService = StubTokenService()
        
        // Test access token generation and validation
        val accountId = "test-account"
        val accessToken = tokenService.generateAccessToken(accountId)
        assertNotNull(accessToken)
        assertTrue(accessToken.startsWith("stub_"))
        
        val validatedAccountId = tokenService.validateAccessToken(accessToken)
        assert(validatedAccountId == accountId)
        
        // Test refresh token
        val refreshToken = tokenService.generateRefreshToken(accountId)
        assertNotNull(refreshToken)
        
        val validatedRefreshAccountId = tokenService.validateRefreshToken(refreshToken)
        assert(validatedRefreshAccountId == accountId)
        
        // Test session token
        val sessionId = "test-session"
        val sessionToken = tokenService.generateSessionToken(sessionId)
        assertNotNull(sessionToken)
        
        val validatedSessionId = tokenService.validateSessionToken(sessionToken)
        assert(validatedSessionId == sessionId)
    }
    
    @Test
    fun testSimplePasskeyService() {
        val passkeyService = SimplePasskeyService()
        
        // Test registration options generation
        val userId = Uuid.random()
        val username = "testuser"
        val displayName = "Test User"
        
        val registrationOptions = passkeyService.generateRegistrationOptions(userId, username, displayName)
        assertNotNull(registrationOptions)
        assertNotNull(registrationOptions.challenge)
        assert(registrationOptions.user.name == username)
        assert(registrationOptions.user.displayName == displayName)
        
        // Test authentication options generation
        val authenticationOptions = passkeyService.generateAuthenticationOptions(userId)
        assertNotNull(authenticationOptions)
        assertNotNull(authenticationOptions.challenge)
    }
    
    @Test
    fun testServerConstantsAvailable() {
        // Test that we can access server constants
        val port = app.logdate.SERVER_PORT
        assertTrue(port > 0)
        assertTrue(port < 65536)
    }
}