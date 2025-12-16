package app.logdate.client.device.identity

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class DesktopDeviceIdProviderTest {
    
    private lateinit var mockConfigDir: Path
    private lateinit var mockDeviceIdFile: File
    private lateinit var provider: DesktopDeviceIdProvider
    
    @BeforeTest
    fun setUp() {
        // Create a temporary directory for tests
        mockConfigDir = Files.createTempDirectory("logdate_test_")
        mockDeviceIdFile = File(mockConfigDir.toFile(), "device_id.dat")
        
        // Mock the userHomeDir to use our temporary directory
        mockkStatic("kotlin.io.FilesKt")
        every { System.getProperty("user.home") } returns mockConfigDir.toString()
        
        provider = DesktopDeviceIdProvider()
    }
    
    @AfterTest
    fun tearDown() {
        unmockkStatic("kotlin.io.FilesKt")
        // Clean up temporary files
        mockDeviceIdFile.delete()
        mockConfigDir.toFile().delete()
    }
    
    @Test
    fun `getDeviceId should generate and store new ID if none exists`() = runTest {
        // When
        val deviceId = provider.getDeviceId().first()
        
        // Then
        assertTrue(mockDeviceIdFile.exists(), "Device ID file should be created")
        assertTrue(mockDeviceIdFile.readText().isNotBlank(), "Device ID file should not be empty")
        assertTrue(deviceId.toString().isNotBlank(), "Device ID should not be blank")
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
    }
    
    @Test
    fun `should recover from invalid stored UUID`() = runTest {
        // Given - Create a file with invalid content
        mockDeviceIdFile.writeText("not-a-valid-uuid")
        
        // When
        val newProvider = DesktopDeviceIdProvider()
        val deviceId = newProvider.getDeviceId().first()
        
        // Then
        assertTrue(deviceId.toString().isNotBlank(), "Should have generated a valid device ID")
    }
}