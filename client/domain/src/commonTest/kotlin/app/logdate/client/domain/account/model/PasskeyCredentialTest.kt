package app.logdate.client.domain.account.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Tests for the PasskeyCredential domain model.
 */
class PasskeyCredentialTest {
    
    @Test
    fun `PasskeyCredential creation with full device info`() {
        // Arrange
        val credentialId = Uuid.parse("6ba7b815-9dad-11d1-80b4-00c04fd430c8")
        val nickname = "My Phone"
        val deviceInfo = DeviceInfo(
            platform = "Android",
            deviceName = "Pixel 7",
            deviceType = DeviceType.MOBILE
        )
        val createdAt = Instant.parse("2023-01-01T00:00:00Z")
        
        // Act
        val passkeyCredential = PasskeyCredential(
            credentialId = credentialId,
            nickname = nickname,
            deviceInfo = deviceInfo,
            createdAt = createdAt
        )
        
        // Assert
        assertEquals(credentialId, passkeyCredential.credentialId)
        assertEquals(nickname, passkeyCredential.nickname)
        assertEquals(deviceInfo, passkeyCredential.deviceInfo)
        assertEquals("Android", passkeyCredential.deviceInfo?.platform)
        assertEquals("Pixel 7", passkeyCredential.deviceInfo?.deviceName)
        assertEquals(DeviceType.MOBILE, passkeyCredential.deviceInfo?.deviceType)
        assertEquals(createdAt, passkeyCredential.createdAt)
    }
    
    @Test
    fun `PasskeyCredential creation with null device name`() {
        // Arrange
        val credentialId = Uuid.parse("6ba7b816-9dad-11d1-80b4-00c04fd430c8")
        val nickname = "Unknown Device"
        val deviceInfo = DeviceInfo(
            platform = "iOS",
            deviceName = null,
            deviceType = DeviceType.MOBILE
        )
        val createdAt = Instant.parse("2023-01-01T00:00:00Z")
        
        // Act
        val passkeyCredential = PasskeyCredential(
            credentialId = credentialId,
            nickname = nickname,
            deviceInfo = deviceInfo,
            createdAt = createdAt
        )
        
        // Assert
        assertEquals(credentialId, passkeyCredential.credentialId)
        assertEquals(nickname, passkeyCredential.nickname)
        assertEquals(deviceInfo, passkeyCredential.deviceInfo)
        assertEquals("iOS", passkeyCredential.deviceInfo?.platform)
        assertNull(passkeyCredential.deviceInfo?.deviceName)
        assertEquals(DeviceType.MOBILE, passkeyCredential.deviceInfo?.deviceType)
        assertEquals(createdAt, passkeyCredential.createdAt)
    }
    
    @Test
    fun `PasskeyCredential creation with null device info`() {
        // Arrange
        val credentialId = Uuid.parse("6ba7b817-9dad-11d1-80b4-00c04fd430c8")
        val nickname = "Unknown Device"
        val createdAt = Instant.parse("2023-01-01T00:00:00Z")
        
        // Act
        val passkeyCredential = PasskeyCredential(
            credentialId = credentialId,
            nickname = nickname,
            deviceInfo = null,
            createdAt = createdAt
        )
        
        // Assert
        assertEquals(credentialId, passkeyCredential.credentialId)
        assertEquals(nickname, passkeyCredential.nickname)
        assertNull(passkeyCredential.deviceInfo)
        assertEquals(createdAt, passkeyCredential.createdAt)
    }
    
    @Test
    fun `DeviceType enum contains all expected types`() {
        // Assert
        assertEquals(4, DeviceType.values().size)
        assertEquals(DeviceType.MOBILE, DeviceType.valueOf("MOBILE"))
        assertEquals(DeviceType.TABLET, DeviceType.valueOf("TABLET"))
        assertEquals(DeviceType.DESKTOP, DeviceType.valueOf("DESKTOP"))
        assertEquals(DeviceType.UNKNOWN, DeviceType.valueOf("UNKNOWN"))
    }
}
