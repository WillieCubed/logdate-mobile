package app.logdate.server.passkeys

import app.logdate.shared.model.*
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class SimplePasskeyTest {

    @Test
    fun `passkey challenge can be created`() {
        val userId = Uuid.random()
        
        val challenge = PasskeyChallenge(
            challenge = "test_challenge",
            userId = userId,
            type = "registration",
            expiresAt = "2024-01-15T10:35:00Z"
        )
        
        assertEquals("test_challenge", challenge.challenge)
        assertEquals(userId, challenge.userId)
        assertEquals("registration", challenge.type)
        assertFalse(challenge.isUsed)
    }
    
    @Test
    fun `passkey registration response can be created`() {
        val response = PasskeyRegistrationResponse(
            id = "credential123",
            rawId = "Y3JlZGVudGlhbDEyMw==",
            response = AuthenticatorAttestationResponse(
                clientDataJSON = "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIn0=",
                attestationObject = "YXR0ZXN0YXRpb25PYmplY3Q="
            )
        )
        
        assertEquals("credential123", response.id)
        assertEquals("public-key", response.type)
        assertEquals("eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIn0=", response.response.clientDataJSON)
    }
    
    @Test
    fun `passkey authentication response can be created`() {
        val response = PasskeyAuthenticationResponse(
            id = "credential123",
            rawId = "Y3JlZGVudGlhbDEyMw==",
            response = AuthenticatorAssertionResponse(
                clientDataJSON = "eyJ0eXBlIjoid2ViYXV0aG4uZ2V0In0=",
                authenticatorData = "YXV0aGVudGljYXRvckRhdGE=",
                signature = "c2lnbmF0dXJl",
                userHandle = null
            )
        )
        
        assertEquals("credential123", response.id)
        assertEquals("public-key", response.type)
        assertEquals("eyJ0eXBlIjoid2ViYXV0aG4uZ2V0In0=", response.response.clientDataJSON)
        assertEquals("c2lnbmF0dXJl", response.response.signature)
    }
    
    @Test
    fun `passkey registration options can be created`() {
        val user = PasskeyUser(
            id = "dXNlcjEyMw==",
            name = "testuser",
            displayName = "Test User"
        )
        
        val options = PasskeyRegistrationOptions(
            challenge = "Y2hhbGxlbmdl",
            user = user
        )
        
        assertEquals("Y2hhbGxlbmdl", options.challenge)
        assertEquals("testuser", options.user.name)
        assertEquals("Test User", options.user.displayName)
    }
    
    @Test
    fun `passkey authentication options can be created`() {
        val options = PasskeyAuthenticationOptions(
            challenge = "Y2hhbGxlbmdl"
        )
        
        assertEquals("Y2hhbGxlbmdl", options.challenge)
        assertTrue(options.allowCredentials.isEmpty())
    }
}