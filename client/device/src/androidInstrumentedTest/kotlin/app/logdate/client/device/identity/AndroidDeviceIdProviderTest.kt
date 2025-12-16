package app.logdate.client.device.identity

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.uuid.Uuid
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@RunWith(AndroidJUnit4::class)
class AndroidDeviceIdProviderTest {

    private lateinit var mockDataStore: DataStore<Preferences>
    private lateinit var context: Context
    private lateinit var deviceIdProvider: AndroidDeviceIdProvider
    
    private val DEVICE_ID_KEY = stringPreferencesKey("device_id")
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockDataStore = mockk()
        
        // Default empty preferences
        val emptyPreferences = mockk<Preferences>()
        every { emptyPreferences[DEVICE_ID_KEY] } returns null
        coEvery { mockDataStore.data } returns flowOf(emptyPreferences)
        
        // Mock edit function
        val editSlot = slot<suspend (Preferences) -> Preferences>()
        coEvery { mockDataStore.edit(capture(editSlot)) } coAnswers {
            val transformer = editSlot.captured
            transformer(emptyPreferences)
            emptyPreferences
        }
        
        deviceIdProvider = AndroidDeviceIdProvider(context, mockDataStore)
    }
    
    @Test
    fun `getDeviceId should generate and store new ID if none exists`() = runTest {
        // When
        val deviceId = deviceIdProvider.getDeviceId().first()
        
        // Then
        coVerify { mockDataStore.edit(any()) }
        assert(deviceId.toString().isNotBlank()) { "Device ID should not be blank" }
    }
    
    @Test
    fun `getDeviceId should return consistent ID until refreshed`() = runTest {
        // When
        val firstId = deviceIdProvider.getDeviceId().first()
        val secondId = deviceIdProvider.getDeviceId().first()
        
        // Then
        assertEquals(firstId, secondId, "Device ID should remain consistent between reads")
    }
    
    @Test
    fun `refreshDeviceId should generate a new ID`() = runTest {
        // Given
        val initialId = deviceIdProvider.getDeviceId().first()
        
        // When
        deviceIdProvider.refreshDeviceId()
        val newId = deviceIdProvider.getDeviceId().first()
        
        // Then
        assertNotEquals(initialId, newId, "Device ID should change after refresh")
        coVerify(exactly = 2) { mockDataStore.edit(any()) } // Initial store + refresh
    }
    
    @Test
    fun `should recover from invalid stored UUID`() = runTest {
        // Given
        val invalidPreferences = mockk<Preferences>()
        every { invalidPreferences[DEVICE_ID_KEY] } returns "not-a-valid-uuid"
        coEvery { mockDataStore.data } returns flowOf(invalidPreferences)
        
        // When
        val provider = AndroidDeviceIdProvider(context, mockDataStore)
        val deviceId = provider.getDeviceId().first()
        
        // Then
        assert(deviceId.toString().isNotBlank()) { "Should have generated a valid device ID" }
        coVerify { mockDataStore.edit(any()) }
    }
}