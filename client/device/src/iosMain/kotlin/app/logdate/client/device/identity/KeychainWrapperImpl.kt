package app.logdate.client.device.identity

import app.logdate.client.datastore.KeyValueStorage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

/**
 * Implementation of KeychainWrapper for iOS.
 * Uses KeyValueStorage to store secure values.
 */
class KeychainWrapperImpl(
    private val serviceName: String = "app.logdate"
) : KeychainWrapper, KoinComponent {
    
    private val storage: KeyValueStorage by inject(qualifier = named("deviceKeyValueStorage"))
    
    private fun createKey(key: String): String {
        return "keychain_${serviceName}_$key"
    }

    override fun getString(key: String): String? {
        val storageKey = createKey(key)
        return runCatching { 
            // Use non-suspend version for sync calls
            storage.getStringSync(storageKey)
        }.getOrNull()
    }
    
    override suspend fun set(value: String, key: String): Boolean {
        val storageKey = createKey(key)
        return runCatching { 
            storage.putString(storageKey, value)
            true
        }.getOrDefault(false)
    }
    
    override suspend fun remove(key: String): Boolean {
        val storageKey = createKey(key)
        return runCatching { 
            storage.remove(storageKey)
            true
        }.getOrDefault(false)
    }
}