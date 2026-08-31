package app.logdate.client.datastore.featureflags

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Reads and writes [FeatureFlag] values.
 *
 * Flags live in the same preferences as the settings a user sets directly, under the key each flag
 * declares. That is deliberate: the three flags that predate this type were already stored that
 * way and are read through their own accessors elsewhere, so both paths see the same value rather
 * than disagreeing about whether a feature is on.
 */
interface FeatureFlagStore {
    /**
     * Observes whether [flag] is enabled, starting from its default for anyone who has never set
     * it.
     */
    fun observe(flag: FeatureFlag): Flow<Boolean>

    /**
     * Reads whether [flag] is currently enabled.
     *
     * Prefer [observe] where the caller can react to a change; this is for one-shot decisions.
     */
    suspend fun isEnabled(flag: FeatureFlag): Boolean = observe(flag).first()

    /**
     * Turns [flag] on or off for this installation.
     */
    suspend fun setEnabled(
        flag: FeatureFlag,
        enabled: Boolean,
    )
}

/**
 * The [FeatureFlagStore] backed by the app's preference store.
 */
class DataStoreFeatureFlagStore(
    private val userPreferences: DataStore<Preferences>,
) : FeatureFlagStore {
    override fun observe(flag: FeatureFlag): Flow<Boolean> =
        userPreferences.data.map { preferences ->
            preferences[booleanPreferencesKey(flag.key)] ?: flag.defaultEnabled
        }

    override suspend fun setEnabled(
        flag: FeatureFlag,
        enabled: Boolean,
    ) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[booleanPreferencesKey(flag.key)] = enabled
            }
        }
    }
}
