package app.logdate.client.domain.account

import app.logdate.client.datastore.KeyValueStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GetAccountSetupDataUseCaseTest {
    
    @Test
    fun `should return empty data when no setup data exists`() {
        // Given
        val mockStorage = MockKeyValueStorage()
        val useCase = GetAccountSetupDataUseCase(mockStorage)
        
        // When
        val result = useCase()
        
        // Then
        assertEquals("", result.username)
        assertEquals("", result.displayName)
        assertNull(result.email)
    }
    
    @Test
    fun `should save and retrieve account setup data correctly`() {
        // Given
        val mockStorage = MockKeyValueStorage()
        val useCase = GetAccountSetupDataUseCase(mockStorage)
        val expectedData = AccountSetupData(
            username = "testuser",
            displayName = "Test User",
            email = "test@example.com"
        )
        
        // When - save data
        useCase.saveAccountSetupData(expectedData)
        
        // Then - retrieve and verify data
        val result = useCase()
        assertEquals(expectedData.username, result.username)
        assertEquals(expectedData.displayName, result.displayName)
        assertEquals(expectedData.email, result.email)
    }
    
    @Test
    fun `should clear account setup data correctly`() {
        // Given
        val mockStorage = MockKeyValueStorage()
        val useCase = GetAccountSetupDataUseCase(mockStorage)
        val data = AccountSetupData(
            username = "testuser",
            displayName = "Test User",
            email = "test@example.com"
        )
        
        // When - save and then clear data
        useCase.saveAccountSetupData(data)
        useCase.clearAccountSetupData()
        
        // Then - data should be cleared
        val result = useCase()
        assertEquals("", result.username)
        assertEquals("", result.displayName)
        assertNull(result.email)
    }
    
    /**
     * Mock implementation of KeyValueStorage for testing
     */
    private class MockKeyValueStorage : KeyValueStorage {
        private val store = mutableMapOf<String, Any>()
        
        override fun getString(key: String, defaultValue: String): String {
            return (store[key] as? String) ?: defaultValue
        }
        
        override fun putString(key: String, value: String) {
            store[key] = value
        }
        
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
            return (store[key] as? Boolean) ?: defaultValue
        }
        
        override fun putBoolean(key: String, value: Boolean) {
            store[key] = value
        }
        
        override fun getLong(key: String, defaultValue: Long): Long {
            return (store[key] as? Long) ?: defaultValue
        }
        
        override fun putLong(key: String, value: Long) {
            store[key] = value
        }
        
        override fun remove(key: String) {
            store.remove(key)
        }
        
        override fun clear() {
            store.clear()
        }
    }
}