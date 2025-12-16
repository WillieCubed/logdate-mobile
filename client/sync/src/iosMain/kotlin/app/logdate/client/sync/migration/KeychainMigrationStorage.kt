package app.logdate.client.sync.migration

import io.github.aakira.napier.Napier
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

/**
 * iOS implementation of MigrationStorage using NSUserDefaults.
 * 
 * Note: In a real implementation, you'd want to use KeychainWrapper for secure storage,
 * but NSUserDefaults is simpler for this example.
 */
class KeychainMigrationStorage(
    private val json: Json
) : MigrationStorage {
    
    private val defaults = NSUserDefaults.standardUserDefaults
    private val MIGRATION_STATE_KEY = "app.logdate.migration.state"
    
    override suspend fun storeMigrationState(state: MigrationState) {
        try {
            val stateJson = json.encodeToString(state)
            defaults.setObject(stateJson, MIGRATION_STATE_KEY)
        } catch (e: Exception) {
            Napier.e("Failed to store migration state", e)
        }
    }
    
    override suspend fun retrieveMigrationState(): MigrationState? {
        val stateJson = defaults.stringForKey(MIGRATION_STATE_KEY) ?: return null
        
        return try {
            json.decodeFromString<MigrationState>(stateJson)
        } catch (e: Exception) {
            Napier.e("Failed to parse migration state", e)
            null
        }
    }
    
    override suspend fun clearMigrationState() {
        defaults.removeObjectForKey(MIGRATION_STATE_KEY)
    }
}