package app.logdate.feature.library.ui.location

import app.logdate.model.AltitudeUnit
import kotlinx.datetime.Instant

data class LocationLogEntryUiState(
    val placeName: String? = null,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val altitudeUnits: AltitudeUnit,
    /**
     * When the location log entry was created.
     */
    val start: Instant,
    /**
     * When the user stopped being at this location.
     *
     * If this entry is a single point in time, this will be `null`.
     */
    val end: Instant?,
)