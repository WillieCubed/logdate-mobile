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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.uuid.Uuid
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class AndroidSecureIdentityStorageTest {

    private lateinit var mockDataStore: DataStore<Preferences>
    private lateinit var context: Context
    private lateinit var storage: AndroidSecureIdentityStorage
    
    private val USER_ID_KEY = stringPreferencesKey("user_identity_id")
    private val MIGRATION_STATE_KEY = stringPreferencesKey("migration_state")
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockDataStore = mockk()
        
        // Default empty preferences
        val emptyPreferences = mockk<Preferences>()
        every { emptyPreferences[USER_ID_KEY] } returns null
        every { emptyPreferences[MIGRATION_STATE_KEY] } returns null
        coEvery { mockDataStore.data } returns flowOf(emptyPreferences)
        
        // Mock edit function
        val editSlot = slot<suspend (Preferences) -> Preferences>()
        coEvery { mockDataStore.edit(capture(editSlot)) } coAnswers {
            val transformer = editSlot.captured
            transformer(emptyPreferences)
            emptyPreferences
        }
        
        storage = AndroidSecureIdentityStorage(mockDataStore)
    }
    
    @Test
    fun `getUserId should return null when no ID is set`() = runTest {
        // When
        val userId = storage.getUserId()
        
        // Then
        assertNull(userId, "User ID should be null when not set")
    }
    
    @Test
    fun `setUserId should store the ID`() = runTest {
        // Given
        val testId = randomUuid()
        val preferencesWithUserId = mockk<Preferences>()
        every { preferencesWithUserId[USER_ID_KEY] } returns testId.toString()
        every { preferencesWithUserId[MIGRATION_STATE_KEY] } returns null
        
        // After setting the ID, return updated preferences
        coEvery { mockDataStore.data } returns flowOf(emptyPreferences) andThen flowOf(preferencesWithUserId)
        
        // When
        storage.setUserId(testId)
        
        // Then
        coVerify { mockDataStore.edit(any()) }
    }
    
    @Test
    fun `getMigrationState should return null when no state is set`() = runTest {
        // When
        val state = storage.getMigrationState()
        
        // Then
        assertNull(state, "Migration state should be null when not set")
    }
    
    @Test
    fun `setMigrationState should store the state`() = runTest {
        // Given
        val testState = MigrationState(
            fromUserId = randomUuid(),
            toUserId = randomUuid(),
            progress = 0.5f,
            timestamp = 123456789L
        )
        
        val stateJson = Json.encodeToString(testState)
        val preferencesWithState = mockk<Preferences>()
        every { preferencesWithState[USER_ID_KEY] } returns null
        every { preferencesWithState[MIGRATION_STATE_KEY] } returns stateJson
        
        // After setting the state, return updated preferences
        coEvery { mockDataStore.data } returns flowOf(emptyPreferences) andThen flowOf(preferencesWithState)
        
        // When
        storage.setMigrationState(testState)
        
        // Then
        coVerify { mockDataStore.edit(any()) }
    }
    
    @Test
    fun `clearMigrationState should remove the state`() = runTest {
        // Given
        val testState = MigrationState(
            fromUserId = randomUuid(),
            toUserId = randomUuid(),
            progress = 0.5f,
            timestamp = 123456789L
        )
        val stateJson = Json.encodeToString(testState)
        
        // Preferences with state
        val preferencesWithState = mockk<Preferences>()
        every { preferencesWithState[USER_ID_KEY] } returns null
        every { preferencesWithState[MIGRATION_STATE_KEY] } returns stateJson
        
        // After clearing, return empty preferences
        coEvery { mockDataStore.data } returns flowOf(preferencesWithState) andThen flowOf(emptyPreferences)
        
        // When
        storage.clearMigrationState()
        
        // Then
        coVerify { mockDataStore.edit(any()) }
    }
    
    @Test
    fun `clear should remove both user ID and migration state`() = runTest {
        // Given
        val testId = randomUuid()
        val testState = MigrationState(
            fromUserId = randomUuid(),
            toUserId = randomUuid(),
            progress = 0.5f,
            timestamp = 123456789L
        )
        val stateJson = Json.encodeToString(testState)
        
        // Preferences with data
        val preferencesWithData = mockk<Preferences>()
        every { preferencesWithData[USER_ID_KEY] } returns testId.toString()
        every { preferencesWithData[MIGRATION_STATE_KEY] } returns stateJson
        
        // After clearing, return empty preferences
        coEvery { mockDataStore.data } returns flowOf(preferencesWithData) andThen flowOf(emptyPreferences)
        
        // When
        storage.clear()
        
        // Then
        coVerify { mockDataStore.edit(any()) }
    }
}