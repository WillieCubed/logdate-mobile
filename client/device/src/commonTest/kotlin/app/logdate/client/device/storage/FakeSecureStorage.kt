package app.logdate.client.device.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Fake implementation of SecureStorage for testing.
 * This implementation stores everything in memory.
 */
class FakeSecureStorage : SecureStorage {
    private val data = mutableMapOf<String, String>()
    private val _dataFlow = MutableStateFlow(data.toMap())
    
    override suspend fun getString(key: String): String? {
        return data[key]
    }
    
    override suspend fun putString(key: String, value: String) {
        data[key] = value
        _dataFlow.value = data.toMap()
    }
    
    override suspend fun remove(key: String) {
        data.remove(key)
        _dataFlow.value = data.toMap()
    }
    
    override suspend fun clear() {
        data.clear()
        _dataFlow.value = emptyMap()
    }
    
    override fun observeString(key: String): Flow<String?> {
        return _dataFlow.map { it[key] }
    }
    
    override fun observeAll(): Flow<Map<String, String>> {
        return _dataFlow
    }
    
    override suspend fun encrypt(data: ByteArray): ByteArray {
        // No encryption in test implementation
        return data
    }
    
    override suspend fun decrypt(data: ByteArray): ByteArray? {
        // No decryption in test implementation
        return data
    }
}