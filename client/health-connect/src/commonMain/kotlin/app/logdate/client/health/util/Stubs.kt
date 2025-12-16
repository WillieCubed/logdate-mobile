package app.logdate.client.health.util

/**
 * This file contains stub types for dependencies that we need for compilation
 * but will be provided by the actual dependencies at runtime.
 */

/**
 * Stub data class for user preferences.
 */
data class UserPreferences(
    val dayStartHour: Int? = null,
    val dayEndHour: Int? = null
)

/**
 * Stub interface for data source.
 */
interface LogdatePreferencesDataSource {
    suspend fun getPreferences(): UserPreferences
}