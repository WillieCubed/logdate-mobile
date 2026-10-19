package app.logdate.client.datastore

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences

/** Removes obsolete credential duplicates; the canonical secure session is managed separately. */
class LegacyCloudCredentialMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData.asMap().keys.any { isCredential(it.name) }

    override suspend fun migrate(currentData: Preferences): Preferences = withoutCredentials(currentData)

    override suspend fun cleanUp() = Unit

    companion object {
        fun withoutCredentials(preferences: Preferences): Preferences =
            preferences.toMutablePreferences().apply {
                asMap().keys.filter { isCredential(it.name) }.forEach { remove(it) }
            }

        private fun isCredential(name: String): Boolean =
            name == "cloud_access_token" ||
                name.startsWith("cloud_access_token_") ||
                name == "cloud_refresh_token" ||
                name.startsWith("cloud_refresh_token_") ||
                name == "access_token" ||
                name.startsWith("access_token_") ||
                name == "refresh_token" ||
                name.startsWith("refresh_token_")
    }
}
