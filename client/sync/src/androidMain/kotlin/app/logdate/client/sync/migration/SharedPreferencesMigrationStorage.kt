package app.logdate.client.sync.migration

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.aakira.napier.Napier
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Android implementation of MigrationStorage using SharedPreferences.
 */
class SharedPreferencesMigrationStorage(
    context: Context,
    private val json: Json,
) : MigrationStorage {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "app.logdate.migration", Context.MODE_PRIVATE
    )
    
    private val MIGRATION_STATE_KEY = "migration.state"
    
    override suspend fun storeMigrationState(state: MigrationState) {
        try {
            val stateJson = json.encodeToString(state)
            prefs.edit { putString(MIGRATION_STATE_KEY, stateJson) }
        } catch (e: Exception) {
            Napier.e("Failed to store migration state", e)
        }
    }
    
    override suspend fun retrieveMigrationState(): MigrationState? {
        val stateJson = prefs.getString(MIGRATION_STATE_KEY, null) ?: return null
        
        return try {
            json.decodeFromString<MigrationState>(stateJson)
        } catch (e: Exception) {
            Napier.e("Failed to parse migration state", e)
            null
        }
    }
    
    override suspend fun clearMigrationState() {
        prefs.edit { remove(MIGRATION_STATE_KEY) }
    }
}