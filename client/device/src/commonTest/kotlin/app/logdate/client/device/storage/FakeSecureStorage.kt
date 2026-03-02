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
    private val dataFlow = MutableStateFlow(data.toMap())

    override suspend fun getString(key: String): String? = data[key]

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        data[key] = value
        dataFlow.value = data.toMap()
    }

    override suspend fun remove(key: String) {
        data.remove(key)
        dataFlow.value = data.toMap()
    }

    override suspend fun clear() {
        data.clear()
        dataFlow.value = emptyMap()
    }

    override fun observeString(key: String): Flow<String?> = dataFlow.map { it[key] }

    override fun observeAll(): Flow<Map<String, String>> = dataFlow

    override suspend fun encrypt(data: ByteArray): ByteArray {
        // No encryption in test implementation
        return data
    }

    override suspend fun decrypt(data: ByteArray): ByteArray? {
        // No decryption in test implementation
        return data
    }
}
