package app.logdate.feature.location.timeline.ui.model

import app.logdate.client.domain.location.LocationMemoryTimeFilter
import kotlin.time.Instant
import kotlin.uuid.Uuid

sealed interface LocationTimelineUiState {
    data object Loading : LocationTimelineUiState

    data class Error(
        val error: LocationTimelineErrorUiState,
    ) : LocationTimelineUiState

    data class Success(
        val currentLocation: CurrentLocationUiModel?,
        val selectedFilter: LocationMemoryTimeFilter,
        val places: List<LocationPlaceUiModel>,
        val visiblePlaces: List<LocationPlaceUiModel>,
        val recentStops: List<LocationStopUiModel>,
        val selectedPlaceId: String? = visiblePlaces.firstOrNull()?.id,
        val canLoadMorePlaces: Boolean = false,
    ) : LocationTimelineUiState {
        val selectedPlace: LocationPlaceUiModel?
            get() = visiblePlaces.firstOrNull { it.id == selectedPlaceId } ?: visiblePlaces.firstOrNull()
    }
}

sealed interface LocationTimelineErrorUiState {
    data object PermissionRequired : LocationTimelineErrorUiState

    data object LocationServicesDisabled : LocationTimelineErrorUiState

    data object TemporarilyUnavailable : LocationTimelineErrorUiState
}

internal fun Throwable.toLocationTimelineErrorUiState(): LocationTimelineErrorUiState =
    when {
        this::class.simpleName == "SecurityException" -> LocationTimelineErrorUiState.PermissionRequired
        message?.contains("permission", ignoreCase = true) == true &&
            message?.contains("location", ignoreCase = true) == true -> {
            LocationTimelineErrorUiState.PermissionRequired
        }
        this is IllegalStateException &&
            message?.contains("location", ignoreCase = true) == true -> {
            LocationTimelineErrorUiState.LocationServicesDisabled
        }
        message?.contains("location disabled", ignoreCase = true) == true -> {
            LocationTimelineErrorUiState.LocationServicesDisabled
        }
        message?.contains("location unavailable", ignoreCase = true) == true -> {
            LocationTimelineErrorUiState.LocationServicesDisabled
        }
        else -> LocationTimelineErrorUiState.TemporarilyUnavailable
    }

enum class LocationLabelSource {
    USER_DEFINED,
    GOOGLE_PLACES,
    COORDINATES,
}

enum class LocationMemoryKind {
    TEXT,
    PHOTO,
    AUDIO,
    VIDEO,
}

data class CurrentLocationUiModel(
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
)

data class LocationPlaceUiModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val lastVisitedLabel: String,
    val memoryCount: Int,
    val sourceLabel: String,
    val source: LocationLabelSource,
    val memories: List<LocationMemoryPreviewUiModel>,
    val relatedStops: List<LocationStopUiModel>,
)

data class LocationMemoryPreviewUiModel(
    val noteId: Uuid,
    val title: String,
    val subtitle: String,
    val timestamp: Instant,
    val latitude: Double,
    val longitude: Double,
    val kind: LocationMemoryKind,
)

data class LocationStopUiModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val startedAt: String,
    val endedAt: String,
    val timeRange: String,
    val duration: String,
    val sourceLabel: String,
    val source: LocationLabelSource,
    val sampleCount: Int,
    val startTime: Instant,
    val endTime: Instant,
    val hasReliableDuration: Boolean,
)
