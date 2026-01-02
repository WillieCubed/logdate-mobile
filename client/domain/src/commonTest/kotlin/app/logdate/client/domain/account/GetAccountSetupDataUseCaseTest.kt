package app.logdate.client.domain.account

import app.logdate.client.datastore.KeyValueStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GetAccountSetupDataUseCaseTest {
    
    @Test
    fun `should return empty data when no setup data exists`() = runTest {
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
    fun `should save and retrieve account setup data correctly`() = runTest {
        // Given
        val mockStorage = MockKeyValueStorage()
        val useCase = GetAccountSetupDataUseCase(mockStorage)
        val expectedData = AccountSetupData(
            username = "testuser",
            displayName = "Test User",
            email = "test@example.com"
        )
        
        // When - save data
        useCase(action = GetAccountSetupDataUseCase.Action.Save, data = expectedData)
        
        // Then - retrieve and verify data
        val result = useCase()
        assertEquals(expectedData.username, result.username)
        assertEquals(expectedData.displayName, result.displayName)
        assertEquals(expectedData.email, result.email)
    }
    
    @Test
    fun `should clear account setup data correctly`() = runTest {
        // Given
        val mockStorage = MockKeyValueStorage()
        val useCase = GetAccountSetupDataUseCase(mockStorage)
        val data = AccountSetupData(
            username = "testuser",
            displayName = "Test User",
            email = "test@example.com"
        )
        
        // When - save and then clear data
        useCase(action = GetAccountSetupDataUseCase.Action.Save, data = data)
        useCase(action = GetAccountSetupDataUseCase.Action.Clear)
        
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
        private val store = mutableMapOf<String, Any?>()
        private val stringFlows = mutableMapOf<String, MutableStateFlow<String?>>()
        private val booleanFlows = mutableMapOf<String, MutableStateFlow<Boolean>>()
        private val intFlows = mutableMapOf<String, MutableStateFlow<Int>>()
        private val longFlows = mutableMapOf<String, MutableStateFlow<Long>>()
        private val floatFlows = mutableMapOf<String, MutableStateFlow<Float>>()

        override suspend fun getString(key: String): String? = store[key] as? String

        override fun getStringSync(key: String): String? = store[key] as? String

        override suspend fun putString(key: String, value: String) {
            store[key] = value
            stringFlows.getOrPut(key) { MutableStateFlow(null) }.value = value
        }

        override suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            (store[key] as? Boolean) ?: defaultValue

        override suspend fun putBoolean(key: String, value: Boolean) {
            store[key] = value
            booleanFlows.getOrPut(key) { MutableStateFlow(value) }.value = value
        }

        override suspend fun getInt(key: String, defaultValue: Int): Int =
            (store[key] as? Int) ?: defaultValue

        override suspend fun putInt(key: String, value: Int) {
            store[key] = value
            intFlows.getOrPut(key) { MutableStateFlow(value) }.value = value
        }

        override suspend fun getLong(key: String, defaultValue: Long): Long =
            (store[key] as? Long) ?: defaultValue

        override suspend fun putLong(key: String, value: Long) {
            store[key] = value
            longFlows.getOrPut(key) { MutableStateFlow(value) }.value = value
        }

        override suspend fun getFloat(key: String, defaultValue: Float): Float =
            (store[key] as? Float) ?: defaultValue

        override suspend fun putFloat(key: String, value: Float) {
            store[key] = value
            floatFlows.getOrPut(key) { MutableStateFlow(value) }.value = value
        }

        override suspend fun remove(key: String) {
            store.remove(key)
            stringFlows[key]?.value = null
            booleanFlows[key]?.value = false
            intFlows[key]?.value = 0
            longFlows[key]?.value = 0L
            floatFlows[key]?.value = 0f
        }

        override suspend fun contains(key: String): Boolean = store.containsKey(key)

        override suspend fun clear() {
            store.clear()
            stringFlows.values.forEach { it.value = null }
            booleanFlows.values.forEach { it.value = false }
            intFlows.values.forEach { it.value = 0 }
            longFlows.values.forEach { it.value = 0L }
            floatFlows.values.forEach { it.value = 0f }
        }

        override fun observeString(key: String): Flow<String?> =
            stringFlows.getOrPut(key) { MutableStateFlow(store[key] as? String) }.asStateFlow()

        override fun observeBoolean(key: String, defaultValue: Boolean): Flow<Boolean> =
            booleanFlows.getOrPut(key) {
                MutableStateFlow((store[key] as? Boolean) ?: defaultValue)
            }.asStateFlow()

        override fun observeInt(key: String, defaultValue: Int): Flow<Int> =
            intFlows.getOrPut(key) { MutableStateFlow((store[key] as? Int) ?: defaultValue) }.asStateFlow()

        override fun observeLong(key: String, defaultValue: Long): Flow<Long> =
            longFlows.getOrPut(key) { MutableStateFlow((store[key] as? Long) ?: defaultValue) }.asStateFlow()

        override fun observeFloat(key: String, defaultValue: Float): Flow<Float> =
            floatFlows.getOrPut(key) { MutableStateFlow((store[key] as? Float) ?: defaultValue) }.asStateFlow()
    }
}
