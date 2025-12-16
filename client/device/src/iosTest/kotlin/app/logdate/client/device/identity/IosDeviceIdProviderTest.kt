package app.logdate.client.device.identity

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.uuid.Uuid
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IosDeviceIdProviderTest {
    
    private lateinit var mockKeychainWrapper: KeychainWrapper
    private lateinit var provider: IosDeviceIdProvider
    
    private val deviceIdKey = "app.logdate.device.id"
    
    @BeforeTest
    fun setUp() {
        mockKeychainWrapper = mockk()
        
        // Default behavior for empty keychain
        every { mockKeychainWrapper.getString(deviceIdKey) } returns null
        every { mockKeychainWrapper.storeString(deviceIdKey, any()) } returns true
        
        provider = IosDeviceIdProvider(mockKeychainWrapper)
    }
    
    @Test
    fun `getDeviceId should generate and store new ID if none exists`() = runTest {
        // When
        val deviceId = provider.getDeviceId().first()
        
        // Then
        verify { mockKeychainWrapper.getString(deviceIdKey) }
        verify { mockKeychainWrapper.storeString(deviceIdKey, any()) }
        assertTrue(deviceId.toString().isNotBlank(), "Device ID should not be blank")
    }
    
    @Test
    fun `getDeviceId should return stored ID if one exists`() = runTest {
        // Given
        val storedUuid = Uuid.random()
        every { mockKeychainWrapper.getString(deviceIdKey) } returns storedUuid.toString()
        
        // When
        val deviceId = provider.getDeviceId().first()
        
        // Then
        assertEquals(storedUuid, deviceId, "Should return the stored UUID")
        verify(exactly = 1) { mockKeychainWrapper.getString(deviceIdKey) }
        verify(exactly = 0) { mockKeychainWrapper.storeString(deviceIdKey, any()) }
    }
    
    @Test
    fun `getDeviceId should return consistent ID until refreshed`() = runTest {
        // When
        val firstId = provider.getDeviceId().first()
        val secondId = provider.getDeviceId().first()
        
        // Then
        assertEquals(firstId, secondId, "Device ID should remain consistent between reads")
    }
    
    @Test
    fun `refreshDeviceId should generate a new ID`() = runTest {
        // Given
        val initialId = provider.getDeviceId().first()
        
        // When
        provider.refreshDeviceId()
        val newId = provider.getDeviceId().first()
        
        // Then
        assertNotEquals(initialId, newId, "Device ID should change after refresh")
        verify(exactly = 2) { mockKeychainWrapper.storeString(deviceIdKey, any()) }
    }
    
    @Test
    fun `should recover from invalid stored UUID`() = runTest {
        // Given
        every { mockKeychainWrapper.getString(deviceIdKey) } returns "not-a-valid-uuid"
        
        // When
        val deviceId = provider.getDeviceId().first()
        
        // Then
        assertTrue(deviceId.toString().isNotBlank(), "Should have generated a valid device ID")
        verify { mockKeychainWrapper.storeString(deviceIdKey, any()) }
    }
}