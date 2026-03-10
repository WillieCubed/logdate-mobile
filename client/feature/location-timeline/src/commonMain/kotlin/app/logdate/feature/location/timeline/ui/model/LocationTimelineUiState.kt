package app.logdate.feature.location.timeline.ui.model

import kotlin.time.Instant

sealed interface LocationTimelineUiState {
    data object Loading : LocationTimelineUiState

    data class Error(
        val error: LocationTimelineErrorUiState,
    ) : LocationTimelineUiState

    data class Success(
        val currentLocation: CurrentLocationUiModel?,
        val stops: List<LocationStopUiModel>,
        val selectedStopId: String? = stops.firstOrNull()?.id,
    ) : LocationTimelineUiState {
        val selectedStop: LocationStopUiModel?
            get() = stops.firstOrNull { it.id == selectedStopId } ?: stops.firstOrNull()
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

data class CurrentLocationUiModel(
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
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
)
