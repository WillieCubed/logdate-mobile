package app.logdate.client.sync.migration

import io.github.aakira.napier.Napier
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Simple implementation of MigrationStorage for testing and development.
 * This should be replaced with platform-specific implementations.
 */
class InMemoryMigrationStorage(
    private val json: Json = Json { ignoreUnknownKeys = true },
) : MigrationStorage {
    private val storage = mutableMapOf<String, String>()
    private val migrationStateKey = "migration.state"

    override suspend fun storeMigrationState(state: MigrationState) {
        try {
            val stateJson = json.encodeToString(state)
            storage[migrationStateKey] = stateJson
        } catch (e: Exception) {
            Napier.e("Failed to store migration state", e)
        }
    }

    override suspend fun retrieveMigrationState(): MigrationState? {
        val stateJson = storage[migrationStateKey] ?: return null

        return try {
            json.decodeFromString<MigrationState>(stateJson)
        } catch (e: Exception) {
            Napier.e("Failed to parse migration state", e)
            null
        }
    }

    override suspend fun clearMigrationState() {
        storage.remove(migrationStateKey)
    }
}
