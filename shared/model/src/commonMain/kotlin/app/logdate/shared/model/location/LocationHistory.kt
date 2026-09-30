package app.logdate.shared.model.location

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class LocationObservation(
    val id: String,
    val ownerId: String,
    val deviceId: String,
    val timestamp: Instant,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val speedMetersPerSecond: Float? = null,
    val bearingDegrees: Float? = null,
    val activity: TravelMode = TravelMode.UNKNOWN,
    val timeZoneId: String? = null,
    val source: String = "UNKNOWN",
    val isMock: Boolean = false,
)

@Serializable
enum class TravelMode {
    STILL,
    WALKING,
    RUNNING,
    CYCLING,
    VEHICLE,
    DRIVING,
    BUS,
    TRAIN,
    UNKNOWN,
}

@Serializable
data class ActivityObservation(
    val id: String,
    val ownerId: String,
    val deviceId: String,
    val timestamp: Instant,
    val activityType: String,
    val transitionType: String,
    val timeZoneId: String? = null,
)

@Serializable
data class SemanticPlace(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val locality: String? = null,
    val externalId: String? = null,
    val userConfirmed: Boolean = false,
)

sealed interface LocationDayItem {
    val id: String
    val start: Instant
    val end: Instant
    val evidenceIds: List<String>
}

data class PlaceVisit(
    override val id: String,
    override val start: Instant,
    override val end: Instant,
    override val evidenceIds: List<String>,
    val latitude: Double,
    val longitude: Double,
    val confirmedStay: Boolean,
    val place: SemanticPlace? = null,
    val memoryIds: List<String> = emptyList(),
) : LocationDayItem

data class JourneyLeg(
    override val id: String,
    override val start: Instant,
    override val end: Instant,
    override val evidenceIds: List<String>,
    val mode: TravelMode,
    val route: List<LocationObservation>,
    val userConfirmed: Boolean = false,
) : LocationDayItem

data class HistoryGap(
    override val id: String,
    override val start: Instant,
    override val end: Instant,
    override val evidenceIds: List<String> = emptyList(),
) : LocationDayItem

@Serializable
enum class HistoryField {
    LABEL,
    PLACE,
    ACTIVITY,
    START,
    END,
    DELETE,
}

/** Immutable edits retain their parent IDs so concurrent changes remain distinguishable. */
@Serializable
data class HistoryCorrection(
    val id: String,
    val targetEvidenceId: String,
    val field: HistoryField,
    val value: String,
    val supersedes: List<String> = emptyList(),
)

@Serializable
data class VisitMemoryLink(
    val noteId: String,
    val targetEvidenceId: String,
)

@Serializable
data class ManualVisit(
    val id: String,
    val start: Instant,
    val end: Instant,
    val placeId: String,
)

@Serializable
data class VisitMemoryContext(
    val evidenceId: String,
    val placeId: String?,
    val placeName: String?,
    val latitude: Double,
    val longitude: Double,
    val visitStart: Instant,
)

@Serializable
sealed interface HistoryPayload {
    @Serializable
    data class Observation(
        val value: LocationObservation,
    ) : HistoryPayload

    @Serializable
    data class Activity(
        val value: ActivityObservation,
    ) : HistoryPayload

    @Serializable
    data class Place(
        val value: SemanticPlace,
    ) : HistoryPayload

    @Serializable
    data class Correction(
        val value: HistoryCorrection,
    ) : HistoryPayload

    @Serializable
    data class MemoryLink(
        val value: VisitMemoryLink,
    ) : HistoryPayload

    @Serializable
    data class Manual(
        val value: ManualVisit,
    ) : HistoryPayload
}
