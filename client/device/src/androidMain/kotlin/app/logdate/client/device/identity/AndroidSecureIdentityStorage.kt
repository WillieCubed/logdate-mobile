package app.logdate.client.device.identity

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * Android implementation for storing device identity data in DataStore.
 */
class AndroidSecureIdentityStorage(
    private val dataStore: DataStore<Preferences>,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private companion object {
        private val USER_ID_KEY = stringPreferencesKey("user_identity_id")
        private val MIGRATION_STATE_KEY = stringPreferencesKey("migration_state")
    }

    suspend fun getUserId(): Uuid? {
        return runCatching {
            val stored = dataStore.data.first()[USER_ID_KEY]
            stored?.let(Uuid::parse)
        }.onFailure { Napier.e("Failed to read user ID", it) }
            .getOrNull()
    }

    suspend fun setUserId(userId: Uuid) {
        runCatching {
            dataStore.edit { preferences ->
                preferences[USER_ID_KEY] = userId.toString()
            }
        }.onFailure { Napier.e("Failed to store user ID", it) }
    }

    suspend fun getMigrationState(): MigrationState? {
        return runCatching {
            val stateJson = dataStore.data.first()[MIGRATION_STATE_KEY] ?: return null
            json.decodeFromString(MigrationState.serializer(), stateJson)
        }.onFailure { Napier.e("Failed to read migration state", it) }
            .getOrNull()
    }

    suspend fun setMigrationState(state: MigrationState) {
        runCatching {
            val stateJson = json.encodeToString(state)
            dataStore.edit { preferences ->
                preferences[MIGRATION_STATE_KEY] = stateJson
            }
        }.onFailure { Napier.e("Failed to store migration state", it) }
    }

    suspend fun clearMigrationState() {
        runCatching {
            dataStore.edit { preferences ->
                preferences.remove(MIGRATION_STATE_KEY)
            }
        }.onFailure { Napier.e("Failed to clear migration state", it) }
    }

    suspend fun clear() {
        runCatching {
            dataStore.edit { preferences ->
                preferences.remove(USER_ID_KEY)
                preferences.remove(MIGRATION_STATE_KEY)
            }
        }.onFailure { Napier.e("Failed to clear identity storage", it) }
    }
}
