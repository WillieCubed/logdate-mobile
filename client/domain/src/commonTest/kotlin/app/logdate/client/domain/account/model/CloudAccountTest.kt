package app.logdate.client.domain.account.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Tests for the CloudAccount domain model.
 */
class CloudAccountTest {
    
    @Test
    fun `CloudAccount creation with valid parameters`() {
        // Arrange
        val id = Uuid.parse("6ba7b812-9dad-11d1-80b4-00c04fd430c8")
        val username = "testuser"
        val displayName = "Test User"
        val userId = Uuid.parse("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
        val createdAt = Instant.parse("2023-01-01T00:00:00Z")
        val updatedAt = Instant.parse("2023-01-02T00:00:00Z")
        val passkeyCredentialIds = listOf(
            Uuid.parse("6ba7b813-9dad-11d1-80b4-00c04fd430c8"),
            Uuid.parse("6ba7b814-9dad-11d1-80b4-00c04fd430c8")
        )
        
        // Act
        val account = CloudAccount(
            id = id,
            username = username,
            displayName = displayName,
            userId = userId,
            createdAt = createdAt,
            updatedAt = updatedAt,
            passkeyCredentialIds = passkeyCredentialIds
        )
        
        // Assert
        assertEquals(id, account.id)
        assertEquals(username, account.username)
        assertEquals(displayName, account.displayName)
        assertEquals(userId, account.userId)
        assertEquals(createdAt, account.createdAt)
        assertEquals(updatedAt, account.updatedAt)
        assertEquals(passkeyCredentialIds, account.passkeyCredentialIds)
    }
    
    @Test
    fun `AuthenticationResult Success contains correct account and credentials`() {
        // Arrange
        val account = CloudAccount(
            id = Uuid.parse("6ba7b812-9dad-11d1-80b4-00c04fd430c8"),
            username = "testuser",
            displayName = "Test User",
            userId = Uuid.parse("6ba7b810-9dad-11d1-80b4-00c04fd430c8"),
            createdAt = Instant.parse("2023-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2023-01-02T00:00:00Z"),
            passkeyCredentialIds = listOf(Uuid.parse("6ba7b813-9dad-11d1-80b4-00c04fd430c8"))
        )
        
        val credentials = AccountCredentials(
            accessToken = "access_token_value",
            refreshToken = "refresh_token_value",
            expiresIn = 3600L
        )
        
        // Act
        val result = app.logdate.shared.model.AuthenticationResult.Success(
            account = account,
            credentials = credentials
        )
        
        // Assert
        assertEquals(account, result.account)
        assertEquals(credentials, result.credentials)
        assertEquals("access_token_value", result.credentials.accessToken)
        assertEquals("refresh_token_value", result.credentials.refreshToken)
        assertEquals(3600L, result.credentials.expiresIn)
    }
    
    @Test
    fun `AuthenticationResult Error contains correct error information`() {
        // Arrange
        val errorCode = "INVALID_CREDENTIALS"
        val message = "The provided credentials are invalid"
        
        // Act
        val result = app.logdate.shared.model.AuthenticationResult.Error(
            errorCode = errorCode,
            message = message
        )
        
        // Assert
        assertEquals(errorCode, result.errorCode)
        assertEquals(message, result.message)
    }
}
