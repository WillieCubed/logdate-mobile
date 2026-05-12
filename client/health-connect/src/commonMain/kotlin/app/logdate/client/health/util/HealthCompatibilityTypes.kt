package app.logdate.client.health.util

/**
 * Minimal user preference contract consumed by health aggregation logic.
 */
data class UserPreferences(
    val dayStartHour: Int? = null,
    val dayEndHour: Int? = null,
)

/**
 * Preference source required by health aggregation logic.
 */
interface LogdatePreferencesDataSource {
    suspend fun getPreferences(): UserPreferences
}
