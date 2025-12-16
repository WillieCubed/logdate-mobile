package app.logdate.client.device.identity

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.uuid.Uuid
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosSecureIdentityStorageTest {
    
    private lateinit var mockKeychainWrapper: KeychainWrapper
    private lateinit var storage: IosSecureIdentityStorage
    
    private val userId = randomUuid()
    private val testState = MigrationState(
        fromUserId = randomUuid(),
        toUserId = randomUuid(),
        progress = 0.5f,
        timestamp = 123456789L
    )
    
    @BeforeTest
    fun setUp() {
        mockKeychainWrapper = mockk(relaxed = true)
        
        // Default behavior for empty keychain
        every { mockKeychainWrapper.getString("app.logdate.user.id") } returns null
        every { mockKeychainWrapper.getString("app.logdate.migration.state") } returns null
        
        storage = IosSecureIdentityStorage(mockKeychainWrapper)
    }
    
    @Test
    fun `getUserId should return null when no ID is set`() = runTest {
        // When
        val userId = storage.getUserId()
        
        // Then
        assertNull(userId, "User ID should be null when not set")
        verify { mockKeychainWrapper.getString("app.logdate.user.id") }
    }
    
    @Test
    fun `setUserId should store the ID`() = runTest {
        // Given
        every { mockKeychainWrapper.storeString("app.logdate.user.id", userId.toString()) } returns true
        
        // When
        storage.setUserId(userId)
        
        // Then
        verify { mockKeychainWrapper.storeString("app.logdate.user.id", userId.toString()) }
    }
    
    @Test
    fun `getUserId should return stored ID`() = runTest {
        // Given
        every { mockKeychainWrapper.getString("app.logdate.user.id") } returns userId.toString()
        
        // When
        val retrievedId = storage.getUserId()
        
        // Then
        assertEquals(userId, retrievedId, "Should return the stored user ID")
    }
    
    @Test
    fun `getMigrationState should return null when no state is set`() = runTest {
        // When
        val state = storage.getMigrationState()
        
        // Then
        assertNull(state, "Migration state should be null when not set")
        verify { mockKeychainWrapper.getString("app.logdate.migration.state") }
    }
    
    @Test
    fun `setMigrationState should store the state`() = runTest {
        // Given
        val stateJson = Json.encodeToString(testState)
        every { mockKeychainWrapper.storeString("app.logdate.migration.state", stateJson) } returns true
        
        // When
        storage.setMigrationState(testState)
        
        // Then
        verify { mockKeychainWrapper.storeString("app.logdate.migration.state", stateJson) }
    }
    
    @Test
    fun `getMigrationState should return stored state`() = runTest {
        // Given
        val stateJson = Json.encodeToString(testState)
        every { mockKeychainWrapper.getString("app.logdate.migration.state") } returns stateJson
        
        // When
        val retrievedState = storage.getMigrationState()
        
        // Then
        assertEquals(testState, retrievedState, "Should return the stored migration state")
    }
    
    @Test
    fun `clearMigrationState should remove the state`() = runTest {
        // Given
        every { mockKeychainWrapper.remove("app.logdate.migration.state") } returns true
        
        // When
        storage.clearMigrationState()
        
        // Then
        verify { mockKeychainWrapper.remove("app.logdate.migration.state") }
    }
    
    @Test
    fun `clear should remove both user ID and migration state`() = runTest {
        // Given
        every { mockKeychainWrapper.remove("app.logdate.user.id") } returns true
        every { mockKeychainWrapper.remove("app.logdate.migration.state") } returns true
        
        // When
        storage.clear()
        
        // Then
        verify { mockKeychainWrapper.remove("app.logdate.user.id") }
        verify { mockKeychainWrapper.remove("app.logdate.migration.state") }
    }
    
    @Test
    fun `should handle invalid migration state JSON`() = runTest {
        // Given
        every { mockKeychainWrapper.getString("app.logdate.migration.state") } returns "invalid-json"
        
        // When
        val state = storage.getMigrationState()
        
        // Then
        assertNull(state, "Should return null for invalid migration state JSON")
    }
}