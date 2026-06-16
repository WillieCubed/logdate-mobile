package app.logdate.client.domain.dayboundary

import app.logdate.client.datastore.LogdatePreferencesDataSource as PersistedPreferences
import app.logdate.client.health.util.LogdatePreferencesDataSource as BoundaryPreferences
import app.logdate.client.health.util.UserPreferences as BoundaryUserPreferences

/**
 * Bridges the app's persisted preferences ([PersistedPreferences]) to the minimal
 * preference contract the day-bounds engine consumes ([BoundaryPreferences]).
 *
 * The day-bounds engine lives in the lower-level health module, which cannot depend
 * on the datastore. Without this adapter that contract was bound to a hardcoded
 * placeholder, so the engine ignored the user's configured day-start hour entirely.
 */
class DatastoreDayBoundaryPreferences(
    private val persisted: PersistedPreferences,
) : BoundaryPreferences {
    override suspend fun getPreferences(): BoundaryUserPreferences {
        val preferences = persisted.getPreferences()
        return BoundaryUserPreferences(
            dayStartHour = preferences.dayStartHour,
            dayEndHour = preferences.dayEndHour,
        )
    }
}
