package app.logdate.client.device.identity

import io.github.aakira.napier.Napier
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * iOS implementation for storing device identity data in the Keychain wrapper.
 */
class IosSecureIdentityStorage(
    private val keychainWrapper: KeychainWrapper,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private companion object {
        private const val USER_ID_KEY = "app.logdate.user.id"
        private const val MIGRATION_STATE_KEY = "app.logdate.migration.state"
    }

    suspend fun getUserId(): Uuid? =
        runCatching {
            keychainWrapper.getString(USER_ID_KEY)?.let(Uuid::parse)
        }.onFailure { Napier.e("Failed to read user ID", it) }
            .getOrNull()

    suspend fun setUserId(userId: Uuid) {
        runCatching {
            keychainWrapper.set(userId.toString(), USER_ID_KEY)
        }.onFailure { Napier.e("Failed to store user ID", it) }
    }

    suspend fun getMigrationState(): MigrationState? {
        return runCatching {
            val stateJson = keychainWrapper.getString(MIGRATION_STATE_KEY) ?: return null
            json.decodeFromString(MigrationState.serializer(), stateJson)
        }.onFailure { Napier.e("Failed to read migration state", it) }
            .getOrNull()
    }

    suspend fun setMigrationState(state: MigrationState) {
        runCatching {
            val stateJson = json.encodeToString(state)
            keychainWrapper.set(stateJson, MIGRATION_STATE_KEY)
        }.onFailure { Napier.e("Failed to store migration state", it) }
    }

    suspend fun clearMigrationState() {
        runCatching {
            keychainWrapper.remove(MIGRATION_STATE_KEY)
        }.onFailure { Napier.e("Failed to clear migration state", it) }
    }

    suspend fun clear() {
        runCatching {
            keychainWrapper.remove(USER_ID_KEY)
            keychainWrapper.remove(MIGRATION_STATE_KEY)
        }.onFailure { Napier.e("Failed to clear identity storage", it) }
    }
}
