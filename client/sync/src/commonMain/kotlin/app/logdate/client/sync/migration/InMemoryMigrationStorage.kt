package app.logdate.client.sync.migration

import io.github.aakira.napier.Napier
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Simple implementation of MigrationStorage for testing and development.
 * This should be replaced with platform-specific implementations.
 */
class InMemoryMigrationStorage(
    private val json: Json = Json { ignoreUnknownKeys = true }
) : MigrationStorage {

    private val storage = mutableMapOf<String, String>()
    private val MIGRATION_STATE_KEY = "migration.state"
    
    override suspend fun storeMigrationState(state: MigrationState) {
        try {
            val stateJson = json.encodeToString(state)
            storage[MIGRATION_STATE_KEY] = stateJson
        } catch (e: Exception) {
            Napier.e("Failed to store migration state", e)
        }
    }
    
    override suspend fun retrieveMigrationState(): MigrationState? {
        val stateJson = storage[MIGRATION_STATE_KEY] ?: return null
        
        return try {
            json.decodeFromString<MigrationState>(stateJson)
        } catch (e: Exception) {
            Napier.e("Failed to parse migration state", e)
            null
        }
    }
    
    override suspend fun clearMigrationState() {
        storage.remove(MIGRATION_STATE_KEY)
    }
}