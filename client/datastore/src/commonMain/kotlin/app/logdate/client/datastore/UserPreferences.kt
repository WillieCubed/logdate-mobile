package app.logdate.client.datastore

/**
 * User preferences data class
 */
data class UserPreferences(
    val dayStartHour: Int? = null,
    val dayEndHour: Int? = null
)