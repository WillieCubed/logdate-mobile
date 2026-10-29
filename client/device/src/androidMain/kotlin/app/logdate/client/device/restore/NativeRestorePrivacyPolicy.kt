package app.logdate.client.device.restore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import app.logdate.client.datastore.LogdatePreferencesDataSource

class NativeRestorePrivacyMetadataRequiredException(
    cause: Throwable? = null,
) : IllegalStateException("Journal privacy settings have not been restored", cause)

object NativeRestorePrivacyPolicy {
    suspend fun verifyBeforeContent(
        preferences: Preferences,
        hasRetainedContent: suspend () -> Boolean,
    ) {
        if (!hasKnownPrivacyMetadata(preferences) && hasRetainedContent()) {
            throw NativeRestorePrivacyMetadataRequiredException()
        }
    }

    suspend fun prepareBackup(
        store: DataStore<Preferences>,
        allowNewInstallationDefaults: (Preferences) -> Boolean = { false },
    ) {
        store.edit { preferences ->
            if (!hasKnownPrivacyMetadata(preferences) && !allowNewInstallationDefaults(preferences)) {
                throw NativeRestorePrivacyMetadataRequiredException()
            }
            if (preferences.asMap()[LogdatePreferencesDataSource.IS_ONBOARDED] == null) {
                preferences[LogdatePreferencesDataSource.IS_ONBOARDED] = false
            }
            if (preferences.asMap()[LogdatePreferencesDataSource.SECURITY_LEVEL] == null) {
                preferences[LogdatePreferencesDataSource.SECURITY_LEVEL] = "NONE"
            }
            check(hasKnownPrivacyMetadata(preferences)) { "Journal privacy settings could not be prepared for backup" }
        }
    }

    internal fun hasKnownPrivacyMetadata(preferences: Preferences): Boolean {
        val onboarded = preferences.asMap()[LogdatePreferencesDataSource.IS_ONBOARDED]
        val security = preferences.asMap()[LogdatePreferencesDataSource.SECURITY_LEVEL]
        return (onboarded is Boolean || security is String) &&
            (onboarded == null || onboarded is Boolean) &&
            (security == null || security == "NONE" || security == "BIOMETRIC")
    }
}
