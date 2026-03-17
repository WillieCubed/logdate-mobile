package app.logdate.client.location.tracking

import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority

/**
 * Defines GPS accuracy and update frequency parameters for a specific movement state.
 *
 * Each profile is tuned for a different activity type (still, walking, driving) to
 * balance location accuracy against battery consumption.
 */
data class LocationProfile(
    val priority: Int,
    val intervalMillis: Long,
    val minIntervalMillis: Long,
    val displacementMeters: Float,
    val maxDelayMillis: Long,
) {
    fun toLocationRequest(): LocationRequest =
        LocationRequest
            .Builder(priority, intervalMillis)
            .setMinUpdateIntervalMillis(minIntervalMillis)
            .setMinUpdateDistanceMeters(displacementMeters)
            .setMaxUpdateDelayMillis(maxDelayMillis)
            .setWaitForAccurateLocation(priority == Priority.PRIORITY_HIGH_ACCURACY)
            .build()

    companion object {
        /** Low-power heartbeat while the user is stationary. */
        val STILL =
            LocationProfile(
                priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                intervalMillis = 180_000L,
                minIntervalMillis = 180_000L,
                displacementMeters = 0f,
                maxDelayMillis = 180_000L,
            )

        /** High accuracy for walking or running — captures each city block. */
        val ON_FOOT =
            LocationProfile(
                priority = Priority.PRIORITY_HIGH_ACCURACY,
                intervalMillis = 30_000L,
                minIntervalMillis = 15_000L,
                displacementMeters = 20f,
                maxDelayMillis = 60_000L,
            )

        /** High accuracy for vehicle travel — larger displacement threshold. */
        val IN_VEHICLE =
            LocationProfile(
                priority = Priority.PRIORITY_HIGH_ACCURACY,
                intervalMillis = 10_000L,
                minIntervalMillis = 5_000L,
                displacementMeters = 50f,
                maxDelayMillis = 30_000L,
            )
    }
}
