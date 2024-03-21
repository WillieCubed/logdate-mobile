package app.logdate.core.world

import app.logdate.core.world.model.LogdateActivity

/**
 * A location provider that fuses activity and location data.
 *
 * This domain-layer provider is the interface for observing the current activity and location.
 * Data is updated in the background and can be observed by the client.
 */
interface ActivityLocationProvider : LogdateLocationProvider {
    fun getCurrentActivity(): LogdateActivity
    fun observeActivity(onActivityUpdate: (newActivity: LogdateActivity) -> Unit)
    fun updateActivity(activity: LogdateActivity)
}